package si.result.project.eearly.dto.qrcode;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

/**
 * Request to e-mail a QR code to its patient. The message goes to the e-mail address of the patient the QR code
 * belongs to; the request chooses no recipient.
 */
@Schema
public record QRCodeSendEmailDTO(

        @Schema(example = "52378678-4cad-4f7b-893e-9e129e47824a")
        UUID qrCodeId,

        @Schema(example = "EEARLY", description = "Application name shown in the message; blank uses email.app-name")
        String appName

) {
}
