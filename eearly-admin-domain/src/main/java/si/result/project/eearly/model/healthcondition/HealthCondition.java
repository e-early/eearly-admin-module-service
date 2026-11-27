package si.result.project.eearly.model.healthcondition;

import com.google.common.base.Objects;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import org.hibernate.annotations.UuidGenerator;
import si.result.project.eearly.exception.DomainExceptionCode;
import si.result.project.eearly.model.algorithm.Algorithm;
import si.result.project.eearly.model.auditable.Auditable;
import si.result.project.eearly.model.healthcondition.command.CreateHealthConditionCommand;
import si.result.project.eearly.model.patient.Patient;
import si.result.spring.boot.bricks.validation.Condition;

import java.util.List;
import java.util.UUID;

@Entity
@Getter
@Table(name = "t_health_condition")
public class HealthCondition extends Auditable {

    @Id
    @UuidGenerator
    @Column(name = "id")
    private UUID id;

    @Column(name = "name")
    private String name;

    /** Inverse side of {@link Patient#getHealthConditions()}. */
    @ManyToMany(fetch = FetchType.LAZY, mappedBy = "healthConditions")
    private List<Patient> patients;

    /** Inverse side of {@link Algorithm#getHealthConditions()}. */
    @ManyToMany(fetch = FetchType.LAZY, mappedBy = "healthConditions")
    private List<Algorithm> algorithms;

    protected HealthCondition() {}

    public static HealthCondition create(final CreateHealthConditionCommand command) {
        final HealthCondition healthCondition = new HealthCondition();
        healthCondition.setName(command.getName());
        return healthCondition;
    }

    private void setName(final String name) {
        Condition.checkNotNull(name, DomainExceptionCode.HEALTH_CONDITION_NAME_NULL);
        this.name = name;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof HealthCondition entity)) {
            return false;
        }
        return Objects.equal(id, entity.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
