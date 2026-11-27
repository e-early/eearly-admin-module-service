package si.result.project.eearly.port.qrcode;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.ListJoin;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.jpa.domain.Specification;
import si.result.project.eearly.event.notification.event.PatientSyncedEvent;
import si.result.project.eearly.exception.DomainExceptionCode;
import si.result.project.eearly.model.caretaker.Caretaker;
import si.result.project.eearly.model.caretaker.Caretaker_;
import si.result.project.eearly.model.patient.Patient;
import si.result.project.eearly.model.patient.Patient_;
import si.result.project.eearly.model.qrcode.QRCode;
import si.result.project.eearly.model.qrcode.QRCodeStatus;
import si.result.project.eearly.model.qrcode.QRCode_;
import si.result.project.eearly.model.qrcode.command.ConfigureQRCodeCommand;
import si.result.project.eearly.model.qrcode.command.CreateQRCodeCommand;
import si.result.spring.boot.bricks.exception.DomainException;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class QRCodeServiceTest {

    @Mock
    private QRCodeRepository qrCodeRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private QRCodeService qrCodeService;

    private UUID qrCodeId;

    @BeforeEach
    void setUp() {
        qrCodeId = UUID.randomUUID();
    }

    @Nested
    @DisplayName("Configure QR code")
    class ConfigureTest {

        @Test
        @DisplayName("Should publish PatientSyncedEvent when mobile app is configured")
        void configure_MobileAppConfigured_PublishesPatientSyncedEvent() {
            UUID patientId = UUID.randomUUID();
            UUID caretakerId = UUID.randomUUID();

            Patient patient = mock(Patient.class);
            when(patient.getId()).thenReturn(patientId);
            Caretaker caretaker = mock(Caretaker.class);
            when(caretaker.getId()).thenReturn(caretakerId);

            QRCode qrCode = mock(QRCode.class);
            when(qrCode.getMobileAppConfigured()).thenReturn(true);
            when(qrCode.getPatient()).thenReturn(patient);
            when(qrCode.getCaretaker()).thenReturn(caretaker);

            ConfigureQRCodeCommand command = mock(ConfigureQRCodeCommand.class);
            when(command.getId()).thenReturn(qrCodeId);

            when(qrCodeRepository.findById(qrCodeId)).thenReturn(Optional.of(qrCode));
            when(qrCodeRepository.save(any())).thenReturn(qrCode);

            qrCodeService.configure(command);

            ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
            verify(eventPublisher).publishEvent(captor.capture());
            PatientSyncedEvent event = (PatientSyncedEvent) captor.getValue();
            assertEquals(patientId, event.patientId());
            assertEquals(caretakerId, event.caretakerId());
        }

        @Test
        @DisplayName("Should publish no event when mobile app is not configured")
        void configure_NotMobileAppConfigured_PublishesNoEvent() {
            QRCode qrCode = mock(QRCode.class);
            when(qrCode.getMobileAppConfigured()).thenReturn(false);

            ConfigureQRCodeCommand command = mock(ConfigureQRCodeCommand.class);
            when(command.getId()).thenReturn(qrCodeId);

            when(qrCodeRepository.findById(qrCodeId)).thenReturn(Optional.of(qrCode));
            when(qrCodeRepository.save(any())).thenReturn(qrCode);

            qrCodeService.configure(command);

            verifyNoInteractions(eventPublisher);
        }

        @Test
        @DisplayName("Should throw for an unknown QR code and publish nothing")
        void configure_UnknownQRCode_ThrowsAndPublishesNoEvent() {
            when(qrCodeRepository.findById(qrCodeId)).thenReturn(Optional.empty());

            DomainException exception = assertThrows(DomainException.class,
                    () -> qrCodeService.configure(new ConfigureQRCodeCommand(qrCodeId, true)));

            assertEquals(DomainExceptionCode.QR_CODE_NOT_FOUND, exception.getCode());
            verifyNoInteractions(eventPublisher);
        }

        @Test
        @DisplayName("Should save the QR code as used before the event is published")
        void configure_RealQRCode_IsUsedWhenTheEventIsPublished() {
            Patient patient = mock(Patient.class);
            when(patient.getId()).thenReturn(UUID.randomUUID());
            Caretaker caretaker = mock(Caretaker.class);
            when(caretaker.getId()).thenReturn(UUID.randomUUID());
            QRCode qrCode = QRCode.create(new CreateQRCodeCommand(
                    "token", OffsetDateTime.now().plusDays(1), "image", patient, caretaker, "https://example.org/o/1"));
            when(qrCodeRepository.findById(any())).thenReturn(Optional.of(qrCode));
            when(qrCodeRepository.save(qrCode)).thenReturn(qrCode);

            qrCodeService.configure(new ConfigureQRCodeCommand(qrCodeId, true));

            assertEquals(QRCodeStatus.USED, qrCode.getStatus());
            assertTrue(qrCode.getMobileAppConfigured());
            verify(eventPublisher).publishEvent(any(PatientSyncedEvent.class));
        }
    }

    @Nested
    @DisplayName("Find the QR code of a patient")
    class FindTest {

        @Test
        @DisplayName("Should return the patient's most recent QR code")
        void findByPatientId_ShouldReturnTheLatestQRCode() {
            UUID patientId = UUID.randomUUID();
            QRCode latest = mock(QRCode.class);
            when(qrCodeRepository.findFirstByPatientIdOrderByCreatedAtDesc(patientId)).thenReturn(Optional.of(latest));

            assertSame(latest, qrCodeService.findByPatientId(patientId));
        }

        @Test
        @DisplayName("Should throw QR_CODE_NOT_FOUND for a patient without a QR code")
        void findByPatientId_WhenNone_ShouldThrow() {
            UUID patientId = UUID.randomUUID();
            when(qrCodeRepository.findFirstByPatientIdOrderByCreatedAtDesc(patientId)).thenReturn(Optional.empty());

            DomainException exception = assertThrows(DomainException.class, () -> qrCodeService.findByPatientId(patientId));

            assertEquals(DomainExceptionCode.QR_CODE_NOT_FOUND, exception.getCode());
        }

        @Test
        @DisplayName("Should throw QR_CODE_NOT_FOUND for an unknown ID")
        void findById_WhenUnknown_ShouldThrow() {
            when(qrCodeRepository.findById(qrCodeId)).thenReturn(Optional.empty());

            DomainException exception = assertThrows(DomainException.class, () -> qrCodeService.findById(qrCodeId));

            assertEquals(DomainExceptionCode.QR_CODE_NOT_FOUND, exception.getCode());
        }
    }

    @Nested
    @DisplayName("Logs")
    class LogsTest {

        private static final String TOKEN = "11111111-2222-3333-4444-555555555555-1700000000000";

        @Test
        @DisplayName("A token that is not found is not logged")
        void findByToken_WhenUnknown_ShouldNotLogTheToken(CapturedOutput output) {
            when(qrCodeRepository.findByToken(TOKEN)).thenReturn(Optional.empty());

            DomainException exception = assertThrows(DomainException.class, () -> qrCodeService.findByToken(TOKEN));

            assertEquals(DomainExceptionCode.QR_CODE_NOT_FOUND, exception.getCode());
            assertFalse(output.getAll().contains(TOKEN));
            assertFalse(output.getAll().contains("11111111"));
        }

        @Test
        @DisplayName("Creating, updating and configuring a QR code log no token")
        void createUpdateConfigure_ShouldNotLogTheToken(CapturedOutput output) {
            Patient patient = mock(Patient.class);
            when(patient.getId()).thenReturn(UUID.randomUUID());
            Caretaker caretaker = mock(Caretaker.class);
            when(caretaker.getId()).thenReturn(UUID.randomUUID());
            CreateQRCodeCommand command = new CreateQRCodeCommand(
                    TOKEN, OffsetDateTime.now().plusDays(1), "image-data", patient, caretaker, "https://example.org/o/1");
            when(qrCodeRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
            QRCode created = qrCodeService.create(command);
            when(qrCodeRepository.findById(any())).thenReturn(Optional.of(created));

            qrCodeService.configure(new ConfigureQRCodeCommand(qrCodeId, true));

            assertFalse(output.getAll().contains(TOKEN));
            assertFalse(output.getAll().contains("image-data"));
            assertFalse(command.toString().contains(TOKEN));
        }
    }

    @Nested
    @DisplayName("Caretaker scoping")
    class CaretakerScopingTest {

        @Test
        @DisplayName("byCaretakerId should join QRCode.patient.caretakers and filter by the caretaker's id")
        void byCaretakerId_ShouldJoinPatientCaretakersAndFilterByCaretakerId() {
            UUID caretakerId = UUID.randomUUID();
            @SuppressWarnings("unchecked")
            Root<QRCode> root = mock(Root.class);
            CriteriaQuery<?> query = mock(CriteriaQuery.class);
            CriteriaBuilder cb = mock(CriteriaBuilder.class);
            @SuppressWarnings("unchecked")
            Join<QRCode, Patient> patientJoin = mock(Join.class);
            @SuppressWarnings("unchecked")
            ListJoin<Patient, Caretaker> caretakerJoin = mock(ListJoin.class);
            @SuppressWarnings("unchecked")
            Path<UUID> idPath = mock(Path.class);
            Predicate equalPredicate = mock(Predicate.class);

            when(root.join(QRCode_.patient)).thenReturn(patientJoin);
            when(patientJoin.join(Patient_.caretakers)).thenReturn(caretakerJoin);
            when(caretakerJoin.get(Caretaker_.id)).thenReturn(idPath);
            when(cb.equal(idPath, caretakerId)).thenReturn(equalPredicate);

            Specification<QRCode> specification = qrCodeService.byCaretakerId(caretakerId);
            Predicate result = specification.toPredicate(root, query, cb);

            assertEquals(equalPredicate, result);
            verify(query).distinct(true);
            verify(root).join(QRCode_.patient);
            verify(patientJoin).join(Patient_.caretakers);
            verify(cb).equal(idPath, caretakerId);
        }
    }
}
