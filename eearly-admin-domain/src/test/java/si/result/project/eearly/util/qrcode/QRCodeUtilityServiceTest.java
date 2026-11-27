package si.result.project.eearly.util.qrcode;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import si.result.project.eearly.model.qrcode.QRCode;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class QRCodeUtilityServiceTest {

    private static final Pattern TOKEN_FORMAT = Pattern.compile(
            "[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}-\\d{13}");

    private final QRCodeUtilityService service = new QRCodeUtilityService();

    private static QRCode qrCode(final String token, final OffsetDateTime expiresAt) {
        final QRCode qrCode = mock(QRCode.class);
        when(qrCode.getToken()).thenReturn(token);
        when(qrCode.getExpiresAt()).thenReturn(expiresAt);
        return qrCode;
    }

    @Test
    @DisplayName("A token is a random version 4 UUID and the creation time in milliseconds")
    void generateQrToken_HasTheDocumentedFormat() {
        final String token = service.generateQrToken();

        assertTrue(TOKEN_FORMAT.matcher(token).matches(), "unexpected token format");
        final UUID random = UUID.fromString(token.substring(0, 36));
        assertEquals(4, random.version());
        final long createdAt = Long.parseLong(token.substring(37));
        assertTrue(Math.abs(System.currentTimeMillis() - createdAt) < 5_000);
    }

    @Test
    @DisplayName("Tokens do not repeat")
    void generateQrToken_IsUnique() {
        final Set<String> tokens = new HashSet<>();
        for (int i = 0; i < 10_000; i++) {
            tokens.add(service.generateQrToken());
        }

        assertEquals(10_000, tokens.size());
    }

    @Test
    @DisplayName("A QR code is valid for 30 days")
    void generateTokenExpirationTime_IsThirtyDaysAhead() {
        final OffsetDateTime before = OffsetDateTime.now();
        final OffsetDateTime expiresAt = service.generateTokenExpirationTime();
        final OffsetDateTime after = OffsetDateTime.now();

        assertFalse(expiresAt.isBefore(before.plus(Duration.ofDays(30))));
        assertFalse(expiresAt.isAfter(after.plus(Duration.ofDays(30))));
    }

    @Test
    @DisplayName("A token is valid until it expires")
    void isQrTokenValid_ChecksTheExpiry() {
        assertTrue(service.isQrTokenValid(qrCode("t", OffsetDateTime.now().plusMinutes(1))));
        assertFalse(service.isQrTokenValid(qrCode("t", OffsetDateTime.now().minusSeconds(1))));
        assertFalse(service.isQrTokenValid(qrCode(null, OffsetDateTime.now().plusMinutes(1))));
        assertFalse(service.isQrTokenValid(qrCode("t", null)));
    }

    @Test
    @DisplayName("Only the QR code's own, unexpired token validates")
    void validateQrToken_ComparesTheToken() {
        final QRCode valid = qrCode("secret-token", OffsetDateTime.now().plusMinutes(1));
        final QRCode expired = qrCode("secret-token", OffsetDateTime.now().minusMinutes(1));

        assertTrue(service.validateQrToken(valid, "secret-token"));
        assertFalse(service.validateQrToken(valid, "secret-token "));
        assertFalse(service.validateQrToken(valid, "Secret-token"));
        assertFalse(service.validateQrToken(valid, ""));
        assertFalse(service.validateQrToken(valid, null));
        assertFalse(service.validateQrToken(expired, "secret-token"));
        assertFalse(service.validateQrToken(qrCode(null, OffsetDateTime.now().plusMinutes(1)), null));
    }
}
