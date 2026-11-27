package si.result.project.eearly.mapper.qrcode;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import si.result.project.eearly.dto.qrcode.QRCodeDTO;
import si.result.project.eearly.dto.qrcode.QRCodeLiteDTO;
import si.result.project.eearly.mapper.caretaker.CaretakerMapperImpl;
import si.result.project.eearly.model.caretaker.Caretaker;
import si.result.project.eearly.model.patient.Patient;
import si.result.project.eearly.model.qrcode.QRCode;
import si.result.project.eearly.model.qrcode.QRCodeStatus;
import si.result.project.eearly.model.qrcode.command.CreateQRCodeCommand;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class QRCodeMapperTest {

    private final QRCodeMapperImpl mapper = new QRCodeMapperImpl();

    private QRCode qrCode;
    private UUID patientId;
    private UUID caretakerId;
    private OffsetDateTime expiresAt;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(mapper, "caretakerMapper", new CaretakerMapperImpl());
        patientId = UUID.randomUUID();
        caretakerId = UUID.randomUUID();
        expiresAt = OffsetDateTime.now().plusDays(30);
        final Patient patient = mock(Patient.class);
        when(patient.getId()).thenReturn(patientId);
        when(patient.getFirstName()).thenReturn("Test");
        when(patient.getLastName()).thenReturn("Patient");
        final Caretaker caretaker = mock(Caretaker.class);
        when(caretaker.getId()).thenReturn(caretakerId);
        when(caretaker.getFirstName()).thenReturn("Test");
        when(caretaker.getLastName()).thenReturn("Caretaker");
        qrCode = QRCode.create(new CreateQRCodeCommand(
                "token", expiresAt, "data:image/png;base64,AAAA", patient, caretaker, "https://example.org/onboarding/1"));
    }

    @Test
    void toDTO_MapsTheQRCodeWithItsPatientAndCaretaker() {
        final QRCodeDTO dto = mapper.toDTO(qrCode);

        assertEquals("token", dto.token());
        assertEquals(expiresAt, dto.expiresAt());
        assertEquals("data:image/png;base64,AAAA", dto.imageUrl());
        assertEquals(QRCodeStatus.ACTIVE, dto.status());
        assertFalse(dto.mobileAppConfigured());
        assertEquals(patientId, dto.patient().id());
        assertEquals("Test", dto.patient().firstName());
        assertNull(dto.patient().patientStatus());
        assertEquals(caretakerId, dto.caretaker().id());
        assertEquals("Caretaker", dto.caretaker().lastName());
    }

    @Test
    void toLiteDTO_MapsThePatientToItsId() {
        final QRCodeLiteDTO dto = mapper.toLiteDTO(qrCode);

        assertEquals(patientId, dto.patientId());
        assertEquals("token", dto.token());
        assertEquals(QRCodeStatus.ACTIVE, dto.status());
    }

    @Test
    void toCreateCommand_PassesTheValuesOn() {
        final Patient patient = qrCode.getPatient();
        final Caretaker caretaker = qrCode.getCaretaker();

        final CreateQRCodeCommand command =
                mapper.toCreateCommand("token", expiresAt, "image", patient, caretaker, "https://example.org/onboarding/1");

        assertEquals("token", command.token());
        assertEquals(expiresAt, command.expiresAt());
        assertEquals("image", command.imageUrl());
        assertSame(patient, command.patient());
        assertSame(caretaker, command.caretaker());
        assertEquals("https://example.org/onboarding/1", command.onboardingUrl());
    }

    @Test
    void theDtosDoNotPrintTheToken() {
        assertFalse(mapper.toDTO(qrCode).toString().contains("token"));
        assertFalse(mapper.toLiteDTO(qrCode).toString().contains("token="));
    }
}
