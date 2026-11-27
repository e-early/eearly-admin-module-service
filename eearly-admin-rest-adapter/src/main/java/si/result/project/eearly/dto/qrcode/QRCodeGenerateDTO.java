package si.result.project.eearly.dto.qrcode;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

/**
 * Request to create the QR code of a patient. The QR code is created on behalf of the authenticated caretaker, who
 * must be assigned to the patient; {@code caretakerId} is accepted for compatibility and ignored.
 */
@Schema
public record QRCodeGenerateDTO (

    @Schema(example = "52378678-4cad-4f7b-893e-9e129e47824a")
    UUID patientId,

    @Schema(example = "52378678-4cad-4f7b-893e-9e129e47824a", description = "Ignored: the authenticated caretaker is used")
    UUID caretakerId,

    @Schema(example = "http://localhost:8081/api/v1/onboarding/52378678-4cad-4f7b-893e-9e129e47824a")
    String onboardingUrl

){}
