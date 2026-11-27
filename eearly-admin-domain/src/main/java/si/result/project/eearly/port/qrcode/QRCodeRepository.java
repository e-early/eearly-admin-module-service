package si.result.project.eearly.port.qrcode;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;
import si.result.project.eearly.model.qrcode.QRCode;
import si.result.project.eearly.model.qrcode.QRCodeStatus;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface QRCodeRepository {

    Optional<QRCode> findById(UUID id);

    Optional<QRCode> findByToken(String token);

    /**
     * The patient's most recently created QR code. A patient can have several over time (a new one is issued once
     * the earlier one has expired or has been used), so this is the latest, never an error for more than one.
     */
    Optional<QRCode> findFirstByPatientIdOrderByCreatedAtDesc(UUID patientId);

    List<QRCode> findByStatusAndExpiresAtBefore(QRCodeStatus status, OffsetDateTime dateTime);

    /** The patient's QR codes in the given status. */
    List<QRCode> findByPatientIdAndStatus(UUID patientId, QRCodeStatus status);

    Page<QRCode> findAll(Specification<QRCode> specification, Pageable pageable);

    QRCode save(QRCode qrCode);

}
