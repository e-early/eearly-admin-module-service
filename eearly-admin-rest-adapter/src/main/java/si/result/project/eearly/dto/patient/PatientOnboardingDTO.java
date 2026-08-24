package si.result.project.eearly.dto.patient;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema
public record PatientOnboardingDTO(

        @Schema(example = "52378678-4cad-4f7b-893e-9e129e47824a")
        UUID id,

        @Schema(example = "http://localhost:8081/api/v1/onboarding/52378678-4cad-4f7b-893e-9e129e47824a")
        String onboardingUrl
){}
