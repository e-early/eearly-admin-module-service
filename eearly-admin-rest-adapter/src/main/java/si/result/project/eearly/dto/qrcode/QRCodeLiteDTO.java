package si.result.project.eearly.dto.qrcode;

import io.swagger.v3.oas.annotations.media.Schema;
import si.result.project.eearly.model.qrcode.QRCodeStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

@Schema
public record QRCodeLiteDTO (

    @Schema(example = "52378678-4cad-4f7b-893e-9e129e47824a")
    UUID id,

    @Schema(example = "abc123-xyz789-def456")
    String token,

    @Schema(example = "2025-08-30T10:30:00+02:00")
    OffsetDateTime expiresAt,

    @Schema(example = "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAA...")
    String imageUrl,

    @Schema(example = "false")
    Boolean mobileAppConfigured,

    @Schema(example = "ACTIVE")
    QRCodeStatus status,

    @Schema(example = "52378678-4cad-4f7b-893e-9e129e47824a")
    UUID patientId

)
{

    /** The token is a credential: it is left out, so that logging a QR code never shows it. */
    @Override
    public String toString() {
        return "QRCodeLiteDTO[id=" + id + "]";
    }
}
