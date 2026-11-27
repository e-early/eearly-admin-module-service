package si.result.project.eearly.facade.qrcode;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedModel;
import org.springframework.transaction.annotation.Transactional;
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
import si.result.project.eearly.port.caretaker.CaretakerService;
import si.result.project.eearly.port.email.AsyncEmailService;
import si.result.project.eearly.port.mobile.MobileServiceService;
import si.result.project.eearly.port.patient.PatientService;
import si.result.project.eearly.port.qrcode.QRCodeService;
import si.result.project.eearly.util.qrcode.QRCodeGenerateType;
import si.result.project.eearly.util.qrcode.QRCodeImageService;
import si.result.project.eearly.util.qrcode.QRCodeUtilityService;
import si.result.rest.filter.Filter;
import si.result.spring.boot.bricks.annotation.Facade;
import si.result.spring.boot.bricks.auth.AuthenticationProvider;
import si.result.spring.boot.bricks.exception.DomainException;

import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * QR codes of the patients, for the authenticated caretaker: every operation is limited to the patients assigned to
 * that caretaker (the caller's token subject is the caretaker's Keycloak id). A QR code of any other patient is
 * answered as {@code PATIENT_NOT_FOUND}, as the patient's other data is.
 */
@Facade
@RequiredArgsConstructor
public class QRCodeServiceFacade {

    private final QRCodeService qrCodeService;
    private final PatientService patientService;
    private final QRCodeUtilityService qrCodeUtilityService;
    private final QRCodeImageService qrCodeImageService;
    private final QRCodeMapper qrCodeMapper;
    private final AsyncEmailService asyncEmailService;
    private final CaretakerService caretakerService;
    private final AuthenticationProvider authenticationProvider;
    private final MobileServiceService mobileServiceService;

    /**
     * QR codes of the patients assigned to the authenticated caretaker.
     */
    @Transactional(readOnly = true)
    public PagedModel<QRCodeLiteDTO> getPage(final Filter filter, final Pageable pageable) {
        return new PagedModel<>(
                qrCodeService.findAllForCaretaker(filter.toSpecification(), pageable, getAuthenticatedCaretaker().getId())
                        .map(qrCodeMapper::toLiteDTO));
    }

    @Transactional(readOnly = true)
    public QRCodeDTO getById(final UUID id) {
        return qrCodeMapper.toDTO(findAssignedQRCode(id));
    }

    /** The most recent QR code of a patient assigned to the authenticated caretaker. */
    @Transactional(readOnly = true)
    public QRCodeDTO getByPatientId(final UUID patientId) {
        patientService.findByIdForCaretaker(requirePatientId(patientId), getAuthenticatedCaretaker().getId());
        return qrCodeMapper.toDTO(qrCodeService.findByPatientId(patientId));
    }

    /**
     * Creates the QR code of a patient assigned to the authenticated caretaker, on that caretaker's
     * behalf. Request caretakerId and onboardingUrl are ignored: the code encodes a fresh single-use onboarding link
     * that the mobile service issues for the patient (which invalidates the patient's earlier links), and the
     * patient's earlier active codes are revoked. The patient must have a mobile user already.
     */
    @Transactional
    public QRCodeLiteDTO create(final QRCodeGenerateDTO qrCodeGenerateDTO) {
        Caretaker caretaker = getAuthenticatedCaretaker();
        Patient patient = patientService.findByIdForCaretaker(requirePatientId(qrCodeGenerateDTO.patientId()), caretaker.getId());
        if (patient.getKeycloakId() == null) {
            throw new DomainException(DomainExceptionCode.PATIENT_NOT_ONBOARDED);
        }
        final String onboardingUrl = mobileServiceService.issueOnboardingLink(patient.getKeycloakId());
        qrCodeUtilityService.validateOnboardingUrl(onboardingUrl);
        String token = qrCodeUtilityService.generateQrToken();
        return qrCodeMapper.toLiteDTO(
                qrCodeService.create(
                        qrCodeMapper.toCreateCommand(
                                token,
                                qrCodeUtilityService.generateTokenExpirationTime(),
                                qrCodeImageService.generateQRCodeImage(
                                    qrCodeGenerateDTO.patientId(),
                                    onboardingUrl,
                                    token,
                                    QRCodeGenerateType.IMAGE),
                                patient,
                                caretaker,
                                onboardingUrl
                        )));
    }

    /**
     * E-mails the QR code to its patient, in the patient's language. The recipient is always the e-mail address on
     * the patient's record and the sender name is the optional {@code appName}; neither the address nor anything else
     * of the message can be chosen by the caller. Recipient domains are limited by
     * {@code email.allowed-recipient-domains}.
     * Only an active, unexpired QR code is sent ({@code QR_CODE_NOT_ACTIVE} otherwise): a used, revoked or expired
     * one no longer works.
     */
    @Transactional(readOnly = true)
    public CompletableFuture<Void> sendQRCodeByEmailAsync(QRCodeSendEmailDTO qrCodeSendEmailDTO) {
        QRCode qrCode = findAssignedQRCode(qrCodeSendEmailDTO.qrCodeId());
        if (!isQRCodeValid(qrCode)) {
            throw new DomainException(DomainExceptionCode.QR_CODE_NOT_ACTIVE);
        }
        Patient patient = qrCode.getPatient();
        Caretaker caretaker = qrCode.getCaretaker();

        String patientName = patient.getFirstName() + " " + patient.getLastName();

        String caretakerName = caretaker.getFirstName() + " " + caretaker.getLastName();

        SendQRCodeEmailCommand command = new SendQRCodeEmailCommand(
                withoutControlCharacters(qrCodeSendEmailDTO.appName()),
                patient.getEmail(),
                patientName,
                caretakerName,
                caretaker.getEmail(),
                qrCode.getImageUrl(),
                patient.getLanguage()
        );

        return asyncEmailService.sendQRCodeEmailAsync(command);
    }

    // A QR code is the caretaker's when its patient is assigned to them; otherwise PATIENT_NOT_FOUND
    // (404), as for the patient's other data.
    private QRCode findAssignedQRCode(final UUID qrCodeId) {
        if (qrCodeId == null) {
            throw new DomainException(DomainExceptionCode.QR_CODE_NOT_FOUND);
        }
        final QRCode qrCode = qrCodeService.findById(qrCodeId);
        patientService.findByIdForCaretaker(qrCode.getPatient().getId(), getAuthenticatedCaretaker().getId());
        return qrCode;
    }

    private static UUID requirePatientId(final UUID patientId) {
        if (patientId == null) {
            throw new DomainException(DomainExceptionCode.PATIENT_ID_NOT_PROVIDED);
        }
        return patientId;
    }

    // The caller must be a caretaker: a token whose subject is no caretaker's Keycloak id (an administrator without a
    // caretaker record, a service account) has no QR codes.
    private Caretaker getAuthenticatedCaretaker() {
        final UUID keycloakId;
        try {
            keycloakId = UUID.fromString(authenticationProvider.getId());
        } catch (IllegalArgumentException e) {
            throw new DomainException(DomainExceptionCode.CARETAKER_NOT_FOUND);
        }
        return caretakerService.findByKeycloakId(keycloakId);
    }

    private boolean isQRCodeValid(QRCode qrCode) {
        return qrCode.getStatus() == QRCodeStatus.ACTIVE &&
                qrCode.getExpiresAt() != null &&
                qrCode.getExpiresAt().isAfter(OffsetDateTime.now());
    }

    /** The application name goes into the sender name of the message, so line breaks and the like are removed. */
    private static String withoutControlCharacters(final String value) {
        return value == null ? null : value.replaceAll("\\p{Cntrl}", "").trim();
    }
}
