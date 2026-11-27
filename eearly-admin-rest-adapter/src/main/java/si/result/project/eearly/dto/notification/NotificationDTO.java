package si.result.project.eearly.dto.notification;

import io.swagger.v3.oas.annotations.media.Schema;
import si.result.project.eearly.model.notification.NotificationType;

import java.time.OffsetDateTime;
import java.util.UUID;

@Schema
public record NotificationDTO(
        @Schema(example = "52378678-4cad-4f7b-893e-9e129e47824a")
        UUID id,
        @Schema(example = "ERROR")
        NotificationType type,
        @Schema(example = "52378678-4cad-4f7b-893e-9e129e47824a")
        UUID patientId,
        @Schema(example = "52378678-4cad-4f7b-893e-9e129e47824a")
        UUID caretakerId,
        @Schema(example = "52378678-4cad-4f7b-893e-9e129e47824a")
        UUID referenceId,
        @Schema(example = "2021-08-10T10:00:00Z")
        OffsetDateTime createdAt,
        @Schema(example = "THRESHOLD_EXCEEDED:2")
        String message,
        @Schema(example = "false")
        boolean isSeen
) {
}
