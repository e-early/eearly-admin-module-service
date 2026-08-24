package si.result.project.eearly.model.algorithm.command;

import si.result.project.eearly.model.algorithm.AlgorithmCategoryType;
import si.result.project.eearly.model.algorithm.AlgorithmPricingModelType;
import si.result.project.eearly.model.algorithm.AlgorithmStatusType;
import si.result.project.eearly.model.measurement.Measurement;
import si.result.project.eearly.model.patient.Patient;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.List;

public record CreateAlgorithmCommand(
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
        AlgorithmPricingModelType pricingModel,
        BigDecimal costPerExecution,
        Currency currency,
        String billingCode,
        String licenseType
) {
}
