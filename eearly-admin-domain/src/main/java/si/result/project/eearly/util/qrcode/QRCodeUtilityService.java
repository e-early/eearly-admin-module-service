package si.result.project.eearly.util.qrcode;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import si.result.project.eearly.exception.DomainExceptionCode;
import si.result.project.eearly.model.qrcode.QRCode;
import si.result.spring.boot.bricks.exception.DomainException;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Creates and checks the token of a QR code and checks the onboarding link a QR code encodes. The token and the link
 * are credentials: they are never logged.
 */
@Slf4j
@Service
public class QRCodeUtilityService {

    /** The longest onboarding link that is stored and encoded (the length of the column it is stored in). */
    static final int MAX_ONBOARDING_URL_LENGTH = 512;

    /**
     * How long a QR code can be used for: the validity of the onboarding links of the mobile service
     * ({@code onboarding.code.validity}, an ISO-8601 duration, 30 days by default).
     */
    @Value("${onboarding.code.validity:P30D}")
    private Duration codeValidity = Duration.ofDays(30);

    /** Whether an onboarding link may be a plain {@code http} one (local development only). */
    @Value("${qrcode.allow-http-onboarding-url:false}")
    private boolean allowHttpOnboardingUrl = false;

    /**
     * A random UUID (122 random bits from the JDK's {@code SecureRandom}) and the creation time in milliseconds, which
     * only makes the token unique and adds no secrecy.
     */
    public String generateQrToken() {
        return UUID.randomUUID() + "-" + System.currentTimeMillis();
    }

    public OffsetDateTime generateTokenExpirationTime() {
        return OffsetDateTime.now().plus(codeValidity);
    }

    /**
     * Checks an onboarding link issued by the mobile service before it goes into a QR code: an absolute {@code https}
     * URL ({@code http} only when {@code qrcode.allow-http-onboarding-url} is set) of at most 512 characters.
     * Nothing of the link is logged.
     *
     * @throws DomainException {@code QR_CODE_ONBOARDING_URL_INVALID}
     */
    public void validateOnboardingUrl(final String onboardingUrl) {
        if (onboardingUrl == null || onboardingUrl.isBlank() || onboardingUrl.length() > MAX_ONBOARDING_URL_LENGTH) {
            throw invalidOnboardingUrl("missing or too long");
        }
        final URI uri;
        try {
            uri = new URI(onboardingUrl);
        } catch (URISyntaxException e) {
            throw invalidOnboardingUrl("not a URI");
        }
        final String scheme = uri.getScheme();
        final boolean schemeAllowed = "https".equalsIgnoreCase(scheme)
                || (allowHttpOnboardingUrl && "http".equalsIgnoreCase(scheme));
        if (!schemeAllowed) {
            throw invalidOnboardingUrl("scheme not allowed");
        }
        if (uri.getHost() == null || uri.getHost().isBlank() || uri.getUserInfo() != null) {
            throw invalidOnboardingUrl("not an absolute URL");
        }
    }

    private static DomainException invalidOnboardingUrl(final String reason) {
        log.error("Onboarding link of the mobile service rejected: {}", reason);
        return new DomainException(DomainExceptionCode.QR_CODE_ONBOARDING_URL_INVALID);
    }

    public boolean isQrTokenValid(QRCode qrCode) {
        return qrCode.getToken() != null &&
                qrCode.getExpiresAt() != null &&
                OffsetDateTime.now().isBefore(qrCode.getExpiresAt());
    }

    /** Whether the token is the QR code's own and the code has not expired; compares in constant time. */
    public boolean validateQrToken(QRCode qrCode, String token) {
        return qrCode.getToken() != null &&
                token != null &&
                MessageDigest.isEqual(
                        qrCode.getToken().getBytes(StandardCharsets.UTF_8), token.getBytes(StandardCharsets.UTF_8)) &&
                isQrTokenValid(qrCode);
    }
}
