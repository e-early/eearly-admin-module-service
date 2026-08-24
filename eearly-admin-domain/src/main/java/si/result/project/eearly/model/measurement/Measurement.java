package si.result.project.eearly.model.measurement;

import com.google.common.base.Objects;
import jakarta.persistence.*;
import lombok.Getter;
import org.hibernate.annotations.JdbcType;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.dialect.PostgreSQLEnumJdbcType;
import si.result.project.eearly.exception.DomainExceptionCode;
import si.result.project.eearly.model.algorithm.Algorithm;
import si.result.project.eearly.model.analysis.Analysis;
import si.result.project.eearly.model.auditable.Auditable;
import si.result.project.eearly.model.measurement.command.CreateMeasurementCommand;
import si.result.project.eearly.model.measurement.command.UpdateMeasurementCommand;
import si.result.project.eearly.model.patient.Patient;
import si.result.spring.boot.bricks.validation.Condition;

import java.util.List;
import java.util.UUID;

@Entity
@Getter
@Table(name = "t_measurement")
public class Measurement extends Auditable {

    @Id
    @UuidGenerator
    @Column(name = "id")
    private UUID id;

    @Column(name = "name")
    private String name;

    @Column(name = "ehr_observation_id")
    private String ehrObservationId;

    @Column(name = "type")
    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    private MeasurementType type;

    @Column(name = "chart_type")
    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    private MeasurementChartType chartType;

    @ManyToMany(fetch = FetchType.LAZY, mappedBy = "measurements")
    private List<Patient> patients;

    @ManyToMany(fetch = FetchType.LAZY, mappedBy = "measurements")
    private List<Algorithm> algorithms;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "t_measurement_analysis",
            joinColumns = @JoinColumn(name = "measurement_id"),
            inverseJoinColumns = @JoinColumn(name = "analysis_id")
    )
    private List<Analysis> analyses;

    protected Measurement() {}

    public static Measurement create(final CreateMeasurementCommand command) {
        final Measurement measurement = new Measurement();
        measurement.setName(command.getName());
        measurement.setType(command.getType());
        measurement.setChart(command.getChartType());
        measurement.setEhrObservationId(command.getEhrObservationId());
        return measurement;
    }

    public void update(final UpdateMeasurementCommand command) {
        setName(command.getName());
        setChart(command.getChartType());
    }

    private void setName(final String name) {
        Condition.checkNotNull(name, DomainExceptionCode.MEASUREMENT_NAME_NULL);
        this.name = name;
    }

    private void setType(final MeasurementType type) {
        Condition.checkNotNull(type, DomainExceptionCode.MEASUREMENT_TYPE_NULL);
        this.type = type;
    }

    private void setChart(final MeasurementChartType chartType) {
        Condition.checkNotNull(chartType, DomainExceptionCode.MEASUREMENT_CHART_TYPE_NULL);
        this.chartType = chartType;
    }

    private void setEhrObservationId(final String ehrObservationId) {
        Condition.checkNotNull(ehrObservationId, DomainExceptionCode.MEASUREMENT_EHR_OBSERVATION_ID_NULL);
        this.ehrObservationId = ehrObservationId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Measurement entity)) {
            return false;
        }
        return Objects.equal(id, entity.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
