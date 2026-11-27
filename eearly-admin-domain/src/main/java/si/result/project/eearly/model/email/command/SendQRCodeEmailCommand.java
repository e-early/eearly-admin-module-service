package si.result.project.eearly.model.email.command;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Sends a patient their QR code. Holds personal data, so it deliberately has no {@code toString}: never log it.
 */
@Getter
@EqualsAndHashCode
@RequiredArgsConstructor
public class SendQRCodeEmailCommand {
    /** Application name shown in the message; null or blank uses {@code email.app-name}. */
    private final String appName;
    private final String patientEmail;
    private final String patientName;
    private final String caretakerName;
    /** Optional; the caretaker becomes the Reply-To of the message. */
    private final String caretakerEmail;
    /** The QR code image as PNG, base64 encoded (a {@code data:image/png;base64,} prefix is accepted). */
    private final String qrCodeImageBase64;
    /** The patient's language (ISO 639-1); null sends the message in the default language. */
    private final String language;
}
