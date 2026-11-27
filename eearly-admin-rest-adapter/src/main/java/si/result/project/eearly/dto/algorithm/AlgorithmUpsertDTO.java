package si.result.project.eearly.dto.algorithm;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.annotation.JsonProperty;
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
    List<UUID> measurementList,

    @Schema
    List<UUID> healthConditionList
    ,

    // --- Credentials for the algorithm's own service endpoint ---
    @Schema(example = "sample-user-42",
            description = "Optional Basic Auth username for this algorithm's service endpoint. Omit it to leave it "
                    + "unchanged, send an empty string to remove the credentials.")
    String authUsername,

    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @Schema(accessMode = Schema.AccessMode.WRITE_ONLY,
            description = "Basic Auth password for this algorithm's service endpoint. Write-only: it is stored "
                    + "encrypted and never returned. Omit it to leave it unchanged, send an empty string to remove "
                    + "it. Needs the server's algorithm.credentials.encryption-key.")
    String authPassword,

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @Schema(accessMode = Schema.AccessMode.READ_ONLY, description = "Whether a password is stored.")
    // Not named has...: MapStruct would take a hasXxx() accessor for a presence check of the xxx property.
    Boolean authPasswordSet
) {

    /** Without the password, which must never reach a log. */
    @Override
    public String toString() {
        return "AlgorithmUpsertDTO[id=" + id + ", name=" + name + ", authUsername=" + authUsername
                + ", authPassword=" + (authPassword == null ? "null" : "***") + "]";
    }
}
