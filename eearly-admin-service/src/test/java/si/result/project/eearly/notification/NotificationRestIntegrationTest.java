package si.result.project.eearly.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import si.result.project.eearly.dao.algorithmexecution.AlgorithmExecutionDAO;
import si.result.project.eearly.dao.caretaker.CaretakerDAO;
import si.result.project.eearly.dao.notification.NotificationDAO;
import si.result.project.eearly.dao.notification.NotificationReadDAO;
import si.result.project.eearly.dao.patient.PatientDAO;
import si.result.project.eearly.event.notification.NotificationEventListener;
import si.result.project.eearly.event.notification.event.AnalysisErrorEvent;
import si.result.project.eearly.event.notification.event.AnalysisReadyEvent;
import si.result.project.eearly.event.notification.event.ExecutionCompletedEvent;
import si.result.project.eearly.event.notification.event.ExecutionFailedEvent;
import si.result.project.eearly.event.notification.event.ExecutionStartedEvent;
import si.result.project.eearly.event.notification.event.PatientSyncedEvent;
import si.result.project.eearly.facade.notification.NotificationServiceFacade;
import si.result.project.eearly.mapper.notification.NotificationMapper;
import si.result.project.eearly.mapper.notification.NotificationMapperImpl;
import si.result.project.eearly.model.algorithm.Algorithm;
import si.result.project.eearly.model.algorithmexecution.AlgorithmExecutionStatus;
import si.result.project.eearly.model.algorithmexecution.AlgorithmExecutionTriggerType;
import si.result.project.eearly.model.algorithmexecution.command.CreateAlgorithmExecutionCommand;
import si.result.project.eearly.model.notification.Notification;
import si.result.project.eearly.model.notification.NotificationType;
import si.result.project.eearly.model.notification.command.CreateNotificationCommand;
import si.result.project.eearly.model.patient.Patient;
import si.result.project.eearly.port.algorithmexecution.AlgorithmExecutionService;
import si.result.project.eearly.port.algorithm.AlgorithmRepository;
import si.result.project.eearly.port.patient.PatientRepository;
import si.result.project.eearly.port.caretaker.CaretakerClient;
import si.result.project.eearly.port.caretaker.CaretakerService;
import si.result.project.eearly.port.notification.NotificationService;
import si.result.project.eearly.port.notification.SseNotificationPublisher;
import si.result.project.eearly.port.patient.PatientService;
import si.result.project.eearly.resource.notification.NotificationController;
import si.result.rest.filter.config.RestFilterArgumentResolver;
import si.result.spring.boot.bricks.advice.ErrorResponseHandler;
import si.result.spring.boot.bricks.auth.AuthenticationProvider;

/**
 * Notifications end to end against a migrated PostgreSQL database: the events become notifications for the
 * caretakers of the patient, and the REST API lists them, counts the unread ones and keeps the read state per
 * caretaker, derived from the token only.
 */
@DataJpaTest
@ContextConfiguration(classes = NotificationRestIntegrationTest.Cfg.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@TestPropertySource(properties = {
        "spring.config.name=notification-rest-test",
        // The first test context of a JVM initialises logging for all later ones (the log format
        // characterization test reads service.name), so name the application as the service does.
        "spring.application.name=eearly-admin-service",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.hibernate.naming.physical-strategy=org.hibernate.boot.model.naming.PhysicalNamingStrategyStandardImpl"
})
class NotificationRestIntegrationTest {

    @SpringBootConfiguration
    @EntityScan(basePackages = "si.result.project.eearly.model")
    @EnableJpaRepositories(basePackages = "si.result.project.eearly.dao")
    @EnableJpaAuditing(dateTimeProviderRef = "zonedDateTimeProvider")
    static class Cfg {

        @Bean
        DateTimeProvider zonedDateTimeProvider() {
            return () -> Optional.of(ZonedDateTime.now(ZoneOffset.UTC));
        }

        // The caller is whoever the test says: the token subject is the caretaker's Keycloak id.
        @Bean
        AuthenticationProvider authenticationProvider() {
            return mock(AuthenticationProvider.class);
        }

        @Bean
        CaretakerClient caretakerClient() {
            return mock(CaretakerClient.class);
        }

        @Bean
        PushRecorder pushRecorder() {
            return new PushRecorder();
        }

        @Bean
        CaretakerService caretakerService(CaretakerDAO caretakerDAO, CaretakerClient caretakerClient) {
            return new CaretakerService(caretakerDAO, caretakerClient);
        }

        @Bean
        PatientService patientService(PatientDAO patientDAO) {
            return new PatientService(patientDAO);
        }

        @Bean
        NotificationService notificationService(NotificationDAO notificationDAO,
                NotificationReadDAO notificationReadDAO) {
            return new NotificationService(notificationDAO, notificationReadDAO);
        }

        @Bean
        NotificationEventListener notificationEventListener(NotificationService notificationService,
                PushRecorder pushRecorder, PatientService patientService) {
            return new NotificationEventListener(notificationService, pushRecorder, patientService);
        }

        @Bean
        AlgorithmExecutionService algorithmExecutionService(AlgorithmExecutionDAO algorithmExecutionDAO,
                ApplicationEventPublisher eventPublisher) {
            return new AlgorithmExecutionService(algorithmExecutionDAO, eventPublisher);
        }

        @Bean
        NotificationMapper notificationMapper() {
            return new NotificationMapperImpl();
        }

        // Spring-managed facade, so that its transactional methods keep the session open while the
        // notifications are mapped (as in the running service).
        @Bean
        NotificationServiceFacade notificationServiceFacade(NotificationService notificationService,
                NotificationMapper notificationMapper, AuthenticationProvider authenticationProvider,
                CaretakerService caretakerService) {
            return new NotificationServiceFacade(notificationService, notificationMapper, authenticationProvider,
                    caretakerService);
        }
    }

    /** Stands in for the push transport and records what would have been pushed. */
    static class PushRecorder implements SseNotificationPublisher {
        final List<Notification> pushed = new CopyOnWriteArrayList<>();

        @Override
        public void broadcast(final Notification notification) {
            pushed.add(notification);
        }
    }

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void db(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.flyway.url", POSTGRES::getJdbcUrl);
        registry.add("spring.flyway.user", POSTGRES::getUsername);
        registry.add("spring.flyway.password", POSTGRES::getPassword);
    }

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String ALGORITHM_NAME = "Apnea detection";

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private NotificationServiceFacade notificationFacade;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private AlgorithmExecutionService algorithmExecutionService;

    @Autowired
    private AlgorithmRepository algorithmRepository;

    @Autowired
    private PatientRepository patientRepository;

    @Autowired
    private AuthenticationProvider authenticationProvider;

    @Autowired
    private PushRecorder pushRecorder;

    private MockMvc mvc;
    private UUID patientId;
    private UUID algorithmId;
    private UUID caretakerA;
    private UUID caretakerB;
    private UUID keycloakA;
    private UUID keycloakB;

    @BeforeEach
    void setUp() {
        reset(authenticationProvider);
        pushRecorder.pushed.clear();
        jdbc.update("DELETE FROM t_notification_read");
        jdbc.update("DELETE FROM t_notification");
        jdbc.update("DELETE FROM t_algorithm_execution");
        jdbc.update("DELETE FROM t_patient_caretaker");
        jdbc.update("DELETE FROM t_patient");
        jdbc.update("DELETE FROM t_algorithm");
        jdbc.update("DELETE FROM t_caretaker");

        patientId = UUID.randomUUID();
        algorithmId = UUID.randomUUID();
        caretakerA = UUID.randomUUID();
        caretakerB = UUID.randomUUID();
        keycloakA = UUID.randomUUID();
        keycloakB = UUID.randomUUID();
        jdbc.update("INSERT INTO t_patient (id, first_name, last_name, date_of_birth, gender, health_insurance_id, "
                + "street, city, state, country, email, phone_number) VALUES (?, 'Pat', 'Ient', DATE '1990-01-01', "
                + "'OTHER', 'X1', 's', 'c', 'st', 'co', 'a@b.c', '1')", patientId);
        jdbc.update("INSERT INTO t_algorithm (id, name) VALUES (?, ?)", algorithmId, ALGORITHM_NAME);
        insertCaretaker(caretakerA, keycloakA);
        insertCaretaker(caretakerB, keycloakB);
        jdbc.update("INSERT INTO t_patient_caretaker (patient_id, caretaker_id) VALUES (?, ?)", patientId, caretakerA);
        jdbc.update("INSERT INTO t_patient_caretaker (patient_id, caretaker_id) VALUES (?, ?)", patientId, caretakerB);

        mvc = MockMvcBuilders
                .standaloneSetup(new NotificationController(notificationFacade))
                .setCustomArgumentResolvers(new RestFilterArgumentResolver(), new PageableHandlerMethodArgumentResolver())
                .setControllerAdvice(new ErrorResponseHandler())
                .build();
        as(keycloakA);
    }

    private void insertCaretaker(final UUID id, final UUID keycloakId) {
        jdbc.update("INSERT INTO t_caretaker (id, keycloak_id) VALUES (?, ?)", id, keycloakId);
    }

    /** Calls from now on carry a token whose subject is the given Keycloak id. */
    private void as(final UUID keycloakId) {
        when(authenticationProvider.getId()).thenReturn(keycloakId.toString());
    }

    private UUID createNotification(final UUID addressedTo, final String message) {
        return notificationService.create(new CreateNotificationCommand(
                NotificationType.ERROR, message, patientId, addressedTo, null)).getId();
    }

    private void run(final UUID executionId) {
        algorithmExecutionService.updateStatus(executionId, AlgorithmExecutionStatus.SUBMITTED);
        algorithmExecutionService.updateStatus(executionId, AlgorithmExecutionStatus.RUNNING);
    }

    private void publishAfterCommit(final Object event) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> eventPublisher.publishEvent(event));
    }

    private List<Map<String, Object>> notificationsOf(final UUID caretakerId) {
        return jdbc.queryForList("SELECT * FROM t_notification WHERE id_caretaker = ? ORDER BY message", caretakerId);
    }

    private int unreadCount() throws Exception {
        final String body = mvc.perform(get("/api/v1/notifications/unread/count"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JSON.readTree(body).get("count").asInt();
    }

    private List<String> listedMessages(final String path) throws Exception {
        final String body = mvc.perform(get(path))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        final List<String> messages = new ArrayList<>();
        for (final JsonNode node : JSON.readTree(body).get("payload").get("content")) {
            messages.add(node.get("message").asText());
        }
        return messages;
    }

    // ------------------------------------------------------------------ events become notifications

    @Test
    void everyPortedEventBecomesANotificationForEachCaretakerOfThePatient() {
        final UUID referenceId = UUID.randomUUID();

        publishAfterCommit(new AnalysisReadyEvent(referenceId, patientId));
        publishAfterCommit(new AnalysisErrorEvent(referenceId, patientId));
        publishAfterCommit(new ExecutionStartedEvent(referenceId, patientId, ALGORITHM_NAME));
        publishAfterCommit(new ExecutionCompletedEvent(referenceId, patientId, ALGORITHM_NAME));
        publishAfterCommit(new ExecutionFailedEvent(referenceId, patientId, ALGORITHM_NAME));

        for (final UUID caretakerId : List.of(caretakerA, caretakerB)) {
            final List<Map<String, Object>> rows = notificationsOf(caretakerId);
            assertThat(rows).extracting(row -> row.get("message")).containsExactly(
                    "ALGORITHM_EXECUTION_COMPLETED:" + ALGORITHM_NAME,
                    "ALGORITHM_EXECUTION_FAILED:" + ALGORITHM_NAME,
                    "ALGORITHM_EXECUTION_STARTED:" + ALGORITHM_NAME,
                    "ANALYSIS_ERROR",
                    "ANALYSIS_READY");
            assertThat(rows).extracting(row -> row.get("type").toString()).containsExactly(
                    "ALGORITHM_EXECUTION", "ERROR", "ALGORITHM_EXECUTION", "ERROR", "ANALYSIS");
            assertThat(rows).allSatisfy(row -> {
                assertThat(row.get("id_patient")).isEqualTo(patientId);
                assertThat(row.get("id_reference")).isEqualTo(referenceId);
                assertThat(row.get("created_at")).isNotNull();
            });
        }
        assertThat(pushRecorder.pushed).hasSize(10);
    }

    @Test
    void patientSyncedBecomesANotificationForThatCaretakerOnly() {
        publishAfterCommit(new PatientSyncedEvent(patientId, caretakerA));

        assertThat(notificationsOf(caretakerA)).hasSize(1);
        assertThat(notificationsOf(caretakerA).getFirst().get("message")).isEqualTo("PATIENT_SYNCED");
        assertThat(notificationsOf(caretakerA).getFirst().get("type").toString()).isEqualTo("SYNC");
        assertThat(notificationsOf(caretakerA).getFirst().get("id_reference")).isEqualTo(patientId);
        assertThat(notificationsOf(caretakerB)).isEmpty();
    }

    @Test
    void anEventThatIsNotCommittedCreatesNoNotification() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            eventPublisher.publishEvent(new AnalysisReadyEvent(UUID.randomUUID(), patientId));
            status.setRollbackOnly();
        });

        assertThat(jdbc.queryForObject("SELECT count(*) FROM t_notification", Integer.class)).isZero();
    }

    @Test
    void notificationsContainOnlyCodesAndIdsNoPersonalData() {
        publishAfterCommit(new ExecutionFailedEvent(UUID.randomUUID(), patientId, ALGORITHM_NAME));
        publishAfterCommit(new AnalysisReadyEvent(UUID.randomUUID(), patientId));
        publishAfterCommit(new PatientSyncedEvent(patientId, caretakerA));

        final List<String> messages = jdbc.queryForList("SELECT message FROM t_notification", String.class);
        assertThat(messages).isNotEmpty().allSatisfy(message -> {
            assertThat(message).matches("[A-Z_]+(:[^:]+)?");
            assertThat(message).doesNotContain("Pat", "Ient", "a@b.c");
        });
    }

    // ------------------------------------------------------------------ the core flows publish the events

    @Test
    void failingAnAlgorithmExecutionNotifiesTheCaretakersOfThePatient() {
        final UUID executionId = UUID.randomUUID();
        jdbc.update("INSERT INTO t_algorithm_execution (id, id_algorithm, id_patient, execution_status, trigger_type) "
                + "VALUES (?, ?, ?, CAST('RUNNING' AS e_execution_status_type), CAST('MANUAL' AS e_trigger_type))",
                executionId, algorithmId, patientId);

        algorithmExecutionService.markFailed(executionId, "failure");

        for (final UUID caretakerId : List.of(caretakerA, caretakerB)) {
            final List<Map<String, Object>> rows = notificationsOf(caretakerId);
            assertThat(rows).hasSize(1);
            assertThat(rows.getFirst().get("message")).isEqualTo("ALGORITHM_EXECUTION_FAILED:" + ALGORITHM_NAME);
            assertThat(rows.getFirst().get("type").toString()).isEqualTo("ERROR");
            assertThat(rows.getFirst().get("id_reference")).isEqualTo(executionId);
        }
    }

    @Test
    void creatingAndCompletingAScheduledExecutionNotifiesTheCaretakersOfThePatient() {
        final Algorithm algorithm = algorithmRepository.findById(algorithmId).orElseThrow();
        final Patient patient = new TransactionTemplate(transactionManager)
                .execute(status -> patientRepository.findById(patientId).orElseThrow());

        final UUID executionId = algorithmExecutionService.create(new CreateAlgorithmExecutionCommand(
                algorithm, patient, null, AlgorithmExecutionTriggerType.SCHEDULED, "{}")).getId();
        run(executionId);
        algorithmExecutionService.markCompleted(executionId, "{}");

        assertThat(notificationsOf(caretakerA)).extracting(row -> row.get("message")).containsExactlyInAnyOrder(
                "ALGORITHM_EXECUTION_STARTED:" + ALGORITHM_NAME, "ALGORITHM_EXECUTION_COMPLETED:" + ALGORITHM_NAME);
        assertThat(notificationsOf(caretakerB)).hasSize(2);
    }

    @Test
    void aManualExecutionStartAndCompletionNotifyNobody() {
        final Algorithm algorithm = algorithmRepository.findById(algorithmId).orElseThrow();
        final Patient patient = new TransactionTemplate(transactionManager)
                .execute(status -> patientRepository.findById(patientId).orElseThrow());

        final UUID executionId = algorithmExecutionService.create(new CreateAlgorithmExecutionCommand(
                algorithm, patient, null, AlgorithmExecutionTriggerType.MANUAL, "{}")).getId();
        run(executionId);
        algorithmExecutionService.markCompleted(executionId, "{}");

        assertThat(jdbc.queryForObject("SELECT count(*) FROM t_notification", Integer.class)).isZero();
    }

    // ------------------------------------------------------------------ REST: list, count, read

    @Test
    void listShowsTheNotificationsAddressedToTheCallerOrToEveryoneAndTheirReadState() throws Exception {
        final UUID own = createNotification(caretakerA, "OWN");
        createNotification(null, "EVERYONE");
        createNotification(caretakerB, "OF_OTHER");

        assertThat(listedMessages("/api/v1/notifications")).containsExactlyInAnyOrder("OWN", "EVERYONE");

        mvc.perform(post("/api/v1/notifications/read/" + own)).andExpect(status().isOk());

        final String body = mvc.perform(get("/api/v1/notifications")).andReturn().getResponse().getContentAsString();
        for (final JsonNode node : JSON.readTree(body).get("payload").get("content")) {
            assertThat(node.get("isSeen").asBoolean()).isEqualTo("OWN".equals(node.get("message").asText()));
            assertThat(node.get("patientId").asText()).isEqualTo(patientId.toString());
        }
    }

    @Test
    void unreadCountAndUnreadListFollowTheCallersReadState() throws Exception {
        final UUID first = createNotification(caretakerA, "FIRST");
        createNotification(caretakerA, "SECOND");
        createNotification(null, "EVERYONE");
        createNotification(caretakerB, "OF_OTHER");

        assertThat(unreadCount()).isEqualTo(3);
        assertThat(listedMessages("/api/v1/notifications/unread")).containsExactlyInAnyOrder(
                "FIRST", "SECOND", "EVERYONE");

        mvc.perform(post("/api/v1/notifications/read/" + first)).andExpect(status().isOk());
        // Marking it again changes nothing.
        mvc.perform(post("/api/v1/notifications/read/" + first)).andExpect(status().isOk());

        assertThat(unreadCount()).isEqualTo(2);
        assertThat(listedMessages("/api/v1/notifications/unread")).containsExactlyInAnyOrder("SECOND", "EVERYONE");

        mvc.perform(post("/api/v1/notifications/read")).andExpect(status().isOk());

        assertThat(unreadCount()).isZero();
        assertThat(listedMessages("/api/v1/notifications/unread")).isEmpty();
        assertThat(listedMessages("/api/v1/notifications")).hasSize(3);
        // Only the caller's own read state was written, and never for the other caretaker's notification.
        assertThat(jdbc.queryForList("SELECT DISTINCT id_caretaker FROM t_notification_read", UUID.class))
                .containsExactly(caretakerA);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM t_notification_read", Integer.class)).isEqualTo(3);
    }

    @Test
    void readStateIsPerCaretakerForNotificationsAddressedToEveryone() throws Exception {
        final UUID everyone = createNotification(null, "EVERYONE");

        mvc.perform(post("/api/v1/notifications/read/" + everyone)).andExpect(status().isOk());
        assertThat(unreadCount()).isZero();

        as(keycloakB);
        assertThat(unreadCount()).isEqualTo(1);
        assertThat(listedMessages("/api/v1/notifications/unread")).containsExactly("EVERYONE");
        mvc.perform(get("/api/v1/notifications"))
                .andExpect(jsonPath("$.payload.content[0].isSeen").value(false));

        as(keycloakA);
        mvc.perform(get("/api/v1/notifications"))
                .andExpect(jsonPath("$.payload.content[0].isSeen").value(true));
    }

    @Test
    void aCaretakerCannotReadOrMarkTheNotificationsOfAnotherCaretaker() throws Exception {
        final UUID ofB = createNotification(caretakerB, "OF_B");
        createNotification(caretakerA, "OF_A");

        // Not in A's lists or count ...
        assertThat(listedMessages("/api/v1/notifications")).containsExactly("OF_A");
        assertThat(listedMessages("/api/v1/notifications/unread")).containsExactly("OF_A");
        assertThat(unreadCount()).isEqualTo(1);

        // ... and A cannot mark it as read, as the notification is not found for A.
        mvc.perform(post("/api/v1/notifications/read/" + ofB)).andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM t_notification_read", Integer.class)).isZero();

        // "Mark all" does not touch it either.
        mvc.perform(post("/api/v1/notifications/read")).andExpect(status().isOk());
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM t_notification_read WHERE id_notification = ?", Integer.class, ofB)).isZero();

        // B still has it unread.
        as(keycloakB);
        assertThat(unreadCount()).isEqualTo(1);
        assertThat(listedMessages("/api/v1/notifications/unread")).containsExactly("OF_B");
    }

    @Test
    void markingAnUnknownNotificationIsNotFound() throws Exception {
        mvc.perform(post("/api/v1/notifications/read/" + UUID.randomUUID())).andExpect(status().isNotFound());
    }

    @Test
    void theCallerIsAlwaysTheTokenSubjectAndAnUnknownSubjectHasNoNotifications() throws Exception {
        createNotification(caretakerA, "OF_A");
        as(UUID.randomUUID());

        mvc.perform(get("/api/v1/notifications")).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/notifications/unread/count")).andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/notifications/read")).andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM t_notification_read", Integer.class)).isZero();
    }

    // ------------------------------------------------------------------ schema

    @Test
    void schemaHasTheNotificationTablesWithTheirKeysAndAllTypes() {
        assertThat(jdbc.queryForList(
                "SELECT enumlabel FROM pg_enum e JOIN pg_type t ON t.oid = e.enumtypid "
                        + "WHERE t.typname = 'e_notification_type' ORDER BY e.enumsortorder", String.class))
                .containsExactly("SYNC", "ERROR", "ANALYSIS", "MEASUREMENT", "ALGORITHM_EXECUTION",
                        "THRESHOLD_ALERT");
        assertThat(jdbc.queryForList("SELECT column_name FROM information_schema.columns "
                + "WHERE table_name = 't_notification' ORDER BY column_name", String.class))
                .containsExactly("created_at", "id", "id_caretaker", "id_patient", "id_reference", "message", "type");
        assertThat(jdbc.queryForList("SELECT column_name FROM information_schema.columns "
                + "WHERE table_name = 't_notification_read' ORDER BY column_name", String.class))
                .containsExactly("id_caretaker", "id_notification");
        assertThat(jdbc.queryForList("SELECT constraint_name FROM information_schema.table_constraints "
                + "WHERE table_name = 't_notification' AND constraint_type = 'FOREIGN KEY'", String.class))
                .containsExactlyInAnyOrder("fk_notification_patient", "fk_notification_caretaker");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM t_notification", Integer.class)).isZero();
    }

    @Test
    void foreignKeysRejectUnknownReferences() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO t_notification (id, type, created_at, message, id_patient) "
                        + "VALUES (?, CAST('SYNC' AS e_notification_type), now(), 'X', ?)",
                UUID.randomUUID(), UUID.randomUUID()))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO t_notification_read (id_caretaker, id_notification) VALUES (?, ?)",
                caretakerA, UUID.randomUUID()))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }
}
