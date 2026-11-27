package si.result.project.eearly.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nimbusds.jwt.JWTClaimsSet;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.data.web.SpringDataWebAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.info.BuildProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.Import;
import org.springframework.data.web.PagedModel;
import org.springframework.http.MediaType;
import org.springframework.data.domain.PageImpl;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import si.result.logging.spring.boot.config.LoggingConfig;
import si.result.logging.spring.boot.config.ServicePropertiesConfig;
import si.result.logging.spring.boot.config.properties.LoggingConfigurationProperties;
import si.result.project.eearly.config.security.SecurityConfig;
import si.result.project.eearly.dto.qrcode.QRCodeDTO;
import si.result.project.eearly.dto.qrcode.QRCodeLiteDTO;
import si.result.project.eearly.facade.qrcode.QRCodeServiceFacade;
import si.result.project.eearly.model.qrcode.QRCodeStatus;
import si.result.project.eearly.resource.qrcode.QRCodeController;
import si.result.spring.boot.bricks.auth.AuthenticationProvider;
import si.result.spring.boot.bricks.auth.AuthenticationProviderImpl;

/**
 * The QR code endpoints with the service's request/response and method logging switched on, as in application.yaml
 * (HTTP and method logging enabled, Authorization header logging off): what the log holds of a request to create,
 * read, list or send a QR code. The facade is mocked and answers with a QR code whose token, image and onboarding
 * URL are distinctive values.
 */
@WebMvcTest(QRCodeController.class)
@ContextConfiguration(classes = {SecurityConfig.class, QRCodeController.class, QRCodeLoggingTest.Cfg.class,
        LoggingConfig.class, ServicePropertiesConfig.class, LoggingConfigurationProperties.class,
        SseLoggingBypassConfig.class})
@Import({si.result.spring.boot.bricks.advice.ErrorResponseHandler.class, SpringDataWebAutoConfiguration.class})
@ExtendWith(OutputCaptureExtension.class)
@TestPropertySource(properties = {
        "spring.config.name=qrcode-logging-test",
        // The first test context of a JVM initialises logging for all later ones (the log format
        // characterization test reads service.name), so name the application as the service does.
        "spring.application.name=eearly-admin-service",
        "spring.security.cors.allowed-origins=*",
        "spring.security.cors.allowed-methods=GET,POST,PUT,DELETE,OPTIONS",
        "admin.keycloak.ui-client-id=test-client",
        "logging.loggers.http.enabled=true",
        "logging.loggers.http.log-response=true",
        "logging.loggers.http.log-authorization-header=false",
        "logging.loggers.method.enabled=true"
})
class QRCodeLoggingTest {

    @TestConfiguration
    @EnableAspectJAutoProxy
    @EnableConfigurationProperties
    static class Cfg {
        @Bean
        AuthenticationProvider authenticationProvider() {
            return new AuthenticationProviderImpl();
        }

        @Bean
        BuildProperties buildProperties() {
            final Properties properties = new Properties();
            properties.setProperty("version", "test");
            return new BuildProperties(properties);
        }
    }

    private static final UUID QR_CODE_ID = UUID.fromString("7a1d9c0e-2f4b-4d6a-8e35-9b1c3d5f7a91");
    private static final UUID PATIENT_ID = UUID.fromString("52378678-4cad-4f7b-893e-9e129e47824a");
    private static final UUID KEYCLOAK_ID = UUID.fromString("0c6c3b3e-8d6e-4d9a-9a52-1d6f6a8e2b11");
    // Distinctive values, so that any occurrence in the log output is the value itself.
    private static final String QR_TOKEN = "d31f6b0a-9c27-4e58-b1a4-6f08c2d9e713-1700000000000";
    private static final String QR_IMAGE = "data:image/png;base64,iVBORw0KGgoDistinctiveImageBytes7c1e";
    private static final String ONBOARDING_URL = "https://mobile.example.org/onboarding/c7b2e9d4-ssw";
    private static final String BEARER = "bearer-token-3e8a5c1f9b72";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private QRCodeServiceFacade facade;

    @BeforeEach
    void setUp() throws Exception {
        final var claims = JWTClaimsSet.parse("{\"sub\": \"" + KEYCLOAK_ID
                + "\", \"resource_access\": {\"test-client\": {\"roles\": [\"Clinician\"]}}}").getClaims();
        when(jwtDecoder.decode(BEARER)).thenReturn(
                Jwt.withTokenValue(BEARER).header("alg", "RS256").claims(c -> c.putAll(claims)).build());
        final OffsetDateTime expiresAt = OffsetDateTime.now().plusDays(30);
        when(facade.getById(any())).thenReturn(
                new QRCodeDTO(QR_CODE_ID, QR_TOKEN, expiresAt, QR_IMAGE, false, QRCodeStatus.ACTIVE, null, null));
        when(facade.getByPatientId(any())).thenReturn(
                new QRCodeDTO(QR_CODE_ID, QR_TOKEN, expiresAt, QR_IMAGE, false, QRCodeStatus.ACTIVE, null, null));
        when(facade.getPage(any(), any())).thenReturn(new PagedModel<>(new PageImpl<>(List.of(
                new QRCodeLiteDTO(QR_CODE_ID, QR_TOKEN, expiresAt, QR_IMAGE, false, QRCodeStatus.ACTIVE, PATIENT_ID)))));
        when(facade.create(any())).thenReturn(
                new QRCodeLiteDTO(QR_CODE_ID, QR_TOKEN, expiresAt, QR_IMAGE, false, QRCodeStatus.ACTIVE, PATIENT_ID));
        when(facade.sendQRCodeByEmailAsync(any())).thenReturn(CompletableFuture.completedFuture(null));
    }

    @Test
    @DisplayName("Requests are logged, without the token, the QR code image, the onboarding URL or the bearer token")
    void theLogHoldsNoCodeAndNoCredential(CapturedOutput output) throws Exception {
        final String bearer = "Bearer " + BEARER;
        final String createBody = "{\"patientId\":\"" + PATIENT_ID + "\",\"onboardingUrl\":\"" + ONBOARDING_URL + "\"}";

        mockMvc.perform(post("/api/v1/qrcodes").header("Authorization", bearer)
                .contentType(MediaType.APPLICATION_JSON).content(createBody)).andExpect(status().isCreated());
        mockMvc.perform(get("/api/v1/qrcodes/" + QR_CODE_ID).header("Authorization", bearer)).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/qrcodes/patient/" + PATIENT_ID).header("Authorization", bearer))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/qrcodes").header("Authorization", bearer)).andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/qrcodes/send-email").header("Authorization", bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"qrCodeId\":\"" + QR_CODE_ID + "\",\"appName\":\"Care App\"}")).andExpect(status().isOk());

        // The request logging is on: the requests themselves are logged ...
        assertThat(output.getAll()).contains("/api/v1/qrcodes");
        // ... and the log holds no code and no credential.
        assertThat(output.getAll()).doesNotContain(QR_TOKEN).doesNotContain("d31f6b0a")
                .doesNotContain("DistinctiveImageBytes").doesNotContain(ONBOARDING_URL).doesNotContain("c7b2e9d4")
                .doesNotContain(BEARER);
    }
}
