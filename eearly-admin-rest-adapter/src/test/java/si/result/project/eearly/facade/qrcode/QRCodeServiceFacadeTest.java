package si.result.project.eearly.facade.qrcode;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.web.PagedModel;
import si.result.project.eearly.dto.qrcode.QRCodeDTO;
import si.result.project.eearly.dto.qrcode.QRCodeGenerateDTO;
import si.result.project.eearly.dto.qrcode.QRCodeLiteDTO;
import si.result.project.eearly.dto.qrcode.QRCodeSendEmailDTO;
import si.result.project.eearly.exception.DomainExceptionCode;
import si.result.project.eearly.mapper.qrcode.QRCodeMapper;
import si.result.project.eearly.model.caretaker.Caretaker;
import si.result.project.eearly.model.email.command.SendQRCodeEmailCommand;
import si.result.project.eearly.model.patient.Patient;
import si.result.project.eearly.model.qrcode.QRCode;
import si.result.project.eearly.model.qrcode.QRCodeStatus;
import si.result.project.eearly.model.qrcode.command.CreateQRCodeCommand;
import si.result.project.eearly.port.caretaker.CaretakerService;
import si.result.project.eearly.port.email.AsyncEmailService;
import si.result.project.eearly.port.patient.PatientService;
import si.result.project.eearly.port.qrcode.QRCodeService;
import si.result.project.eearly.util.qrcode.QRCodeGenerateType;
import si.result.project.eearly.util.qrcode.QRCodeImageService;
import si.result.project.eearly.util.qrcode.QRCodeUtilityService;
import si.result.rest.filter.Filter;
import si.result.spring.boot.bricks.auth.AuthenticationProvider;
import si.result.spring.boot.bricks.exception.DomainException;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class QRCodeServiceFacadeTest {

    @Mock
    private QRCodeService qrCodeService;
    @Mock
    private PatientService patientService;
    @Mock
    private QRCodeUtilityService qrCodeUtilityService;
    @Mock
    private QRCodeImageService qrCodeImageService;
    @Mock
    private QRCodeMapper qrCodeMapper;
    @Mock
    private AsyncEmailService asyncEmailService;
    @Mock
    private CaretakerService caretakerService;
    @Mock
    private AuthenticationProvider authenticationProvider;
    @Mock
    private Filter filter;

    @InjectMocks
    private QRCodeServiceFacade qrCodeServiceFacade;

    private UUID keycloakId;
    private UUID caretakerId;
    private Caretaker caretaker;
    private UUID patientId;
    private Patient patient;
    private UUID qrCodeId;
    private QRCode qrCode;

    @BeforeEach
    void setUp() {
        keycloakId = UUID.randomUUID();
        caretakerId = UUID.randomUUID();
        caretaker = mock(Caretaker.class);
        lenient().when(caretaker.getId()).thenReturn(caretakerId);
        lenient().when(authenticationProvider.getId()).thenReturn(keycloakId.toString());
        lenient().when(caretakerService.findByKeycloakId(keycloakId)).thenReturn(caretaker);

        patientId = UUID.randomUUID();
        patient = mock(Patient.class);
        lenient().when(patient.getId()).thenReturn(patientId);
        qrCodeId = UUID.randomUUID();
        qrCode = mock(QRCode.class);
        lenient().when(qrCode.getPatient()).thenReturn(patient);
    }

    private void patientIsNotTheCaretakers() {
        when(patientService.findByIdForCaretaker(patientId, caretakerId))
                .thenThrow(new DomainException(DomainExceptionCode.PATIENT_NOT_FOUND));
    }

    private static void assertPatientNotFound(final DomainException exception) {
        assertEquals(DomainExceptionCode.PATIENT_NOT_FOUND, exception.getCode());
    }

    @Nested
    @DisplayName("Get QR codes")
    class GetPageTest {

        @Test
        @DisplayName("Should list only the QR codes of the caretaker's patients")
        void getPage_ShouldFilterByAuthenticatedCaretaker() {
            Specification<QRCode> specification = mock(Specification.class);
            Pageable pageable = Pageable.unpaged();
            Page<QRCode> page = new PageImpl<>(List.of(qrCode));
            QRCodeLiteDTO dto = mock(QRCodeLiteDTO.class);
            when(filter.toSpecification()).thenReturn((Specification) specification);
            when(qrCodeService.findAllForCaretaker(specification, pageable, caretakerId)).thenReturn(page);
            when(qrCodeMapper.toLiteDTO(qrCode)).thenReturn(dto);

            PagedModel<QRCodeLiteDTO> result = qrCodeServiceFacade.getPage(filter, pageable);

            assertEquals(List.of(dto), result.getContent());
            verify(qrCodeService, never()).findAll(any(), any());
        }
    }

    @Nested
    @DisplayName("Authenticated caller")
    class CallerTest {

        @Test
        @DisplayName("Should find no QR codes for a token whose subject is no caretaker's Keycloak id")
        void getPage_WhenSubjectIsNoCaretaker_ShouldThrowCaretakerNotFound() {
            UUID other = UUID.randomUUID();
            when(authenticationProvider.getId()).thenReturn(other.toString());
            when(caretakerService.findByKeycloakId(other))
                    .thenThrow(new DomainException(DomainExceptionCode.CARETAKER_NOT_FOUND));

            DomainException exception = assertThrows(DomainException.class,
                    () -> qrCodeServiceFacade.getPage(filter, Pageable.unpaged()));

            assertEquals(DomainExceptionCode.CARETAKER_NOT_FOUND, exception.getCode());
            verifyNoInteractions(qrCodeService);
        }

        @Test
        @DisplayName("Should find no QR codes for a token whose subject is not an ID at all (a service account)")
        void getById_WhenSubjectIsNotAnId_ShouldThrowCaretakerNotFound() {
            when(authenticationProvider.getId()).thenReturn("service-account-mobile");
            when(qrCodeService.findById(qrCodeId)).thenReturn(qrCode);

            DomainException exception = assertThrows(DomainException.class, () -> qrCodeServiceFacade.getById(qrCodeId));

            assertEquals(DomainExceptionCode.CARETAKER_NOT_FOUND, exception.getCode());
            verifyNoInteractions(caretakerService);
            verify(qrCodeMapper, never()).toDTO(any());
        }
    }

    @Nested
    @DisplayName("Get QR code by ID")
    class GetByIdTest {

        @Test
        @DisplayName("Should return the QR code of the caretaker's patient")
        void getById_WhenPatientAssigned_ShouldReturnQRCode() {
            QRCodeDTO dto = mock(QRCodeDTO.class);
            when(qrCodeService.findById(qrCodeId)).thenReturn(qrCode);
            when(patientService.findByIdForCaretaker(patientId, caretakerId)).thenReturn(patient);
            when(qrCodeMapper.toDTO(qrCode)).thenReturn(dto);

            assertEquals(dto, qrCodeServiceFacade.getById(qrCodeId));
        }

        @Test
        @DisplayName("Should throw PATIENT_NOT_FOUND for another caretaker's patient's QR code")
        void getById_WhenPatientNotAssigned_ShouldThrowPatientNotFound() {
            when(qrCodeService.findById(qrCodeId)).thenReturn(qrCode);
            patientIsNotTheCaretakers();

            assertPatientNotFound(assertThrows(DomainException.class, () -> qrCodeServiceFacade.getById(qrCodeId)));
            verify(qrCodeMapper, never()).toDTO(any());
        }

        @Test
        @DisplayName("Should throw QR_CODE_NOT_FOUND without an ID")
        void getById_WithoutId_ShouldThrowQRCodeNotFound() {
            DomainException exception = assertThrows(DomainException.class, () -> qrCodeServiceFacade.getById(null));

            assertEquals(DomainExceptionCode.QR_CODE_NOT_FOUND, exception.getCode());
            verifyNoInteractions(qrCodeService);
        }
    }

    @Nested
    @DisplayName("Get QR code by patient ID")
    class GetByPatientIdTest {

        @Test
        @DisplayName("Should return the QR code of the caretaker's patient")
        void getByPatientId_WhenPatientAssigned_ShouldReturnQRCode() {
            QRCodeDTO dto = mock(QRCodeDTO.class);
            when(patientService.findByIdForCaretaker(patientId, caretakerId)).thenReturn(patient);
            when(qrCodeService.findByPatientId(patientId)).thenReturn(qrCode);
            when(qrCodeMapper.toDTO(qrCode)).thenReturn(dto);

            assertEquals(dto, qrCodeServiceFacade.getByPatientId(patientId));
        }

        @Test
        @DisplayName("Should throw PATIENT_NOT_FOUND for another caretaker's patient, without reading the QR code")
        void getByPatientId_WhenPatientNotAssigned_ShouldThrowPatientNotFound() {
            patientIsNotTheCaretakers();

            assertPatientNotFound(assertThrows(DomainException.class,
                    () -> qrCodeServiceFacade.getByPatientId(patientId)));
            verify(qrCodeService, never()).findByPatientId(any());
        }
    }

    @Nested
    @DisplayName("Create QR code")
    class CreateTest {

        private final String onboardingUrl = "http://mobile/api/v1/onboarding/";

        private void tokenIsGenerated() {
            when(qrCodeUtilityService.generateQrToken()).thenReturn("token");
        }

        @Test
        @DisplayName("Should create the QR code on the authenticated caretaker's behalf, ignoring the request's caretakerId")
        void create_WhenPatientAssigned_ShouldUseAuthenticatedCaretaker() {
            UUID requestCaretakerId = UUID.randomUUID();
            String url = onboardingUrl + patientId;
            QRCodeGenerateDTO request = new QRCodeGenerateDTO(patientId, requestCaretakerId, url);
            CreateQRCodeCommand command = mock(CreateQRCodeCommand.class);
            QRCodeLiteDTO dto = mock(QRCodeLiteDTO.class);
            when(patientService.findByIdForCaretaker(patientId, caretakerId)).thenReturn(patient);
            when(qrCodeService.findByPatientId(patientId))
                    .thenThrow(new DomainException(DomainExceptionCode.QR_CODE_NOT_FOUND));
            when(qrCodeUtilityService.generateQrToken()).thenReturn("token");
            when(qrCodeImageService.generateQRCodeImage(patientId, url, "token", QRCodeGenerateType.IMAGE)).thenReturn("image");
            when(qrCodeMapper.toCreateCommand(eq("token"), any(), eq("image"), eq(patient), eq(caretaker), eq(url)))
                    .thenReturn(command);
            when(qrCodeService.create(command)).thenReturn(qrCode);
            when(qrCodeMapper.toLiteDTO(qrCode)).thenReturn(dto);

            assertEquals(dto, qrCodeServiceFacade.create(request));
            verify(caretakerService, never()).findById(any());
        }

        @Test
        @DisplayName("Should throw PATIENT_NOT_FOUND for another caretaker's patient and create nothing")
        void create_WhenPatientNotAssigned_ShouldThrowPatientNotFound() {
            QRCodeGenerateDTO request = new QRCodeGenerateDTO(patientId, caretakerId, "http://mobile/onboarding");
            patientIsNotTheCaretakers();

            assertPatientNotFound(assertThrows(DomainException.class, () -> qrCodeServiceFacade.create(request)));
            verify(qrCodeService, never()).create(any());
        }

        @Test
        @DisplayName("Should throw PATIENT_ID_NOT_PROVIDED without a patient and create nothing")
        void create_WithoutPatient_ShouldThrow() {
            QRCodeGenerateDTO request = new QRCodeGenerateDTO(null, caretakerId, onboardingUrl);

            DomainException exception = assertThrows(DomainException.class, () -> qrCodeServiceFacade.create(request));

            assertEquals(DomainExceptionCode.PATIENT_ID_NOT_PROVIDED, exception.getCode());
            verify(qrCodeService, never()).create(any());
        }

        @Test
        @DisplayName("Should not create a second QR code while the first is active and has not expired")
        void create_WhenAValidQRCodeExists_ShouldThrowQRCodeFound() {
            QRCode existing = mock(QRCode.class);
            when(existing.getStatus()).thenReturn(QRCodeStatus.ACTIVE);
            when(existing.getExpiresAt()).thenReturn(OffsetDateTime.now().plusDays(1));
            when(patientService.findByIdForCaretaker(patientId, caretakerId)).thenReturn(patient);
            when(qrCodeService.findByPatientId(patientId)).thenReturn(existing);

            DomainException exception = assertThrows(DomainException.class,
                    () -> qrCodeServiceFacade.create(new QRCodeGenerateDTO(patientId, null, onboardingUrl)));

            assertEquals(DomainExceptionCode.QR_CODE_FOUND, exception.getCode());
            verify(qrCodeService, never()).create(any());
        }

        @Test
        @DisplayName("Should create a new QR code once the earlier one has expired or has been used")
        void create_WhenTheEarlierQRCodeIsNoLongerValid_ShouldCreateANewOne() {
            QRCode expired = mock(QRCode.class);
            when(expired.getStatus()).thenReturn(QRCodeStatus.ACTIVE, QRCodeStatus.USED);
            lenient().when(expired.getExpiresAt()).thenReturn(OffsetDateTime.now().minusDays(1), OffsetDateTime.now().plusDays(1));
            when(patientService.findByIdForCaretaker(patientId, caretakerId)).thenReturn(patient);
            when(qrCodeService.findByPatientId(patientId)).thenReturn(expired);
            tokenIsGenerated();
            when(qrCodeImageService.generateQRCodeImage(any(), any(), any(), any())).thenReturn("image");
            CreateQRCodeCommand command = mock(CreateQRCodeCommand.class);
            when(qrCodeMapper.toCreateCommand(any(), any(), any(), any(), any(), any())).thenReturn(command);
            when(qrCodeService.create(command)).thenReturn(qrCode);

            qrCodeServiceFacade.create(new QRCodeGenerateDTO(patientId, null, onboardingUrl));
            qrCodeServiceFacade.create(new QRCodeGenerateDTO(patientId, null, onboardingUrl));

            verify(qrCodeService, times(2)).create(command);
        }
    }

    @Nested
    @DisplayName("Send QR code by email")
    class SendEmailTest {

        @Test
        @DisplayName("Should throw PATIENT_NOT_FOUND for another caretaker's patient's QR code and send nothing")
        void sendQRCodeByEmailAsync_WhenPatientNotAssigned_ShouldThrowPatientNotFound() {
            QRCodeSendEmailDTO request = new QRCodeSendEmailDTO(qrCodeId, "eEarly");
            when(qrCodeService.findById(qrCodeId)).thenReturn(qrCode);
            patientIsNotTheCaretakers();

            assertPatientNotFound(assertThrows(DomainException.class,
                    () -> qrCodeServiceFacade.sendQRCodeByEmailAsync(request)));
            verify(asyncEmailService, never()).sendQRCodeEmailAsync(any());
        }

        @Test
        @DisplayName("Should send to the patient's own address, in the patient's language, with the caretaker as sender")
        void sendQRCodeByEmailAsync_ShouldUseTheRecordsOfThePatientAndCaretaker() {
            when(qrCodeService.findById(qrCodeId)).thenReturn(qrCode);
            when(patientService.findByIdForCaretaker(patientId, caretakerId)).thenReturn(patient);
            when(qrCode.getCaretaker()).thenReturn(caretaker);
            when(qrCode.getImageUrl()).thenReturn("data:image/png;base64,AAAA");
            when(patient.getEmail()).thenReturn("test.patient@example.com");
            when(patient.getFirstName()).thenReturn("Test");
            when(patient.getLastName()).thenReturn("Patient");
            when(patient.getLanguage()).thenReturn("en");
            when(caretaker.getFirstName()).thenReturn("Test");
            when(caretaker.getLastName()).thenReturn("Caretaker");
            when(caretaker.getEmail()).thenReturn("test.caretaker@example.org");

            qrCodeServiceFacade.sendQRCodeByEmailAsync(new QRCodeSendEmailDTO(qrCodeId, "Care App"));

            ArgumentCaptor<SendQRCodeEmailCommand> captor = ArgumentCaptor.forClass(SendQRCodeEmailCommand.class);
            verify(asyncEmailService).sendQRCodeEmailAsync(captor.capture());
            SendQRCodeEmailCommand command = captor.getValue();
            assertEquals("test.patient@example.com", command.getPatientEmail());
            assertEquals("Test Patient", command.getPatientName());
            assertEquals("Test Caretaker", command.getCaretakerName());
            assertEquals("test.caretaker@example.org", command.getCaretakerEmail());
            assertEquals("data:image/png;base64,AAAA", command.getQrCodeImageBase64());
            assertEquals("en", command.getLanguage());
            assertEquals("Care App", command.getAppName());
        }

        @Test
        @DisplayName("Should strip line breaks and other control characters from the application name")
        void sendQRCodeByEmailAsync_ShouldCleanTheApplicationName() {
            when(qrCodeService.findById(qrCodeId)).thenReturn(qrCode);
            when(patientService.findByIdForCaretaker(patientId, caretakerId)).thenReturn(patient);
            when(qrCode.getCaretaker()).thenReturn(caretaker);

            qrCodeServiceFacade.sendQRCodeByEmailAsync(
                    new QRCodeSendEmailDTO(qrCodeId, "Care App\r\nBcc: someone@example.net"));
            qrCodeServiceFacade.sendQRCodeByEmailAsync(new QRCodeSendEmailDTO(qrCodeId, null));

            ArgumentCaptor<SendQRCodeEmailCommand> captor = ArgumentCaptor.forClass(SendQRCodeEmailCommand.class);
            verify(asyncEmailService, times(2)).sendQRCodeEmailAsync(captor.capture());
            assertEquals("Care AppBcc: someone@example.net", captor.getAllValues().get(0).getAppName());
            assertNull(captor.getAllValues().get(1).getAppName());
        }

        @Test
        @DisplayName("Should throw QR_CODE_NOT_FOUND without a QR code ID and send nothing")
        void sendQRCodeByEmailAsync_WithoutQRCode_ShouldThrow() {
            DomainException exception = assertThrows(DomainException.class,
                    () -> qrCodeServiceFacade.sendQRCodeByEmailAsync(new QRCodeSendEmailDTO(null, "eEarly")));

            assertEquals(DomainExceptionCode.QR_CODE_NOT_FOUND, exception.getCode());
            verifyNoInteractions(asyncEmailService);
        }
    }
}
