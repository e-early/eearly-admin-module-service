package si.result.project.eearly.sse;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import si.result.project.eearly.mapper.notification.NotificationMapperImpl;
import si.result.project.eearly.model.caretaker.Caretaker;
import si.result.project.eearly.model.notification.Notification;
import si.result.project.eearly.model.notification.NotificationType;
import si.result.project.eearly.model.notification.command.CreateNotificationCommand;
import si.result.project.eearly.port.caretaker.CaretakerService;
import si.result.project.eearly.resource.notification.NotificationSseController;
import si.result.spring.boot.bricks.auth.AuthenticationProvider;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;

/**
 * Who receives what on the notification stream, with the real controller, registry and mapper: a caretaker
 * receives the notifications addressed to them (and those addressed to everyone) and never those of another
 * caretaker; the caretaker is the one of the token, whatever the request says.
 */
@DisplayName("Notification stream delivery")
class NotificationStreamDeliveryTest {

    private static final String STREAM = "/api/v1/notifications/stream";
    private static final ObjectMapper JSON = new ObjectMapper();

    private final UUID keycloakA = UUID.randomUUID();
    private final UUID keycloakB = UUID.randomUUID();
    private final UUID caretakerA = UUID.randomUUID();
    private final UUID caretakerB = UUID.randomUUID();
    private final UUID patientId = UUID.randomUUID();

    private AuthenticationProvider authenticationProvider;
    private SseEmitterRegistry registry;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        authenticationProvider = mock(AuthenticationProvider.class);
        final CaretakerService caretakerService = mock(CaretakerService.class);
        final Caretaker a = caretaker(caretakerA);
        final Caretaker b = caretaker(caretakerB);
        when(caretakerService.findByKeycloakId(keycloakA)).thenReturn(a);
        when(caretakerService.findByKeycloakId(keycloakB)).thenReturn(b);
        registry = new SseEmitterRegistry(new NotificationMapperImpl());
        mvc = MockMvcBuilders
                .standaloneSetup(new NotificationSseController(registry, authenticationProvider, caretakerService))
                .build();
    }

    private static Caretaker caretaker(final UUID id) {
        final Caretaker caretaker = mock(Caretaker.class);
        when(caretaker.getId()).thenReturn(id);
        return caretaker;
    }

    /** Opens a stream with a token whose subject is the given Keycloak id. */
    private MvcResult open(final UUID keycloakId) throws Exception {
        when(authenticationProvider.getId()).thenReturn(keycloakId.toString());
        return mvc.perform(get(STREAM).accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(request().asyncStarted())
                .andReturn();
    }

    private Notification notification(final UUID addressedTo, final String message) {
        final Notification notification = Notification.create(new CreateNotificationCommand(
                NotificationType.ERROR, message, patientId, addressedTo, UUID.randomUUID()));
        ReflectionTestUtils.setField(notification, "id", UUID.randomUUID());
        return notification;
    }

    private static String received(final MvcResult stream) throws Exception {
        return stream.getResponse().getContentAsString();
    }

    private static List<JsonNode> events(final MvcResult stream) throws Exception {
        final List<JsonNode> events = new ArrayList<>();
        for (final String line : received(stream).split("\n")) {
            if (line.startsWith("data:")) {
                events.add(JSON.readTree(line.substring("data:".length())));
            }
        }
        return events;
    }

    @Test
    @DisplayName("A stream opens with the connected comment")
    void stream_opensWithConnectedComment() throws Exception {
        final MvcResult stream = open(keycloakA);

        assertThat(received(stream)).contains(":connected");
    }

    @Test
    @DisplayName("A caretaker receives their own notification")
    void caretaker_receivesOwnNotification() throws Exception {
        final MvcResult streamA = open(keycloakA);
        final Notification forA = notification(caretakerA, "ANALYSIS_READY");

        registry.broadcast(forA);

        assertThat(events(streamA)).hasSize(1);
        assertThat(events(streamA).getFirst().get("id").asText()).isEqualTo(forA.getId().toString());
        assertThat(received(streamA)).contains("event:notification");
    }

    @Test
    @DisplayName("Caretaker A never receives the notification of caretaker B, and B never receives A's")
    void caretaker_neverReceivesTheNotificationOfAnother() throws Exception {
        final MvcResult streamA = open(keycloakA);
        final MvcResult streamB = open(keycloakB);
        final Notification forA = notification(caretakerA, "ANALYSIS_READY");
        final Notification forB = notification(caretakerB, "ANALYSIS_ERROR");

        registry.broadcast(forA);
        registry.broadcast(forB);

        assertThat(events(streamA)).extracting(event -> event.get("id").asText())
                .containsExactly(forA.getId().toString());
        assertThat(events(streamB)).extracting(event -> event.get("id").asText())
                .containsExactly(forB.getId().toString());
        assertThat(received(streamA)).doesNotContain(forB.getId().toString()).doesNotContain("ANALYSIS_ERROR");
        assertThat(received(streamB)).doesNotContain(forA.getId().toString()).doesNotContain("ANALYSIS_READY");
    }

    @Test
    @DisplayName("A notification of a caretaker who is not connected reaches nobody else")
    void notificationOfAnUnconnectedCaretaker_reachesNobodyElse() throws Exception {
        final MvcResult streamA = open(keycloakA);

        registry.broadcast(notification(caretakerB, "ANALYSIS_ERROR"));

        assertThat(events(streamA)).isEmpty();
    }

    @Test
    @DisplayName("A notification addressed to everyone reaches every connected caretaker")
    void notificationForEveryone_reachesEveryConnectedCaretaker() throws Exception {
        final MvcResult streamA = open(keycloakA);
        final MvcResult streamB = open(keycloakB);
        final Notification forEveryone = notification(null, "PATIENT_SYNCED");

        registry.broadcast(forEveryone);

        for (final MvcResult stream : List.of(streamA, streamB)) {
            assertThat(events(stream)).extracting(event -> event.get("id").asText())
                    .containsExactly(forEveryone.getId().toString());
        }
    }

    @Test
    @DisplayName("The caretaker of the stream is the one of the token, whatever the request names")
    void caretaker_isTheOneOfTheToken() throws Exception {
        when(authenticationProvider.getId()).thenReturn(keycloakA.toString());
        final MvcResult streamA = mvc.perform(get(STREAM).accept(MediaType.TEXT_EVENT_STREAM)
                        .param("caretakerId", caretakerB.toString())
                        .param("keycloakId", keycloakB.toString())
                        .header("X-Caretaker-Id", caretakerB.toString()))
                .andExpect(request().asyncStarted())
                .andReturn();
        final Notification forA = notification(caretakerA, "ANALYSIS_READY");
        final Notification forB = notification(caretakerB, "ANALYSIS_ERROR");

        registry.broadcast(forB);
        registry.broadcast(forA);

        assertThat(events(streamA)).extracting(event -> event.get("id").asText())
                .containsExactly(forA.getId().toString());
    }

    @Test
    @DisplayName("A new stream of a caretaker replaces the previous one, which gets nothing more")
    void newStream_replacesThePreviousOne() throws Exception {
        final MvcResult first = open(keycloakA);
        final MvcResult second = open(keycloakA);
        final Notification forA = notification(caretakerA, "ANALYSIS_READY");

        registry.broadcast(forA);

        assertThat(events(first)).isEmpty();
        assertThat(events(second)).hasSize(1);
        assertThat(registry.emitterCount(caretakerA)).isEqualTo(1);
    }

    @Test
    @DisplayName("The event carries the same fields as the REST notification: ids, type, message code, no personal data")
    void payload_isTheRestNotification() throws Exception {
        final MvcResult streamA = open(keycloakA);
        final Notification forA = notification(caretakerA, "ALGORITHM_EXECUTION_FAILED:Apnea detection");

        registry.broadcast(forA);

        final JsonNode event = events(streamA).getFirst();
        assertThat(event.fieldNames()).toIterable().containsExactlyInAnyOrder(
                "id", "type", "patientId", "caretakerId", "referenceId", "createdAt", "message", "isSeen");
        assertThat(event.get("id").asText()).isEqualTo(forA.getId().toString());
        assertThat(event.get("type").asText()).isEqualTo("ERROR");
        assertThat(event.get("patientId").asText()).isEqualTo(patientId.toString());
        assertThat(event.get("caretakerId").asText()).isEqualTo(caretakerA.toString());
        assertThat(event.get("referenceId").asText()).isEqualTo(forA.getReferenceId().toString());
        assertThat(event.get("message").asText()).isEqualTo("ALGORITHM_EXECUTION_FAILED:Apnea detection");
        assertThat(event.get("isSeen").asBoolean()).isFalse();
    }
}
