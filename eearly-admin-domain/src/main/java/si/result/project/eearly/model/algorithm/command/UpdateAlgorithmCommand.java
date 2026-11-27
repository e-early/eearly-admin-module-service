package si.result.project.eearly.model.algorithm.command;

import si.result.project.eearly.model.algorithm.AlgorithmCategoryType;
import si.result.project.eearly.model.algorithm.AlgorithmPricingModelType;
import si.result.project.eearly.model.algorithm.AlgorithmStatusType;
import si.result.project.eearly.model.healthcondition.HealthCondition;
import si.result.project.eearly.model.measurement.Measurement;
import si.result.project.eearly.model.patient.Patient;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.List;
import java.util.UUID;

public record UpdateAlgorithmCommand(
        UUID id,
        String name,
        String description,
        String algorithmVersion,
        AlgorithmCategoryType category,
        AlgorithmStatusType status,
        String containerName,
        String containerVersion,
        String serviceEndpoint,
        String runEndpoint,
        String cancelEndpoint,
        String healthCheckEndpoint,
        String runnerConfig,
        String inputSchema,
        String outputSchema,
        String author,
        List<Patient> patients,
        List<Measurement> measurements,
        List<HealthCondition> healthConditions,
        AlgorithmPricingModelType pricingModel,
        BigDecimal costPerExecution,
        Currency currency,
        String billingCode,
        String licenseType,
        String authUsername,
        /** As submitted by the caller, in clear text; the service replaces it with the encrypted value before it is stored. */
        String authPassword
) {

    /** The same command with the credentials to store (the username and the encrypted password). */
    public UpdateAlgorithmCommand withCredentials(final String username, final String storedPassword) {
        return new UpdateAlgorithmCommand(id, name, description, algorithmVersion, category, status, containerName,
                containerVersion, serviceEndpoint, runEndpoint, cancelEndpoint, healthCheckEndpoint, runnerConfig,
                inputSchema, outputSchema, author, patients, measurements, healthConditions, pricingModel,
                costPerExecution, currency, billingCode, licenseType, username, storedPassword);
    }

    /** Without the password, which must never reach a log. */
    @Override
    public String toString() {
        return "UpdateAlgorithmCommand[id=" + id + ", name=" + name + ", algorithmVersion=" + algorithmVersion
                + ", serviceEndpoint=" + serviceEndpoint + ", authUsername=" + authUsername + ", authPassword=***]";
    }
}
