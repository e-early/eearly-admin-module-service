package si.result.project.eearly.dto.notification;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema
public record UnreadNotificationsDTO(
        @Schema(example = "5")
        long count
) {
}
