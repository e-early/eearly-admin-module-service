package si.result.project.eearly.model.qrcode;

import com.google.common.base.Objects;
import jakarta.persistence.*;
import lombok.Getter;
import org.hibernate.annotations.JdbcType;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.dialect.PostgreSQLEnumJdbcType;
import si.result.project.eearly.exception.DomainExceptionCode;
import si.result.project.eearly.model.auditable.Auditable;
import si.result.project.eearly.model.caretaker.Caretaker;
import si.result.project.eearly.model.patient.Patient;
import si.result.project.eearly.model.qrcode.command.ConfigureQRCodeCommand;
import si.result.project.eearly.model.qrcode.command.CreateQRCodeCommand;
import si.result.project.eearly.model.qrcode.command.UpdateQRCodeCommand;
import si.result.spring.boot.bricks.validation.Condition;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * The code a patient scans to set up the mobile app. The relation is mapped from here to the patient and the
 * caretaker only; neither of them navigates back to its QR codes (use {@code QRCodeRepository}).
 *
 * <p>Holds the code's token and the image that encodes it: never log it.
 */
@Entity
@Getter
@Table(name = "t_qr_code")
public class QRCode extends Auditable {

    @Id
    @UuidGenerator
    @Column(name = "id")
    private UUID id;

    @Column(name = "token", unique = true)
    private String token;

    @Column(name = "expires_at")
    private OffsetDateTime expiresAt;

    @Column(name = "image_url", length = 10000)
    private String imageUrl;

    @Column(name = "mobile_app_configured")
    private Boolean mobileAppConfigured = false;

    @Column(name = "onboarding_url")
    private String onboardingUrl;

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(name = "status")
    private QRCodeStatus status = QRCodeStatus.ACTIVE;

    // Many, not one: a patient gets a new code once the earlier one has expired or has been used.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_patient")
    private Patient patient;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_caretaker")
    private Caretaker caretaker;

    protected QRCode() {}

    public static QRCode create(CreateQRCodeCommand command) {
        QRCode qrCode = new QRCode();
        qrCode.setToken(command.token());
        qrCode.setExpiresAt(command.expiresAt());
        qrCode.setImageUrl(command.imageUrl());
        qrCode.setPatient(command.patient());
        qrCode.setCaretaker(command.caretaker());
        qrCode.setOnboardingUrl(command.onboardingUrl());
        qrCode.setStatus(QRCodeStatus.ACTIVE);
        qrCode.setMobileAppConfigured(false);
        return qrCode;
    }

    public void update(UpdateQRCodeCommand command) {
        setToken(command.getToken());
        setExpiresAt(command.getExpiresAt());
        setImageUrl(command.getImageUrl());
        setStatus(command.getStatus());
        setMobileAppConfigured(command.getMobileAppConfigured());
    }

    public void configure(ConfigureQRCodeCommand command) {
        setMobileAppConfigured(command.isMobileAppConfigured());
        if (command.isMobileAppConfigured()) {
            setStatus(QRCodeStatus.USED);
        }
    }

    /** Withdraws the code: it can no longer be used (its onboarding link has been replaced by a newer one). */
    public void revoke() {
        setStatus(QRCodeStatus.REVOKED);
    }

    private void setToken(String token) {
        Condition.checkNotNull(token, DomainExceptionCode.QR_CODE_TOKEN_NULL);
        this.token = token;
    }

    private void setExpiresAt(OffsetDateTime expiresAt) {
        Condition.checkNotNull(expiresAt, DomainExceptionCode.QR_CODE_TOKEN_EXPIRATION_DATE_NULL);
        this.expiresAt = expiresAt;
    }

    private void setImageUrl(String imageUrl) {
        Condition.checkNotNull(imageUrl, DomainExceptionCode.QR_CODE_URL_NULL);
        this.imageUrl = imageUrl;
    }

    private void setMobileAppConfigured(Boolean mobileAppConfigured) {
        Condition.checkNotNull(mobileAppConfigured, DomainExceptionCode.QR_CODE_MOBILE_APP_CONFIGURED_NULL);
        this.mobileAppConfigured = mobileAppConfigured;
    }

    private void setStatus(QRCodeStatus status) {
        Condition.checkNotNull(status, DomainExceptionCode.QR_CODE_STATUS_NULL);
        this.status = status;
    }

    private void setPatient(Patient patient) {
        Condition.checkNotNull(patient, DomainExceptionCode.QR_CODE_PATIENT_NULL);
        this.patient = patient;
    }

    private void setCaretaker(Caretaker caretaker) {
        Condition.checkNotNull(caretaker, DomainExceptionCode.QR_CODE_CARETAKER_NULL);
        this.caretaker = caretaker;
    }

    private void setOnboardingUrl(String onboardingUrl) {
        Condition.checkNotNull(onboardingUrl, DomainExceptionCode.QR_CODE_ONBOARDING_URL_NULL);
        Condition.checkNotEmpty(onboardingUrl, DomainExceptionCode.QR_CODE_ONBOARDING_URL_BLANK);
        this.onboardingUrl = onboardingUrl;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof QRCode entity)) {
            return false;
        }
        return Objects.equal(id, entity.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
