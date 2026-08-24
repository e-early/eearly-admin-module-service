package si.result.project.eearly.model.algorithm;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcType;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.dialect.PostgreSQLEnumJdbcType;
import org.hibernate.type.SqlTypes;
import org.springframework.web.util.UriComponentsBuilder;
import si.result.project.eearly.exception.DomainExceptionCode;
import si.result.project.eearly.model.algorithm.command.CreateAlgorithmCommand;
import si.result.project.eearly.model.algorithm.command.UpdateAlgorithmCommand;
import si.result.project.eearly.model.auditable.Auditable;
import si.result.project.eearly.model.measurement.Measurement;
import si.result.project.eearly.model.patient.Patient;
import si.result.spring.boot.bricks.exception.DomainException;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.List;
import java.util.UUID;

@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = false)
@Table(name = "t_algorithm")
public class Algorithm extends Auditable {

    @Id
    @UuidGenerator
    @Column(name = "id")
    private UUID id;

    @Column(name = "name")
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "algorithm_version", length = 50)
    private String algorithmVersion;

    @Column(name = "model_threshold")
    private Double modelThreshold;

    @Enumerated(EnumType.STRING)
    @Column(name = "category")
    @JdbcType(PostgreSQLEnumJdbcType.class)
    private AlgorithmCategoryType category;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    @JdbcType(PostgreSQLEnumJdbcType.class)
    private AlgorithmStatusType status = AlgorithmStatusType.DRAFT;

    @Column(name = "container_name")
    private String containerName;

    @Column(name = "container_version", length = 128)
    private String containerVersion;

    @Column(name = "service_endpoint", length = 512)
    private String serviceEndpoint;

    @Column(name = "run_endpoint")
    private String runEndpoint;

    @Column(name = "cancel_endpoint")
    private String cancelEndpoint;

    @Column(name = "health_check_endpoint")
    private String healthCheckEndpoint;

    @Column(name = "runner_config", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String runnerConfig;

    @Column(name = "input_schema")
    @JdbcTypeCode(SqlTypes.JSON)
    private String inputSchema;

    @Column(name = "output_schema")
    @JdbcTypeCode(SqlTypes.JSON)
    private String outputSchema;

    @Column(name = "author")
    private String author;

    // --- Billing fields ---
    @Enumerated(EnumType.STRING)
    @Column(name = "pricing_model")
    @JdbcType(PostgreSQLEnumJdbcType.class)
    private AlgorithmPricingModelType pricingModel;

    @Column(name = "cost_per_execution", precision = 10, scale = 4)
    private BigDecimal costPerExecution;

    @Column(name = "currency", length = 3)
    @JdbcTypeCode(SqlTypes.CHAR)
    private Currency currency = Currency.getInstance("EUR");

    @Column(name = "billing_code", length = 50)
    private String billingCode;

    @Column(name = "license_type", length = 100)
    private String licenseType;

    @ManyToMany(fetch = FetchType.LAZY, mappedBy = "algorithms")
    private List<Patient> patients;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "t_measurement_algorithm",
            joinColumns = @JoinColumn(name = "algorithm_id"),
            inverseJoinColumns = @JoinColumn(name = "measurement_id")
    )
    private List<Measurement> measurements;

    public static Algorithm create(CreateAlgorithmCommand command) {
        return Algorithm.builder()
                .name(command.name())

                .description(command.description())
                .algorithmVersion(command.algorithmVersion())
                .category(command.category())
                .status(command.status())
                .containerName(command.containerName())
                .containerVersion(command.containerVersion())
                .serviceEndpoint(command.serviceEndpoint())
                .runEndpoint(command.runEndpoint())
                .cancelEndpoint(command.cancelEndpoint())
                .healthCheckEndpoint(command.healthCheckEndpoint())
                .runnerConfig(command.runnerConfig())
                .inputSchema(command.inputSchema())
                .outputSchema(command.outputSchema())
                .author(command.author())

                .pricingModel(command.pricingModel())
                .costPerExecution(command.costPerExecution())
                .currency(command.currency())
                .billingCode(command.billingCode())
                .licenseType(command.licenseType())

                .patients(command.patients())
                .measurements(command.measurements())
                .build();
    }

    public void update(final UpdateAlgorithmCommand command) {
        setName(command.name());
        setDescription(command.description());
        setAlgorithmVersion(command.algorithmVersion());
        setCategory(command.category());
        setStatus(command.status());
        setContainerName(command.containerName());
        setContainerVersion(command.containerVersion());
        setServiceEndpoint(command.serviceEndpoint());
        setRunEndpoint(command.runEndpoint());
        setCancelEndpoint(command.cancelEndpoint());
        setHealthCheckEndpoint(command.healthCheckEndpoint());
        setRunnerConfig(command.runnerConfig());
        setInputSchema(command.inputSchema());
        setOutputSchema(command.outputSchema());
        setAuthor(command.author());

        setPricingModel(command.pricingModel());
        setCostPerExecution(command.costPerExecution());
        setCurrency(command.currency());
        setBillingCode(command.billingCode());
        setLicenseType(command.licenseType());

        setPatients(command.patients());
        setMeasurements(command.measurements());
    }

    public String getExecutionUrl() {
        return buildUrl(runEndpoint);
    }

    public String getCancellationUrl(String taskId) {
        return buildUrl(requireEndpoint(cancelEndpoint).replace("{taskId}", taskId));
    }

    public String getHealthUrl() {
        return buildUrl(healthCheckEndpoint);
    }

    public String getResultUrl(final String taskId) {
        return buildUrl("/results/" + taskId);
    }

    private String buildUrl(String path) {
        return UriComponentsBuilder.fromUriString(requireServiceEndpoint())
                .path(requireEndpoint(path))
                .build()
                .toUriString();
    }

    private String requireServiceEndpoint() {
        if (serviceEndpoint == null || serviceEndpoint.isBlank()) {
            throw new DomainException(DomainExceptionCode.ALGORITHM_SERVICE_ENDPOINT_MISSING);
        }
        return serviceEndpoint;
    }

    private String requireEndpoint(String endpoint) {
        if (endpoint == null || endpoint.isBlank()) {
            throw new DomainException(DomainExceptionCode.ALGORITHM_ENDPOINT_PATH_MISSING);
        }
        return endpoint;
    }
}
