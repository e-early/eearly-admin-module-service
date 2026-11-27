package si.result.project.eearly.model.qrcode;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import si.result.project.eearly.exception.DomainExceptionCode;
import si.result.project.eearly.model.caretaker.Caretaker;
import si.result.project.eearly.model.patient.Patient;
import si.result.project.eearly.model.qrcode.command.ConfigureQRCodeCommand;
import si.result.project.eearly.model.qrcode.command.CreateQRCodeCommand;
import si.result.project.eearly.model.qrcode.command.UpdateQRCodeCommand;
import si.result.spring.boot.bricks.exception.DomainException;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.UUID;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class QRCodeTest {

    private static final String ONBOARDING_URL = "https://mobile.example.org/api/v1/onboarding/1";

    private Patient patient;
    private Caretaker caretaker;
    private OffsetDateTime expiresAt;

    @BeforeEach
    void setUp() {
        patient = mock(Patient.class);
        caretaker = mock(Caretaker.class);
        expiresAt = OffsetDateTime.now().plusDays(30);
    }

    private QRCode created() {
        return QRCode.create(new CreateQRCodeCommand("token", expiresAt, "image", patient, caretaker, ONBOARDING_URL));
    }

    private static void assertCode(final DomainExceptionCode expected, final Supplier<?> action) {
        final DomainException exception = assertThrows(DomainException.class, action::get);
        assertEquals(expected, exception.getCode());
    }

    @Test
    @DisplayName("A new QR code is active and the app is not yet configured")
    void create_IsActiveAndNotConfigured() {
        final QRCode qrCode = created();

        assertEquals(QRCodeStatus.ACTIVE, qrCode.getStatus());
        assertFalse(qrCode.getMobileAppConfigured());
        assertEquals("token", qrCode.getToken());
        assertEquals(expiresAt, qrCode.getExpiresAt());
        assertEquals("image", qrCode.getImageUrl());
        assertEquals(ONBOARDING_URL, qrCode.getOnboardingUrl());
        assertSame(patient, qrCode.getPatient());
        assertSame(caretaker, qrCode.getCaretaker());
    }

    @Test
    @DisplayName("Every value of a new QR code is required")
    void create_RequiresEveryValue() {
        assertCode(DomainExceptionCode.QR_CODE_TOKEN_NULL,
                () -> QRCode.create(new CreateQRCodeCommand(null, expiresAt, "image", patient, caretaker, ONBOARDING_URL)));
        assertCode(DomainExceptionCode.QR_CODE_TOKEN_EXPIRATION_DATE_NULL,
                () -> QRCode.create(new CreateQRCodeCommand("token", null, "image", patient, caretaker, ONBOARDING_URL)));
        assertCode(DomainExceptionCode.QR_CODE_URL_NULL,
                () -> QRCode.create(new CreateQRCodeCommand("token", expiresAt, null, patient, caretaker, ONBOARDING_URL)));
        assertCode(DomainExceptionCode.QR_CODE_PATIENT_NULL,
                () -> QRCode.create(new CreateQRCodeCommand("token", expiresAt, "image", null, caretaker, ONBOARDING_URL)));
        assertCode(DomainExceptionCode.QR_CODE_CARETAKER_NULL,
                () -> QRCode.create(new CreateQRCodeCommand("token", expiresAt, "image", patient, null, ONBOARDING_URL)));
        assertCode(DomainExceptionCode.QR_CODE_ONBOARDING_URL_NULL,
                () -> QRCode.create(new CreateQRCodeCommand("token", expiresAt, "image", patient, caretaker, null)));
        assertCode(DomainExceptionCode.QR_CODE_ONBOARDING_URL_BLANK,
                () -> QRCode.create(new CreateQRCodeCommand("token", expiresAt, "image", patient, caretaker, "")));
    }

    @Test
    @DisplayName("Configuring the app marks the QR code as used")
    void configure_MarksTheQRCodeAsUsed() {
        final QRCode qrCode = created();

        qrCode.configure(new ConfigureQRCodeCommand(UUID.randomUUID(), true));

        assertEquals(QRCodeStatus.USED, qrCode.getStatus());
        assertTrue(qrCode.getMobileAppConfigured());
    }

    @Test
    @DisplayName("Un-configuring keeps the status")
    void configure_WithFalse_KeepsTheStatus() {
        final QRCode qrCode = created();

        qrCode.configure(new ConfigureQRCodeCommand(UUID.randomUUID(), false));

        assertEquals(QRCodeStatus.ACTIVE, qrCode.getStatus());
        assertFalse(qrCode.getMobileAppConfigured());
    }

    @Test
    @DisplayName("Update replaces the values and requires each of them")
    void update_ReplacesTheValues() {
        final QRCode qrCode = created();
        final OffsetDateTime later = expiresAt.plusDays(1);

        qrCode.update(new UpdateQRCodeCommand(UUID.randomUUID(), "other", later, "other-image", QRCodeStatus.REVOKED, true));

        assertEquals("other", qrCode.getToken());
        assertEquals(later, qrCode.getExpiresAt());
        assertEquals("other-image", qrCode.getImageUrl());
        assertEquals(QRCodeStatus.REVOKED, qrCode.getStatus());
        assertTrue(qrCode.getMobileAppConfigured());
        assertCode(DomainExceptionCode.QR_CODE_STATUS_NULL,
                () -> { qrCode.update(new UpdateQRCodeCommand(null, "t", later, "i", null, true)); return null; });
        assertCode(DomainExceptionCode.QR_CODE_MOBILE_APP_CONFIGURED_NULL,
                () -> { qrCode.update(new UpdateQRCodeCommand(null, "t", later, "i", QRCodeStatus.ACTIVE, null)); return null; });
    }

    @Test
    @DisplayName("The statuses are those of the QR code table's enum type")
    void statuses() {
        assertEquals(java.util.List.of("ACTIVE", "USED", "EXPIRED", "REVOKED"),
                Arrays.stream(QRCodeStatus.values()).map(Enum::name).toList());
    }

    @Test
    @DisplayName("The commands do not print the token")
    void commandsDoNotPrintTheToken() {
        final String token = "secret-token-value";

        assertFalse(new CreateQRCodeCommand(token, expiresAt, "image", patient, caretaker, ONBOARDING_URL).toString().contains(token));
        assertFalse(new UpdateQRCodeCommand(UUID.randomUUID(), token, expiresAt, "image", QRCodeStatus.ACTIVE, false)
                .toString().contains(token));
    }

    @Test
    @DisplayName("Patient and caretaker do not navigate back to their QR codes")
    void patientAndCaretakerHaveNoBackReference() {
        assertFalse(Arrays.stream(Patient.class.getDeclaredFields()).anyMatch(f -> f.getType() == QRCode.class),
                "Patient has a QRCode field");
        assertFalse(Arrays.stream(Caretaker.class.getDeclaredFields())
                .anyMatch(f -> f.getType() == QRCode.class || f.getGenericType().getTypeName().contains(QRCode.class.getName())),
                "Caretaker has a QRCode field");
    }
}
