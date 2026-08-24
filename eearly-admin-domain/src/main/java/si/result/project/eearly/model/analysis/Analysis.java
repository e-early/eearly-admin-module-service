package si.result.project.eearly.model.analysis;

import com.fasterxml.jackson.core.type.TypeReference;
import com.google.common.base.Objects;
import jakarta.persistence.*;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import org.hibernate.annotations.JdbcType;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.dialect.PostgreSQLEnumJdbcType;
import org.hibernate.type.SqlTypes;
import si.result.project.eearly.exception.DomainExceptionCode;
import si.result.project.eearly.model.algorithm.Algorithm;
import si.result.project.eearly.model.analysis.command.CreateAnalysisCommand;
import si.result.project.eearly.model.analysis.command.UpdateAnalysisCommand;
import si.result.project.eearly.model.auditable.Auditable;
import si.result.project.eearly.model.measurement.Measurement;
import si.result.project.eearly.model.patient.Patient;
import si.result.project.eearly.util.JsonColumnUtils;
import si.result.spring.boot.bricks.validation.Condition;

@Entity
@Getter
@Table(name = "t_analysis")
public class Analysis extends Auditable {

    @Id
    @UuidGenerator
    @Column(name = "id")
    private UUID id;

    @Column(name = "name")
    private String name;

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(name = "state")
    private AnalysisState state;

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(name = "feedback")
    private FeedbackState feedback;

    @Column(name = "detected")
    private Boolean detected;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_patient")
    private Patient patient;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_algorithm")
    private Algorithm algorithm;

    @ManyToMany(fetch = FetchType.LAZY, mappedBy = "analyses")
    private List<Measurement> measurements;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "input_parameters", columnDefinition = "jsonb")
    private String inputParametersJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "chart_display_options", columnDefinition = "jsonb")
    private String chartDisplayOptions;

    protected Analysis() {}

    public static Analysis create(final CreateAnalysisCommand command) {
        final Analysis analysis = new Analysis();
        analysis.setName(command.getName());
        analysis.setPatient(command.getPatient());
        analysis.setState(command.getState());
        analysis.setFeedback(command.getFeedback());
        analysis.setDetected(command.getDetected());
        analysis.setAlgorithm(command.getAlgorithm());
        analysis.setInputParameters(command.getInputParameters());
        analysis.setChartDisplayOptions(command.getChartDisplayOptions());
        return analysis;
    }

    public void update(final UpdateAnalysisCommand command) {
        setFeedback(command.getFeedback());
    }

    public void applyAlgorithmResult(final AnalysisState newState, final Boolean detected) {
        setState(newState);
        setDetected(detected);
    }

    private void setName(final String name) {
        Condition.checkNotNull(name, DomainExceptionCode.ANALYSIS_NAME_NULL);
        this.name = name;
    }

    private void setState(final AnalysisState state) {
        Condition.checkNotNull(state, DomainExceptionCode.ANALYSIS_STATE_NULL);
        this.state = state;
    }

    private void setFeedback(final FeedbackState feedback) {
        this.feedback = feedback;
    }

    private void setDetected(final Boolean detected) {
        this.detected = detected;
    }

    private void setPatient(final Patient patient) {
        Condition.checkNotNull(patient, DomainExceptionCode.PATIENT_NOT_FOUND);
        this.patient = patient;
    }

    private void setAlgorithm(final Algorithm algorithm) {
        Condition.checkNotNull(algorithm, DomainExceptionCode.ALGORITHM_NOT_FOUND);
        this.algorithm = algorithm;
    }

    public List<AnalysisSelection> getInputParameters() {
        return JsonColumnUtils.readList(
                inputParametersJson,
                new TypeReference<>() {
                }
        );
    }

    private void setInputParameters(final List<AnalysisSelection> inputParameters) {
        this.inputParametersJson = JsonColumnUtils.write(inputParameters);
    }

    private void setChartDisplayOptions(final String chartDisplayOptions) {
        this.chartDisplayOptions = chartDisplayOptions;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Analysis analysis)) {
            return false;
        }
        return Objects.equal(id, analysis.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
