package si.result.project.eearly.model.algorithmexecution;

import com.fasterxml.jackson.core.type.TypeReference;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcType;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.dialect.PostgreSQLEnumJdbcType;
import org.hibernate.type.SqlTypes;
import si.result.project.eearly.exception.DomainExceptionCode;
import si.result.project.eearly.model.algorithm.Algorithm;
import si.result.project.eearly.model.algorithmexecution.command.CreateAlgorithmExecutionCommand;
import si.result.project.eearly.model.auditable.Auditable;
import si.result.project.eearly.model.analysis.Analysis;
import si.result.project.eearly.model.analysis.AnalysisSelection;
import si.result.project.eearly.model.patient.Patient;
import si.result.project.eearly.util.JsonColumnUtils;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import si.result.spring.boot.bricks.exception.DomainException;

@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = false)
@Table(name = "t_algorithm_execution")
public class AlgorithmExecution extends Auditable {

    @Id
    @UuidGenerator
    @Column(name = "id")
    private UUID id;

    // --- Relationships ---
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_algorithm", nullable = false)
    private Algorithm algorithm;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_patient", nullable = false)
    private Patient patient;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_analysis")
    private Analysis analysis;

    // --- Execution fields ---
    @Enumerated(EnumType.STRING)
    @Column(name = "execution_status", nullable = false)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Setter(AccessLevel.NONE)
    private AlgorithmExecutionStatus executionStatus = AlgorithmExecutionStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_type", nullable = false)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    private AlgorithmExecutionTriggerType triggerType;

    @Column(name = "external_request_id", length = 128)
    private String externalRequestId;

    @Column(name = "input_parameters")
    @JdbcTypeCode(SqlTypes.JSON)
    private String inputParameters;

    @Column(name = "result_data")
    @JdbcTypeCode(SqlTypes.JSON)
    private String resultData;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    // --- Lifecycle methods ---
    public void updateStatus(AlgorithmExecutionStatus newStatus) {
        if (!isValidTransition(this.executionStatus, newStatus)) {
            throw new DomainException(
                    DomainExceptionCode.INVALID_EXECUTION_STATUS_TRANSITION,
                    String.format("Cannot transition from %s to %s", this.executionStatus,
                            newStatus)
            );
        }
        this.executionStatus = newStatus;
        if (newStatus == AlgorithmExecutionStatus.RUNNING) {
            this.startedAt = Instant.now();
        }
        if (newStatus == AlgorithmExecutionStatus.COMPLETED
                || newStatus == AlgorithmExecutionStatus.FAILED
                || newStatus == AlgorithmExecutionStatus.CANCELLED) {
            this.completedAt = Instant.now();
        }
    }

    private boolean isValidTransition(AlgorithmExecutionStatus current,
            AlgorithmExecutionStatus next) {
        return switch (current) {
            case PENDING -> next == AlgorithmExecutionStatus.SUBMITTED
                    || next == AlgorithmExecutionStatus.CANCELLED;
            case SUBMITTED -> next == AlgorithmExecutionStatus.RUNNING
                    || next == AlgorithmExecutionStatus.FAILED
                    || next == AlgorithmExecutionStatus.CANCELLED;
            case RUNNING -> next == AlgorithmExecutionStatus.COMPLETED
                    || next == AlgorithmExecutionStatus.FAILED
                    || next == AlgorithmExecutionStatus.CANCELLED;
            default -> false;
        };
    }

    public static AlgorithmExecution create(CreateAlgorithmExecutionCommand command) {
        final String inputParameters = command.inputParameters() != null
                ? command.inputParameters()
                : command.analysis() != null ? command.analysis().getInputParametersJson() : null;

        return AlgorithmExecution.builder()
                .algorithm(command.algorithm())
                .patient(command.patient())
                .analysis(command.analysis())
                .executionStatus(AlgorithmExecutionStatus.PENDING)
                .triggerType(command.triggerType())
                .inputParameters(inputParameters)
                .build();
    }

    public List<AnalysisSelection> getInputSelections() {
        return JsonColumnUtils.readList(
                inputParameters,
                new TypeReference<List<AnalysisSelection>>() {}
        );
    }
}
