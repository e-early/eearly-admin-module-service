package si.result.project.eearly.model.organization;

import com.google.common.base.Objects;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import org.hibernate.annotations.UuidGenerator;
import si.result.project.eearly.exception.DomainExceptionCode;
import si.result.project.eearly.model.auditable.Auditable;
import si.result.project.eearly.model.department.Department;
import si.result.project.eearly.model.organization.command.CreateOrganizationCommand;
import si.result.project.eearly.model.organization.command.UpdateOrganizationCommand;
import si.result.spring.boot.bricks.validation.Condition;

import java.util.List;
import java.util.UUID;

@Entity
@Getter
@Table(name = "t_organization")
public class Organization extends Auditable {

    @Id
    @UuidGenerator
    @Column(name = "id")
    private UUID id;

    @Column(name = "name")
    private String name;

    /** Inverse side of {@link Department#getOrganization()}; departments are managed through their own endpoints. */
    @OneToMany(fetch = FetchType.LAZY, mappedBy = "organization")
    private List<Department> departments;

    protected Organization() {}

    public static Organization create(final CreateOrganizationCommand command) {
        final Organization organization = new Organization();
        organization.setName(command.getName());
        return organization;
    }

    public void update(final UpdateOrganizationCommand command) {
        setName(command.getName());
    }

    private void setName(final String name) {
        Condition.checkNotNull(name, DomainExceptionCode.ORGANIZATION_NAME_NULL);
        this.name = name;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Organization entity)) {
            return false;
        }
        return Objects.equal(id, entity.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
