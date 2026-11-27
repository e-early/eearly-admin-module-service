package si.result.project.eearly.port.email;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import si.result.project.eearly.exception.DomainExceptionCode;
import si.result.project.eearly.model.email.QRCodeEmailContent;
import si.result.project.eearly.model.email.command.SendQRCodeEmailCommand;
import si.result.project.eearly.model.patient.PatientLanguage;
import si.result.spring.boot.bricks.exception.DomainException;

import java.io.ByteArrayInputStream;
import java.net.URLConnection;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Sends the e-mails of the service, synchronously.
 *
 * <p>Logs never contain recipients, names, codes or message bodies, only the message type and a random message id;
 * exceptions are logged by their class only, because mail exceptions tend to quote the addresses.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    static final String LOGO_CONTENT_ID = "logo";
    static final String QR_CODE_MESSAGE_TYPE = "QR_CODE";

    private static final String ATTACHMENT_NAME = "qr-code.png";
    private static final String QR_CODE_CONTENT_ID = "qrcode";
    private static final String DATA_URI_PREFIX = "data:image/png;base64,";
    private static final String HTML_SUFFIX = ".html";

    private final JavaMailSender mailSender;
    private final EmailTemplateService emailTemplateService;
    private final ResourceLoader resourceLoader;
    private final EmailProperties properties;

    /**
     * Sends the patient their QR code, in the patient's language. The caretaker, when known, is the Reply-To.
     *
     * @throws DomainException {@code EMAIL_SEND_FAILED} (without a cause, which could quote an address) or a template
     *                         error; a recipient outside {@code email.allowed-recipient-domains} is skipped silently
     */
    public void sendQRCodeToPatient(final SendQRCodeEmailCommand command) {
        final String messageId = UUID.randomUUID().toString();
        if (!isRecipientAllowed(command.getPatientEmail())) {
            log.warn("Email not sent: type={}, id={}, recipient domain is not in email.allowed-recipient-domains",
                    QR_CODE_MESSAGE_TYPE, messageId);
            return;
        }
        try {
            final String appName = isBlank(command.getAppName()) ? properties.appName() : command.getAppName();
            final MimeMessage message = mailSender.createMimeMessage();
            final MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            // Messages always come from the configured sender (relays often accept no other), so the caretaker
            // goes into Reply-To.
            helper.setFrom(properties.from(), appName);
            if (!isBlank(command.getCaretakerEmail())) {
                helper.setReplyTo(command.getCaretakerEmail(), command.getCaretakerName());
            }
            helper.setTo(command.getPatientEmail());
            helper.setSubject(qrCodeSubject(command.getLanguage()));

            byte[] imageBytes = null;
            if (command.getQrCodeImageBase64() != null) {
                imageBytes = Base64.getDecoder().decode(command.getQrCodeImageBase64().replace(DATA_URI_PREFIX, ""));
            }
            final Resource logo = findLogo();

            // Gmail and Outlook block data: URIs in <img>, so the QR code is referenced as an inline part.
            helper.setText(emailTemplateService.generateQRCodeEmail(
                    new QRCodeEmailContent(
                            command.getPatientName(),
                            command.getCaretakerName(),
                            appName,
                            imageBytes != null ? "cid:" + QR_CODE_CONTENT_ID : null,
                            properties.appDownloadUrl(),
                            logo != null),
                    qrCodeTemplate(command.getLanguage())), true);

            if (imageBytes != null) {
                helper.addInline(QR_CODE_CONTENT_ID, new ByteArrayResource(imageBytes), "image/png");
                final byte[] attachmentBytes = imageBytes;
                helper.addAttachment(ATTACHMENT_NAME, () -> new ByteArrayInputStream(attachmentBytes));
            }
            if (logo != null) {
                helper.addInline(LOGO_CONTENT_ID, logo, logoContentType(logo));
            }

            mailSender.send(message);
            log.info("Email sent: type={}, id={}", QR_CODE_MESSAGE_TYPE, messageId);
        } catch (DomainException e) {
            throw e;
        } catch (Exception e) {
            // Deliberately neither logged with its message nor kept as the cause: both can quote the addresses.
            log.error("Email failed: type={}, id={}, reason={}",
                    QR_CODE_MESSAGE_TYPE, messageId, e.getClass().getSimpleName());
            throw new DomainException(DomainExceptionCode.EMAIL_SEND_FAILED);
        }
    }

    /**
     * The configured template (e.g. {@code qr-code-email.html}) is in Slovenian, the default language; an English
     * patient gets its {@code -en} sibling ({@code qr-code-email-en.html}), so a deployment needs one template
     * setting only.
     */
    String qrCodeTemplate(final String language) {
        final String template = properties.qrcode().template();
        if (!PatientLanguage.ENGLISH.equals(language)) {
            return template;
        }
        return template.endsWith(HTML_SUFFIX)
                ? template.substring(0, template.length() - HTML_SUFFIX.length()) + "-en" + HTML_SUFFIX
                : template + "-en";
    }

    static String qrCodeSubject(final String language) {
        return PatientLanguage.ENGLISH.equals(language)
                ? "Your Healthcare Monitoring QR Code"
                : "Vaša QR koda za spremljanje zdravja";
    }

    boolean isRecipientAllowed(final String email) {
        final List<String> domains = Arrays.stream(properties.allowedRecipientDomains().split(","))
                .map(String::trim)
                .map(domain -> domain.toLowerCase(Locale.ROOT))
                .filter(domain -> !domain.isEmpty())
                .toList();
        if (domains.contains("*")) {
            return true;
        }
        final String recipientDomain = email == null ? "" : email.substring(email.lastIndexOf('@') + 1).toLowerCase(Locale.ROOT);
        return domains.contains(recipientDomain);
    }

    /** The configured logo, or null when none is configured or it does not exist (the message then has no logo). */
    private Resource findLogo() {
        final String location = properties.logo();
        if (isBlank(location)) {
            return null;
        }
        final Resource logo = resourceLoader.getResource(location);
        if (!logo.exists()) {
            log.warn("Email logo not found at {}, sending without logo", location);
            return null;
        }
        return logo;
    }

    private static String logoContentType(final Resource logo) {
        final String guessed = logo.getFilename() == null ? null : URLConnection.guessContentTypeFromName(logo.getFilename());
        return guessed != null ? guessed : "image/png";
    }

    private static boolean isBlank(final String value) {
        return value == null || value.isBlank();
    }
}
