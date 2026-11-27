package si.result.project.eearly.config.security;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.nimbusds.jwt.JWTClaimsSet;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import si.result.project.eearly.config.CallbackTokenProperties;
import si.result.project.eearly.model.algorithmexecution.AlgorithmExecution;
import si.result.project.eearly.model.algorithmexecution.AlgorithmExecutionStatus;
import si.result.project.eearly.model.analysis.Analysis;
import si.result.project.eearly.port.algorithmexecution.AlgorithmExecutionRepository;

/** Shared by the tests of the result endpoints. Secrets are generated per run, never written down. */
final class CallbackTokenTestSupport {

    private CallbackTokenTestSupport() {
    }

    static String randomSecret() {
        return UUID.randomUUID() + "-" + UUID.randomUUID();
    }

    static CallbackTokenProperties properties(final String secret) {
        final CallbackTokenProperties properties = new CallbackTokenProperties();
        properties.setTokenSecret(secret);
        return properties;
    }

    /** Puts a RUNNING execution of the analysis into the (mocked) repository. */
    static AlgorithmExecution storeExecution(final AlgorithmExecutionRepository repository, final UUID analysisId) {
        final Analysis analysis = mock(Analysis.class);
        lenient().when(analysis.getId()).thenReturn(analysisId);
        final AlgorithmExecution execution = AlgorithmExecution.builder().id(UUID.randomUUID())
                .analysis(analysis).executionStatus(AlgorithmExecutionStatus.RUNNING).build();
        lenient().when(repository.findById(execution.getId())).thenReturn(Optional.of(execution));
        lenient().when(repository.findByIdForUpdate(execution.getId())).thenReturn(Optional.of(execution));
        lenient().when(repository.save(any(AlgorithmExecution.class))).thenAnswer(i -> i.getArgument(0));
        return execution;
    }

    /** A token as the service builds it, signed with the given secret, whatever the claims. */
    static String sign(final String secret, final UUID executionId, final UUID jti, final Instant expiresAt) {
        try {
            final String json = "{\"executionId\":\"" + executionId + "\",\"jti\":\"" + jti + "\",\"exp\":"
                    + expiresAt.getEpochSecond() + "}";
            final String payload = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(json.getBytes(StandardCharsets.UTF_8));
            final Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return payload + "." + Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    /** Makes the decoder accept {@code value} as a Keycloak token of the given client. */
    static void acceptJwt(final JwtDecoder decoder, final String value, final String clientId) throws Exception {
        final var claims = JWTClaimsSet.parse("{\"sub\": \"caretaker-1\", \"resource_access\": {\"" + clientId
                + "\": {\"roles\": [\"Clinician\"]}}}").getClaims();
        when(decoder.decode(value)).thenReturn(
                Jwt.withTokenValue(value).header("alg", "RS256").claims(c -> c.putAll(claims)).build());
    }

    static String body(final UUID analysisId) {
        return "{\"correlationId\":\"" + UUID.randomUUID() + "\",\"analysisId\":\"" + analysisId
                + "\",\"state\":\"COMPLETED\",\"detected\":true,\"name\":\"model\",\"detections\":[]}";
    }
}
