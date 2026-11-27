package si.result.project.eearly.port.qrcode;

import jakarta.persistence.criteria.Join;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import si.result.project.eearly.event.notification.event.PatientSyncedEvent;
import si.result.project.eearly.exception.DomainExceptionCode;
import si.result.project.eearly.model.caretaker.Caretaker;
import si.result.project.eearly.model.caretaker.Caretaker_;
import si.result.project.eearly.model.patient.Patient;
import si.result.project.eearly.model.patient.Patient_;
import si.result.project.eearly.model.qrcode.QRCode;
import si.result.project.eearly.model.qrcode.QRCode_;
import si.result.project.eearly.model.qrcode.command.ConfigureQRCodeCommand;
import si.result.project.eearly.model.qrcode.command.CreateQRCodeCommand;
import si.result.project.eearly.model.qrcode.command.UpdateQRCodeCommand;
import si.result.spring.boot.bricks.exception.DomainException;

import java.util.UUID;

/**
 * QR codes of the patients. Logs identify a QR code by its id only, never by its token.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QRCodeService {

    private final QRCodeRepository qrCodeRepository;
    private final ApplicationEventPublisher eventPublisher;

    public QRCode findById(UUID id) {
        return qrCodeRepository.findById(id)
                .orElseThrow(() -> {
                    log.error("QR code with ID {} not found", id);
                    return new DomainException(DomainExceptionCode.QR_CODE_NOT_FOUND);
                });
    }

    public QRCode findByToken(String token) {
        return qrCodeRepository.findByToken(token)
                .orElseThrow(() -> {
                    // The token is a credential: it is not logged, not even when it is unknown.
                    log.error("QR code with the given token not found");
                    return new DomainException(DomainExceptionCode.QR_CODE_NOT_FOUND);
                });
    }

    /** The patient's most recent QR code. */
    public QRCode findByPatientId(UUID patientId) {
        return qrCodeRepository.findFirstByPatientIdOrderByCreatedAtDesc(patientId)
                .orElseThrow(() -> {
                    log.error("QR code with Patient ID {} not found", patientId);
                    return new DomainException(DomainExceptionCode.QR_CODE_NOT_FOUND);
                });
    }

    public Page<QRCode> findAll(final Specification<QRCode> specification, final Pageable pageable) {
        return qrCodeRepository.findAll(specification, pageable);
    }

    public Page<QRCode> findAllForCaretaker(
            final Specification<QRCode> specification, final Pageable pageable, final UUID caretakerId) {
        return qrCodeRepository.findAll(byCaretakerId(caretakerId).and(specification), pageable);
    }

    // Package-private so unit tests can assert join/predicate shape without a real EntityManager.
    Specification<QRCode> byCaretakerId(final UUID caretakerId) {
        return (root, query, cb) -> {
            query.distinct(true);
            Join<QRCode, Patient> patientJoin = root.join(QRCode_.patient);
            Join<Patient, Caretaker> caretakerJoin = patientJoin.join(Patient_.caretakers);
            return cb.equal(caretakerJoin.get(Caretaker_.id), caretakerId);
        };
    }

    public QRCode create(CreateQRCodeCommand command) {
        log.info("Creating QR code for patient: {}", command.patient().getId());
        QRCode qrCode = QRCode.create(command);
        log.debug("QR code created with ID: {}", qrCode.getId());
        return qrCodeRepository.save(qrCode);
    }

    public QRCode update(UpdateQRCodeCommand command) {
        log.info("Updating QR code: {}", command.getId());
        final var qrCode = findById(command.getId());
        qrCode.update(command);
        log.debug("QR code updated with ID: {}", qrCode.getId());
        return qrCodeRepository.save(qrCode);
    }

    /**
     * Records that the patient's app has been set up with the QR code (which then counts as used) and, once it has,
     * publishes a {@link PatientSyncedEvent} for the patient's notification.
     */
    @Transactional
    public QRCode configure(ConfigureQRCodeCommand command) {
        log.info("Configuring QR code: {}", command.getId());
        final var qrCode = findById(command.getId());
        qrCode.configure(command);
        final var saved = qrCodeRepository.save(qrCode);
        log.debug("QR code configured with ID: {}", saved.getId());
        if (Boolean.TRUE.equals(saved.getMobileAppConfigured())) {
            eventPublisher.publishEvent(new PatientSyncedEvent(
                    saved.getPatient().getId(),
                    saved.getCaretaker().getId()
            ));
        }
        return saved;
    }
}
