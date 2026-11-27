package si.result.project.eearly.port.algorithmexecution;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import si.result.project.eearly.config.CallbackTokenProperties;
import si.result.project.eearly.exception.CallbackTokenRejectedException;
import si.result.project.eearly.exception.CallbackTokenRejectedException.Reason;
import si.result.project.eearly.exception.DomainExceptionCode;
import si.result.project.eearly.model.algorithmexecution.AlgorithmExecution;
import si.result.spring.boot.bricks.exception.DomainException;

/**
 * Issues and verifies the single-use bearer token a webhook-style algorithm provider presents when it reports its
 * result to {@code POST /api/v1/analyses/{id}/result}. This service is both the only issuer and the only verifier, so
 * a plain HMAC-signed payload is used instead of a standard JWT: nothing outside this service ever parses the token,
 * and the provider only echoes it back verbatim as the {@code token} query parameter of the callback URL it was given.
 *
 * <p>Callback tokens are <strong>opt-in</strong>. They are enabled only while
 * {@code algorithm.callback.token-secret} holds a secret of at least
 * {@value CallbackTokenProperties#MIN_SECRET_BYTES} bytes. A secret that is set but too short fails the start of the
 * application (a silent fallback to "no tokens" would leave a provider without a working callback). Without a secret
 * no token is issued or accepted.
 *
 * <p>A token is the Base64URL of {@code {executionId, jti, exp}} and the Base64URL of its HMAC-SHA256, joined by a
 * dot. The {@code jti} is stored on the execution, so the token is single use and a newer token replaces an older
 * one. The token itself is never stored and never logged.
 */
@Slf4j
@Service
public class CallbackTokenService {

    /**
     * Request attribute that carries the token of a request authenticated by it from the security filter to the
     * controller. It is only set for a request that was authenticated by the token and not by a JWT.
     */
    public static final String VERIFIED_TOKEN_ATTRIBUTE =
            "si.result.project.eearly.port.algorithmexecution.CallbackTokenService.VERIFIED_TOKEN";

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    private final AlgorithmExecutionRepository algorithmExecutionRepository;
    private final byte[] secret;
    private final Duration tokenTtl;
    private final Clock clock;

    @Autowired
    public CallbackTokenService(
            final AlgorithmExecutionRepository algorithmExecutionRepository,
            final CallbackTokenProperties properties) {
        this(algorithmExecutionRepository, properties, Clock.systemUTC());
    }

    CallbackTokenService(
            final AlgorithmExecutionRepository algorithmExecutionRepository,
            final CallbackTokenProperties properties,
            final Clock clock) {
        this.algorithmExecutionRepository = algorithmExecutionRepository;
        this.clock = clock;
        this.tokenTtl = properties.getTokenTtl();
        final String configured = properties.getTokenSecret();
        if (configured == null || configured.isBlank()) {
            this.secret = null;
            log.info("Callback tokens are disabled (algorithm.callback.token-secret is not set)");
            return;
        }
        final byte[] secretBytes = configured.getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < CallbackTokenProperties.MIN_SECRET_BYTES) {
            throw new IllegalStateException("algorithm.callback.token-secret must be at least "
                    + CallbackTokenProperties.MIN_SECRET_BYTES + " bytes long (for example `openssl rand -base64 48`)");
        }
        if (tokenTtl == null || tokenTtl.isZero() || tokenTtl.isNegative()) {
            throw new IllegalStateException("algorithm.callback.token-ttl must be a positive duration");
        }
        this.secret = secretBytes;
        log.info("Callback tokens are enabled (token-ttl={})", tokenTtl);
    }

    /** Whether callback tokens are enabled, that is whether a valid secret is configured. */
    public boolean isEnabled() {
        return secret != null;
    }

    /**
     * Issues a new token for the execution and records its identifier on the execution, replacing an earlier one.
     *
     * <p>Takes the execution's id rather than an entity on purpose: by the time this runs, the caller's in-memory
     * execution (typically loaded once at the start of a long-running asynchronous flow) may be several status
     * transitions stale. Saving that copy would collide with the optimistic lock of rows other transactions already
     * advanced, so the current row is re-read here. It commits in its own transaction, because the token must be
     * stored before the provider can possibly call back.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String issueToken(final UUID algorithmExecutionId) {
        requireEnabled();
        final AlgorithmExecution execution = algorithmExecutionRepository.findById(algorithmExecutionId)
                .orElseThrow(() -> new DomainException(DomainExceptionCode.ALGORITHM_EXECUTION_NOT_FOUND));

        final UUID jti = UUID.randomUUID();
        final Instant expiresAt = clock.instant().plus(tokenTtl);

        execution.issueCallbackToken(jti, expiresAt);
        algorithmExecutionRepository.save(execution);

        return sign(new TokenPayload(execution.getId(), jti, expiresAt.getEpochSecond()));
    }

    /**
     * Checks the format, the signature (in constant time) and the expiry of the token, without touching the
     * database. Used by the security filter to refuse a bad token before the request reaches any application code.
     * It does not use the token up, see {@link #verifyAndConsume(String, UUID)}.
     *
     * @throws CallbackTokenRejectedException if the token is not valid
     */
    public VerifiedCallbackToken verify(final String token) {
        if (!isEnabled()) {
            throw new CallbackTokenRejectedException(Reason.DISABLED);
        }
        final TokenPayload payload = decodeAndVerifySignature(token);
        if (!clock.instant().isBefore(Instant.ofEpochSecond(payload.exp()))) {
            throw new CallbackTokenRejectedException(Reason.EXPIRED);
        }
        return new VerifiedCallbackToken(payload.executionId(), payload.jti(), Instant.ofEpochSecond(payload.exp()));
    }

    /**
     * Verifies the token, checks that it was issued for {@code expectedAnalysisId} and uses it up.
     *
     * <p>Deliberately joins the caller's transaction ({@code REQUIRED}), so the use of the token commits or rolls
     * back together with the processing of the result it authorises: a result that fails to process does not burn
     * the token and the provider can retry. The row of the execution is locked ({@code SELECT ... FOR UPDATE}) until
     * then, so two concurrent callbacks with the same token cannot both succeed: the second one waits, then finds
     * the token used. Everything the caller does to the same execution afterwards must therefore join the same
     * transaction too and not start its own one, which would wait for that lock.
     *
     * @throws CallbackTokenRejectedException for any token that is not valid, whatever the reason
     */
    @Transactional
    public void verifyAndConsume(final String token, final UUID expectedAnalysisId) {
        final VerifiedCallbackToken verified = verify(token);
        if (expectedAnalysisId == null) {
            throw new CallbackTokenRejectedException(Reason.ANALYSIS_MISMATCH);
        }

        final AlgorithmExecution execution = algorithmExecutionRepository.findByIdForUpdate(verified.executionId())
                .orElseThrow(() -> new CallbackTokenRejectedException(Reason.UNKNOWN_EXECUTION));

        if (execution.getAnalysis() == null || !expectedAnalysisId.equals(execution.getAnalysis().getId())) {
            throw new CallbackTokenRejectedException(Reason.ANALYSIS_MISMATCH);
        }

        execution.consumeCallbackToken(verified.jti(), clock.instant());
        algorithmExecutionRepository.save(execution);
    }

    private void requireEnabled() {
        if (!isEnabled()) {
            throw new IllegalStateException("Callback tokens are not enabled (algorithm.callback.token-secret)");
        }
    }

    private String sign(final TokenPayload payload) {
        final String encodedPayload;
        try {
            encodedPayload = ENCODER.encodeToString(
                    OBJECT_MAPPER.writeValueAsString(payload).getBytes(StandardCharsets.UTF_8));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to serialize the callback token payload", exception);
        }
        return encodedPayload + "." + ENCODER.encodeToString(hmac(encodedPayload));
    }

    private TokenPayload decodeAndVerifySignature(final String token) {
        if (token == null || token.isBlank()) {
            throw new CallbackTokenRejectedException(Reason.MALFORMED);
        }
        final int separatorIndex = token.indexOf('.');
        if (separatorIndex <= 0 || separatorIndex == token.length() - 1) {
            throw new CallbackTokenRejectedException(Reason.MALFORMED);
        }

        final String encodedPayload = token.substring(0, separatorIndex);
        final byte[] providedSignature;
        try {
            providedSignature = DECODER.decode(token.substring(separatorIndex + 1));
        } catch (IllegalArgumentException exception) {
            throw new CallbackTokenRejectedException(Reason.MALFORMED);
        }

        // Constant time: the payload is parsed only after the signature has proven to be ours.
        if (!MessageDigest.isEqual(hmac(encodedPayload), providedSignature)) {
            throw new CallbackTokenRejectedException(Reason.SIGNATURE);
        }

        try {
            final TokenPayload payload = OBJECT_MAPPER.readValue(
                    new String(DECODER.decode(encodedPayload), StandardCharsets.UTF_8), TokenPayload.class);
            if (payload.executionId() == null || payload.jti() == null) {
                throw new CallbackTokenRejectedException(Reason.MALFORMED);
            }
            return payload;
        } catch (CallbackTokenRejectedException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new CallbackTokenRejectedException(Reason.MALFORMED);
        }
    }

    private byte[] hmac(final String data) {
        try {
            final Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret, HMAC_ALGORITHM));
            return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException | InvalidKeyException exception) {
            throw new IllegalStateException("Unable to compute the callback token signature", exception);
        }
    }

    /** What a token that passed {@link #verify(String)} says. */
    public record VerifiedCallbackToken(UUID executionId, UUID jti, Instant expiresAt) {
    }

    private record TokenPayload(UUID executionId, UUID jti, long exp) {
    }
}
