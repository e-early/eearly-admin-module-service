package si.result.project.eearly.dto.algorithm;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.swagger.v3.oas.annotations.media.Schema;
import si.result.project.eearly.dto.common.StringOrJsonDeserializer;

import java.util.List;
import java.util.UUID;

@Schema
public record AlgorithmUpsertDTO(
    @Schema(example = "52378678-4cad-4f7b-893e-9e129e47824a")
    UUID id,

    @Schema(example = "Apnea detection AI algorithm")
    String name,

    @Schema(example = "Detects sleep apnea from breathing data")
    String description,

    @Schema(example = "1.0.0")
    String algorithmVersion,

    @Schema(example = "CARDIOVASCULAR")
    String category,

    @Schema(example = "DRAFT")
    String status,

    @Schema(example = "ecg-classifier")
    String containerName,

    @Schema(example = "v1.2.3")
    String containerVersion,

    @Schema(example = "http://algorithm-api-host:3000")
    String serviceEndpoint,

    @Schema(example = "/run")
    String runEndpoint,

    @Schema(example = "/tasks/{taskId}")
    String cancelEndpoint,

    @Schema(example = "/healthz")
    String healthCheckEndpoint,

    @JsonDeserialize(using = StringOrJsonDeserializer.class)
    @Schema(type = "object", example = "{\"runner\":\"gpu\",\"workers\":2}")
    String runnerConfig,

    @JsonDeserialize(using = StringOrJsonDeserializer.class)
    @Schema(example = "{ \"input\": {\"type\":\"number\"}, \"output\": {\"type\":\"boolean\"} }")
    String inputSchema,

    @JsonDeserialize(using = StringOrJsonDeserializer.class)
    @Schema(example = "{ \"apneaDetected\": true }")
    String outputSchema,

    @Schema(example = "ACME Corp")
    String author,

    // --- Billing / Pricing Fields ---
    @Schema(example = "FREE")
    String pricingModel,

    @Schema(example = "0.0")
    Double costPerExecution,

    @Schema(example = "EUR")
    String currency,

    @Schema(example = "BILL-001")
    String billingCode,

    @Schema(example = "Commercial")
    String licenseType,

    @Schema
    List<UUID> patientList,

    @Schema
    List<UUID> measurementList
) {
}
