package si.result.project.eearly.port.mobile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;
import si.result.project.eearly.exception.DomainExceptionCode;
import si.result.project.eearly.model.mobile.CreateMobileUserCommand;
import si.result.spring.boot.bricks.exception.DomainException;

@ExtendWith(OutputCaptureExtension.class)
class MobileServiceServiceTest {

    private static final String BASE_URL = "http://mobile.test/api/v1";
    private static final String CLIENT_TOKEN = "client-token-5d2b9e";
    // Distinctive values, so that any occurrence in the log output is the value itself.
    private static final String SECRET_LINK = "https://mobile.example.org/api/v1/onboarding/secret-code-9f3a7c1e";
    private static final String SECRET_BODY = "secret-body-4c8e1b";

    private final UUID userId = UUID.randomUUID();
    private MockRestServiceServer server;
    private MobileKeycloakService keycloak;
    private MobileServiceService service;

    @BeforeEach
    void setUp() {
        final RestTemplate restTemplate = new RestTemplateBuilder().rootUri(BASE_URL).build();
        server = MockRestServiceServer.bindTo(restTemplate).build();
        keycloak = mock(MobileKeycloakService.class);
        when(keycloak.getAccessTokenForClient()).thenReturn(CLIENT_TOKEN);
        service = new MobileServiceService(restTemplate, keycloak);
    }

    private String linkEndpoint() {
        return BASE_URL + "/onboarding/users/" + userId + "/onboarding-link";
    }

    @Test
    @DisplayName("issueOnboardingLink posts to the user's onboarding-link endpoint with the client token and returns the link")
    void issueOnboardingLink_Returns200Link(final CapturedOutput output) {
        server.expect(requestTo(linkEndpoint()))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer " + CLIENT_TOKEN))
                .andExpect(content().string(""))
                .andRespond(withSuccess("{\"onboardingUrl\":\"" + SECRET_LINK + "\"}", MediaType.APPLICATION_JSON));

        assertThat(service.issueOnboardingLink(userId)).isEqualTo(SECRET_LINK);

        server.verify();
        assertThat(output.getAll()).doesNotContain(SECRET_LINK).doesNotContain("secret-code-9f3a7c1e")
                .doesNotContain(CLIENT_TOKEN);
    }

    @Test
    @DisplayName("issueOnboardingLink answers MOBILE_USER_NOT_FOUND for an unknown user")
    void issueOnboardingLink_404() {
        server.expect(requestTo(linkEndpoint())).andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThatThrownBy(() -> service.issueOnboardingLink(userId))
                .isInstanceOfSatisfying(DomainException.class,
                        e -> assertThat(e.getCode()).isEqualTo(DomainExceptionCode.MOBILE_USER_NOT_FOUND));
    }

    @Test
    @DisplayName("issueOnboardingLink answers MOBILE_SERVICE_REQUEST_FAILED for other errors, logging the status only")
    void issueOnboardingLink_500(final CapturedOutput output) {
        server.expect(requestTo(linkEndpoint())).andRespond(
                withStatus(HttpStatus.INTERNAL_SERVER_ERROR).body(SECRET_BODY + " " + SECRET_LINK)
                        .contentType(MediaType.TEXT_PLAIN));

        assertThatThrownBy(() -> service.issueOnboardingLink(userId))
                .isInstanceOfSatisfying(DomainException.class,
                        e -> assertThat(e.getCode()).isEqualTo(DomainExceptionCode.MOBILE_SERVICE_REQUEST_FAILED));

        assertThat(output.getAll()).contains("500").doesNotContain(SECRET_BODY).doesNotContain(SECRET_LINK)
                .doesNotContain("secret-code-9f3a7c1e").doesNotContain(CLIENT_TOKEN);
    }

    @Test
    @DisplayName("issueOnboardingLink fails for other client errors too, such as an expired token")
    void issueOnboardingLink_401() {
        server.expect(requestTo(linkEndpoint())).andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThatThrownBy(() -> service.issueOnboardingLink(userId))
                .isInstanceOfSatisfying(DomainException.class,
                        e -> assertThat(e.getCode()).isEqualTo(DomainExceptionCode.MOBILE_SERVICE_REQUEST_FAILED));
    }

    @Test
    @DisplayName("issueOnboardingLink fails when the answer holds no link, or is not readable")
    void issueOnboardingLink_NoLink(final CapturedOutput output) {
        server.expect(requestTo(linkEndpoint()))
                .andRespond(withSuccess("{\"onboardingUrl\":\"  \"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(linkEndpoint()))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(linkEndpoint()))
                .andRespond(withSuccess("not json " + SECRET_BODY, MediaType.APPLICATION_JSON));

        for (int i = 0; i < 3; i++) {
            assertThatThrownBy(() -> service.issueOnboardingLink(userId))
                    .isInstanceOfSatisfying(DomainException.class,
                            e -> assertThat(e.getCode()).isEqualTo(DomainExceptionCode.MOBILE_SERVICE_REQUEST_FAILED));
        }

        assertThat(output.getAll()).doesNotContain(SECRET_BODY);
    }

    @Test
    @DisplayName("issueOnboardingLink fails without calling the mobile service when no client token can be had")
    void issueOnboardingLink_NoToken() {
        when(keycloak.getAccessTokenForClient()).thenThrow(new RuntimeException("keycloak down"));

        assertThatThrownBy(() -> service.issueOnboardingLink(userId))
                .isInstanceOfSatisfying(DomainException.class,
                        e -> assertThat(e.getCode()).isEqualTo(DomainExceptionCode.MOBILE_SERVICE_REQUEST_FAILED));

        server.verify();
    }

    @Test
    @DisplayName("issueOnboardingLink fails without calling the mobile service on an empty client token")
    void issueOnboardingLink_EmptyToken() {
        when(keycloak.getAccessTokenForClient()).thenReturn(" ");

        assertThatThrownBy(() -> service.issueOnboardingLink(userId))
                .isInstanceOfSatisfying(DomainException.class,
                        e -> assertThat(e.getCode()).isEqualTo(DomainExceptionCode.MOBILE_SERVICE_REQUEST_FAILED));
    }

    @Test
    @DisplayName("createUser logs the status of a failure, not the body of the answer")
    void createUser_Failure_LogsNoBody(final CapturedOutput output) {
        server.expect(requestTo(BASE_URL + "/onboarding/create-user")).andRespond(
                withStatus(HttpStatus.INTERNAL_SERVER_ERROR).body(SECRET_BODY).contentType(MediaType.TEXT_PLAIN));

        assertThatThrownBy(() -> service.createUser(new CreateMobileUserCommand("A", "B", "a@example.org"), "t"))
                .isInstanceOfSatisfying(DomainException.class,
                        e -> assertThat(e.getCode()).isEqualTo(DomainExceptionCode.MOBILE_SERVICE_REQUEST_FAILED));

        assertThat(output.getAll()).contains("500").doesNotContain(SECRET_BODY);
        verifyNoInteractions(keycloak);
    }
}
