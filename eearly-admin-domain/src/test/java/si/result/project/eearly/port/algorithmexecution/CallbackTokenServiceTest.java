package si.result.project.eearly.port.algorithmexecution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.EnumSet;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import si.result.project.eearly.config.CallbackTokenProperties;
import si.result.project.eearly.exception.CallbackTokenRejectedException;
import si.result.project.eearly.exception.CallbackTokenRejectedException.Reason;
import si.result.project.eearly.exception.DomainExceptionCode;
import si.result.project.eearly.model.algorithmexecution.AlgorithmExecution;
import si.result.project.eearly.model.algorithmexecution.AlgorithmExecutionStatus;
import si.result.project.eearly.model.analysis.Analysis;
import si.result.spring.boot.bricks.exception.DomainException;

@ExtendWith(MockitoExtension.class)
class CallbackTokenServiceTest {

    /** Generated, never a literal: the tests hold no secret. */
    private static final String SECRET = UUID.randomUUID() + "-" + UUID.randomUUID();

    @Mock
    private AlgorithmExecutionRepository algorithmExecutionRepository;

    private MutableClock clock;
    private CallbackTokenService callbackTokenService;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.parse("2026-06-01T10:00:00Z"));
        callbackTokenService = service(SECRET, Duration.ofMinutes(15));
        lenient().when(algorithmExecutionRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private CallbackTokenService service(final String secret, final Duration ttl) {
        final CallbackTokenProperties properties = new CallbackTokenProperties();
        properties.setTokenSecret(secret);
        properties.setTokenTtl(ttl);
        return new CallbackTokenService(algorithmExecutionRepository, properties, clock);
    }

    private AlgorithmExecution execution(final UUID executionId, final UUID analysisId) {
        final Analysis analysis = mock(Analysis.class);
        lenient().when(analysis.getId()).thenReturn(analysisId);
        return AlgorithmExecution.builder()
                .id(executionId)
                .analysis(analysis)
                .executionStatus(AlgorithmExecutionStatus.RUNNING)
                .build();
    }

    private AlgorithmExecution storedExecution(final UUID executionId, final UUID analysisId) {
        final AlgorithmExecution execution = execution(executionId, analysisId);
        lenient().when(algorithmExecutionRepository.findById(executionId)).thenReturn(Optional.of(execution));
        lenient().when(algorithmExecutionRepository.findByIdForUpdate(executionId)).thenReturn(Optional.of(execution));
        return execution;
    }

    private static Reason reasonOf(final Runnable action) {
        try {
            action.run();
        } catch (CallbackTokenRejectedException exception) {
            return exception.getReason();
        }
        throw new AssertionError("the token was not rejected");
    }

    // --- Opt-in ---

    @Test
    @DisplayName("Without a secret callback tokens are off: none is issued and none is accepted")
    void withoutSecret_isDisabled() {
        for (final String blank : new String[] {null, "", "   "}) {
            final CallbackTokenService disabled = service(blank, Duration.ofMinutes(15));

            assertThat(disabled.isEnabled()).isFalse();
            assertThatThrownBy(() -> disabled.issueToken(UUID.randomUUID())).isInstanceOf(IllegalStateException.class);
            assertThat(reasonOf(() -> disabled.verify("a.b"))).isEqualTo(Reason.DISABLED);
            assertThat(reasonOf(() -> disabled.verifyAndConsume("a.b", UUID.randomUUID()))).isEqualTo(Reason.DISABLED);
        }
        verify(algorithmExecutionRepository, never()).save(any());
    }

    @Test
    @DisplayName("A secret shorter than 32 bytes fails fast, without echoing the secret")
    void shortSecret_failsFast() {
        final String shortSecret = "x".repeat(31);

        assertThatThrownBy(() -> service(shortSecret, Duration.ofMinutes(15)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at least 32 bytes")
                .hasMessageNotContaining(shortSecret);
    }

    @Test
    @DisplayName("32 bytes are enough, counted in bytes and not in characters")
    void secretOfExactlyThirtyTwoBytes_isEnabled() {
        assertThat(service("x".repeat(32), Duration.ofMinutes(15)).isEnabled()).isTrue();
        // 16 characters of 2 bytes each
        assertThat(service("é".repeat(16), Duration.ofMinutes(15)).isEnabled()).isTrue();
        assertThatThrownBy(() -> service("é".repeat(15), Duration.ofMinutes(15)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("A non-positive time to live fails fast")
    void nonPositiveTtl_failsFast() {
        assertThatThrownBy(() -> service(SECRET, Duration.ZERO)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> service(SECRET, Duration.ofSeconds(-1))).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("The default time to live is 15 minutes")
    void defaultTtl_isFifteenMinutes() {
        assertThat(new CallbackTokenProperties().getTokenTtl()).isEqualTo(Duration.ofMinutes(15));
    }

    @Test
    @DisplayName("The properties print no secret")
    void propertiesToString_holdsNoSecret() {
        final CallbackTokenProperties properties = new CallbackTokenProperties();
        properties.setTokenSecret(SECRET);

        assertThat(properties.toString()).doesNotContain(SECRET);
    }

    // --- Transaction semantics ---

    /**
     * The token is used up in the same transaction as the processing of the result it authorises: if the processing
     * fails the use is rolled back and the provider may retry. Issuing, on the other hand, must commit before the
     * provider can possibly call back.
     */
    @Test
    @DisplayName("Issuing commits on its own, using the token joins the caller's transaction")
    void transactionPropagation() throws NoSuchMethodException {
        final Method issue = CallbackTokenService.class.getDeclaredMethod("issueToken", UUID.class);
        final Method consume = CallbackTokenService.class.getDeclaredMethod("verifyAndConsume", String.class, UUID.class);

        assertThat(issue.getAnnotation(Transactional.class).propagation()).isEqualTo(Propagation.REQUIRES_NEW);
        assertThat(consume.getAnnotation(Transactional.class)).isNotNull();
        assertThat(consume.getAnnotation(Transactional.class).propagation()).isEqualTo(Propagation.REQUIRED);
    }

    @Test
    @DisplayName("Using the token locks the execution row, so concurrent callbacks cannot both succeed")
    void verifyAndConsume_locksTheRow() {
        final UUID executionId = UUID.randomUUID();
        final UUID analysisId = UUID.randomUUID();
        storedExecution(executionId, analysisId);
        final String token = callbackTokenService.issueToken(executionId);

        callbackTokenService.verifyAndConsume(token, analysisId);

        verify(algorithmExecutionRepository).findByIdForUpdate(executionId);
    }

    // --- Issuing ---

    @Test
    void issueToken_throwsWhenExecutionNotFound() {
        final UUID executionId = UUID.randomUUID();
        when(algorithmExecutionRepository.findById(executionId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> callbackTokenService.issueToken(executionId))
                .isInstanceOf(DomainException.class)
                .satisfies(e -> assertThat(((DomainException) e).getCode())
                        .isEqualTo(DomainExceptionCode.ALGORITHM_EXECUTION_NOT_FOUND));
    }

    @Test
    @DisplayName("An issued token carries execution id, jti and expiry, and the execution stores the jti, not the token")
    void issueToken_storesOnlyTheTokenId() throws Exception {
        final UUID executionId = UUID.randomUUID();
        final AlgorithmExecution execution = storedExecution(executionId, UUID.randomUUID());

        final String token = callbackTokenService.issueToken(executionId);

        final String[] parts = token.split("\\.");
        assertThat(parts).hasSize(2);
        final JsonNode payload = new ObjectMapper().readTree(Base64.getUrlDecoder().decode(parts[0]));
        assertThat(payload.get("executionId").asText()).isEqualTo(executionId.toString());
        assertThat(payload.get("jti").asText()).isEqualTo(execution.getCallbackTokenId().toString());
        assertThat(payload.get("exp").asLong()).isEqualTo(clock.instant().plus(Duration.ofMinutes(15)).getEpochSecond());
        assertThat(execution.getCallbackTokenExpiresAt()).isEqualTo(clock.instant().plus(Duration.ofMinutes(15)));
        assertThat(execution.getCallbackTokenConsumedAt()).isNull();
        // URL-safe: it goes into a query parameter as it is
        assertThat(token).matches("[A-Za-z0-9_\\-.]+");
    }

    // --- Verifying ---

    @Test
    void issueThenVerifyAndConsume_succeeds_forTheMatchingAnalysis() {
        final UUID executionId = UUID.randomUUID();
        final UUID analysisId = UUID.randomUUID();
        final AlgorithmExecution execution = storedExecution(executionId, analysisId);

        final String token = callbackTokenService.issueToken(executionId);
        callbackTokenService.verifyAndConsume(token, analysisId);

        assertThat(execution.getCallbackTokenConsumedAt()).isEqualTo(clock.instant());
        verify(algorithmExecutionRepository, org.mockito.Mockito.atLeastOnce()).save(execution);
    }

    @Test
    @DisplayName("verify checks the token without using it up and without touching the database")
    void verify_doesNotConsume() {
        final UUID executionId = UUID.randomUUID();
        final UUID analysisId = UUID.randomUUID();
        final AlgorithmExecution execution = storedExecution(executionId, analysisId);
        final String token = callbackTokenService.issueToken(executionId);

        final CallbackTokenService.VerifiedCallbackToken verified = callbackTokenService.verify(token);

        assertThat(verified.executionId()).isEqualTo(executionId);
        assertThat(verified.jti()).isEqualTo(execution.getCallbackTokenId());
        assertThat(execution.getCallbackTokenConsumedAt()).isNull();
        verify(algorithmExecutionRepository, never()).findByIdForUpdate(any());
    }

    @Test
    void verifyAndConsume_rejectsReplay_ofAnAlreadyConsumedToken() {
        final UUID executionId = UUID.randomUUID();
        final UUID analysisId = UUID.randomUUID();
        storedExecution(executionId, analysisId);
        final String token = callbackTokenService.issueToken(executionId);
        callbackTokenService.verifyAndConsume(token, analysisId);

        assertThat(reasonOf(() -> callbackTokenService.verifyAndConsume(token, analysisId)))
                .isEqualTo(Reason.ALREADY_USED);
    }

    @Test
    void verifyAndConsume_rejectsToken_whenAnalysisIdDoesNotMatch() {
        final UUID executionId = UUID.randomUUID();
        final AlgorithmExecution execution = storedExecution(executionId, UUID.randomUUID());
        final String token = callbackTokenService.issueToken(executionId);

        assertThat(reasonOf(() -> callbackTokenService.verifyAndConsume(token, UUID.randomUUID())))
                .isEqualTo(Reason.ANALYSIS_MISMATCH);
        assertThat(execution.getCallbackTokenConsumedAt()).as("a refused token is not used up").isNull();
    }

    @Test
    @DisplayName("Without an analysis id to check against the token is refused, never accepted unbound")
    void verifyAndConsume_rejectsToken_whenNoAnalysisIdIsGiven() {
        final UUID executionId = UUID.randomUUID();
        storedExecution(executionId, UUID.randomUUID());
        final String token = callbackTokenService.issueToken(executionId);

        assertThat(reasonOf(() -> callbackTokenService.verifyAndConsume(token, null)))
                .isEqualTo(Reason.ANALYSIS_MISMATCH);
    }

    @Test
    void verifyAndConsume_rejectsToken_ofAnExecutionWithoutAnalysis() {
        final UUID executionId = UUID.randomUUID();
        final AlgorithmExecution execution = AlgorithmExecution.builder().id(executionId)
                .executionStatus(AlgorithmExecutionStatus.RUNNING).build();
        when(algorithmExecutionRepository.findById(executionId)).thenReturn(Optional.of(execution));
        when(algorithmExecutionRepository.findByIdForUpdate(executionId)).thenReturn(Optional.of(execution));
        final String token = callbackTokenService.issueToken(executionId);

        assertThat(reasonOf(() -> callbackTokenService.verifyAndConsume(token, UUID.randomUUID())))
                .isEqualTo(Reason.ANALYSIS_MISMATCH);
    }

    @Test
    void verifyAndConsume_rejectsExpiredToken() {
        final UUID executionId = UUID.randomUUID();
        final UUID analysisId = UUID.randomUUID();
        storedExecution(executionId, analysisId);
        final String token = callbackTokenService.issueToken(executionId);

        clock.advance(Duration.ofMinutes(15));

        assertThat(reasonOf(() -> callbackTokenService.verifyAndConsume(token, analysisId))).isEqualTo(Reason.EXPIRED);
        assertThat(reasonOf(() -> callbackTokenService.verify(token))).isEqualTo(Reason.EXPIRED);
    }

    @Test
    void verify_acceptsTheTokenUntilItsExpiry() {
        final UUID executionId = UUID.randomUUID();
        storedExecution(executionId, UUID.randomUUID());
        final String token = callbackTokenService.issueToken(executionId);

        clock.advance(Duration.ofMinutes(14).plusSeconds(59));

        assertThat(callbackTokenService.verify(token).executionId()).isEqualTo(executionId);
    }

    @Test
    void verify_rejectsTamperedSignature() {
        final UUID executionId = UUID.randomUUID();
        storedExecution(executionId, UUID.randomUUID());
        final String token = callbackTokenService.issueToken(executionId);
        // Second-to-last character, not the last: the last one of a base64url 32-byte HMAC carries two padding bits
        // the decoder discards, so flipping only that one can leave the decoded signature unchanged.
        final int index = token.length() - 2;
        final String tampered = token.substring(0, index) + (token.charAt(index) == 'A' ? 'B' : 'A')
                + token.substring(index + 1);

        assertThat(reasonOf(() -> callbackTokenService.verify(tampered))).isEqualTo(Reason.SIGNATURE);
    }

    @Test
    @DisplayName("A changed payload fails the signature, even one that names another execution")
    void verify_rejectsTamperedPayload() throws Exception {
        final UUID executionId = UUID.randomUUID();
        storedExecution(executionId, UUID.randomUUID());
        final String token = callbackTokenService.issueToken(executionId);
        final String[] parts = token.split("\\.");
        final String forgedPayload = "{\"executionId\":\"" + UUID.randomUUID() + "\",\"jti\":\"" + UUID.randomUUID()
                + "\",\"exp\":" + Instant.now().plusSeconds(86_400).getEpochSecond() + "}";
        final String forged = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(forgedPayload.getBytes(StandardCharsets.UTF_8)) + "." + parts[1];

        assertThat(reasonOf(() -> callbackTokenService.verify(forged))).isEqualTo(Reason.SIGNATURE);
    }

    @Test
    void verify_rejectsTokenSignedWithADifferentSecret() {
        final UUID executionId = UUID.randomUUID();
        storedExecution(executionId, UUID.randomUUID());
        final String token = callbackTokenService.issueToken(executionId);
        final CallbackTokenService other = service(UUID.randomUUID() + "-" + UUID.randomUUID(), Duration.ofMinutes(15));

        assertThat(reasonOf(() -> other.verify(token))).isEqualTo(Reason.SIGNATURE);
    }

    @Test
    void verify_rejectsMalformedTokens() {
        for (final String malformed : new String[] {null, "", "   ", "not-a-real-token", ".", "a.", ".b", "a.b.c",
                "%%%.%%%", "e30.e30", "YWJj.!!!"}) {
            assertThat(reasonOf(() -> callbackTokenService.verify(malformed)))
                    .as("token: %s", malformed)
                    .isIn(Reason.MALFORMED, Reason.SIGNATURE);
        }
    }

    @Test
    @DisplayName("A token signed with our secret whose payload names no execution is malformed")
    void verify_rejectsAValidlySignedNonTokenPayload() throws Exception {
        final String payload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"exp\":4000000000}".getBytes(StandardCharsets.UTF_8));
        final javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
        mac.init(new javax.crypto.spec.SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        final String token = payload + "." + Base64.getUrlEncoder().withoutPadding()
                .encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));

        assertThat(reasonOf(() -> callbackTokenService.verify(token))).isEqualTo(Reason.MALFORMED);
    }

    @Test
    @DisplayName("A newer token replaces the older one of the same execution")
    void verifyAndConsume_rejectsASupersededToken() {
        final UUID executionId = UUID.randomUUID();
        final UUID analysisId = UUID.randomUUID();
        storedExecution(executionId, analysisId);
        final String first = callbackTokenService.issueToken(executionId);
        final String second = callbackTokenService.issueToken(executionId);

        assertThat(reasonOf(() -> callbackTokenService.verifyAndConsume(first, analysisId)))
                .isEqualTo(Reason.SUPERSEDED);
        callbackTokenService.verifyAndConsume(second, analysisId);
    }

    @Test
    void verifyAndConsume_rejectsAnUnknownExecution() {
        final UUID executionId = UUID.randomUUID();
        final AlgorithmExecution execution = storedExecution(executionId, UUID.randomUUID());
        final String token = callbackTokenService.issueToken(executionId);
        when(algorithmExecutionRepository.findByIdForUpdate(executionId)).thenReturn(Optional.empty());

        assertThat(reasonOf(() -> callbackTokenService.verifyAndConsume(token, UUID.randomUUID())))
                .isEqualTo(Reason.UNKNOWN_EXECUTION);
        assertThat(execution.getCallbackTokenConsumedAt()).isNull();
    }

    @Test
    @DisplayName("The token of an execution that has ended is refused")
    void verifyAndConsume_rejectsATokenOfAnEndedExecution() {
        for (final AlgorithmExecutionStatus ended : EnumSet.of(AlgorithmExecutionStatus.COMPLETED,
                AlgorithmExecutionStatus.FAILED, AlgorithmExecutionStatus.CANCELLED)) {
            final UUID executionId = UUID.randomUUID();
            final UUID analysisId = UUID.randomUUID();
            final AlgorithmExecution execution = storedExecution(executionId, analysisId);
            final String token = callbackTokenService.issueToken(executionId);
            final AlgorithmExecution endedExecution = AlgorithmExecution.builder().id(executionId)
                    .analysis(execution.getAnalysis()).executionStatus(ended)
                    .callbackTokenId(execution.getCallbackTokenId())
                    .callbackTokenExpiresAt(execution.getCallbackTokenExpiresAt()).build();
            when(algorithmExecutionRepository.findByIdForUpdate(executionId)).thenReturn(Optional.of(endedExecution));

            assertThat(reasonOf(() -> callbackTokenService.verifyAndConsume(token, analysisId)))
                    .as("status %s", ended)
                    .isEqualTo(Reason.EXECUTION_NOT_ACTIVE);
        }
    }

    @Test
    @DisplayName("Every refusal has the same message, so a caller learns nothing about the reason")
    void everyRefusalHasTheSameMessage() {
        for (final Reason reason : Reason.values()) {
            assertThat(new CallbackTokenRejectedException(reason).getMessage())
                    .isEqualTo(CallbackTokenRejectedException.MESSAGE);
        }
    }

    /** A clock the test moves. */
    private static final class MutableClock extends Clock {
        private Instant now;

        private MutableClock(final Instant now) {
            this.now = now;
        }

        private void advance(final Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(final ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
