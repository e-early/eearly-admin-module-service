package si.result.project.eearly.model.department;

import com.google.common.base.Objects;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import org.hibernate.annotations.UuidGenerator;
import si.result.project.eearly.exception.DomainExceptionCode;
import si.result.project.eearly.model.auditable.Auditable;
import si.result.project.eearly.model.caretaker.Caretaker;
import si.result.project.eearly.model.department.command.CreateDepartmentCommand;
import si.result.project.eearly.model.department.command.UpdateDepartmentCommand;
import si.result.project.eearly.model.organization.Organization;
import si.result.spring.boot.bricks.validation.Condition;

import java.util.List;
import java.util.UUID;

@Entity
@Getter
@Table(name = "t_department")
public class Department extends Auditable {

    @Id
    @UuidGenerator
    @Column(name = "id")
    private UUID id;

    @Column(name = "name")
    private String name;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_organization")
    private Organization organization;

    /** Inverse side of {@link Caretaker#getDepartment()}; caretakers are managed through their own endpoints. */
    @OneToMany(fetch = FetchType.LAZY, mappedBy = "department")
    private List<Caretaker> caretakers;

    protected Department() {}

    public static Department create(final CreateDepartmentCommand command) {
        final Department department = new Department();
        department.setName(command.getName());
        department.setOrganization(command.getOrganization());
        return department;
    }

    public void update(final UpdateDepartmentCommand command) {
        setName(command.getName());
        setOrganization(command.getOrganization());
    }

    private void setName(final String name) {
        Condition.checkNotNull(name, DomainExceptionCode.DEPARTMENT_NAME_NULL);
        this.name = name;
    }

    private void setOrganization(final Organization organization) {
        Condition.checkNotNull(organization, DomainExceptionCode.DEPARTMENT_ORGANIZATION_NOT_FOUND);
        this.organization = organization;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Department entity)) {
            return false;
        }
        return Objects.equal(id, entity.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
