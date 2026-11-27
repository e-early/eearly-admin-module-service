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

import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@org.junit.jupiter.api.extension.ExtendWith(org.springframework.boot.test.system.OutputCaptureExtension.class)
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
    @DisplayName("The validity of a QR code is configurable")
    void generateTokenExpirationTime_UsesTheConfiguredValidity() {
        ReflectionTestUtils.setField(service, "codeValidity", Duration.ofDays(7));
        final OffsetDateTime before = OffsetDateTime.now();
        final OffsetDateTime expiresAt = service.generateTokenExpirationTime();
        final OffsetDateTime after = OffsetDateTime.now();

        assertFalse(expiresAt.isBefore(before.plus(Duration.ofDays(7))));
        assertFalse(expiresAt.isAfter(after.plus(Duration.ofDays(7))));
    }

    @Test
    @DisplayName("An absolute https onboarding link is accepted")
    void validateOnboardingUrl_AcceptsHttps() {
        service.validateOnboardingUrl("https://mobile.example.org/api/v1/onboarding/abc123");
        service.validateOnboardingUrl("HTTPS://mobile.example.org:8443/o/abc123");
        service.validateOnboardingUrl("https://mobile.example.org/" + "a".repeat(512 - "https://mobile.example.org/".length()));
    }

    @Test
    @DisplayName("A link that is missing, too long, relative, not https or has credentials is refused")
    void validateOnboardingUrl_RefusesTheRest() {
        for (final String url : new String[] {null, "", "  ", "/onboarding/abc", "mobile.example.org/o/abc",
                "http://mobile.example.org/o/abc", "ftp://mobile.example.org/o/abc", "javascript:alert(1)",
                "https:///o/abc", "https://user:pass@mobile.example.org/o/abc", "https://mobile.example.org/a b",
                "https://mobile.example.org/" + "a".repeat(512 - "https://mobile.example.org/".length() + 1)}) {
            final var exception = assertThrows(si.result.spring.boot.bricks.exception.DomainException.class,
                    () -> service.validateOnboardingUrl(url), "accepted: " + url);
            assertEquals(si.result.project.eearly.exception.DomainExceptionCode.QR_CODE_ONBOARDING_URL_INVALID,
                    exception.getCode());
        }
    }

    @Test
    @DisplayName("A plain http link is accepted only when the flag allows it")
    void validateOnboardingUrl_HttpOnlyWhenAllowed() {
        ReflectionTestUtils.setField(service, "allowHttpOnboardingUrl", true);

        service.validateOnboardingUrl("http://localhost:8081/api/v1/onboarding/abc123");
        assertThrows(si.result.spring.boot.bricks.exception.DomainException.class,
                () -> service.validateOnboardingUrl("ftp://localhost/o/abc"));
    }

    @Test
    @DisplayName("A refused link is not logged")
    void validateOnboardingUrl_DoesNotLogTheLink(final org.springframework.boot.test.system.CapturedOutput output) {
        assertThrows(si.result.spring.boot.bricks.exception.DomainException.class,
                () -> service.validateOnboardingUrl("http://mobile.example.org/o/secret-code-4711"));

        assertFalse(output.getAll().contains("secret-code-4711"));
        assertFalse(output.getAll().contains("mobile.example.org"));
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
