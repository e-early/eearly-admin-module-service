package si.result.project.eearly.util.qrcode;

import org.springframework.stereotype.Service;
import si.result.project.eearly.model.qrcode.QRCode;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Creates and checks the token of a QR code. The token is a credential: it is never logged.
 */
@Service
public class QRCodeUtilityService {

    /** How long a QR code can be used for. */
    static final long TOKEN_VALIDITY_DAYS = 30;

    /**
     * A random UUID (122 random bits from the JDK's {@code SecureRandom}) and the creation time in milliseconds, which
     * only makes the token unique and adds no secrecy.
     */
    public String generateQrToken() {
        return UUID.randomUUID() + "-" + System.currentTimeMillis();
    }

    public OffsetDateTime generateTokenExpirationTime() {
        return OffsetDateTime.now().plusDays(TOKEN_VALIDITY_DAYS);
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
