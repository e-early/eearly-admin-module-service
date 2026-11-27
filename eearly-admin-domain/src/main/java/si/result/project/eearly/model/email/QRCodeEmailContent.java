package si.result.project.eearly.model.email;

/**
 * Values of the QR code message template. Holds personal data: never log it.
 *
 * @param patientName    name of the recipient
 * @param caretakerName  name of the healthcare provider who set the monitoring up
 * @param appName        application name
 * @param qrCodeImage    image source of the QR code, normally {@code cid:qrcode} (an inline part of the message)
 * @param appDownloadUrl optional http(s) link to the mobile application; blank or any other scheme omits it
 * @param withLogo       whether the message carries the inline logo ({@code cid:logo})
 */
public record QRCodeEmailContent(
        String patientName,
        String caretakerName,
        String appName,
        String qrCodeImage,
        String appDownloadUrl,
        boolean withLogo) {

    @Override
    public String toString() {
        return "QRCodeEmailContent";
    }
}
