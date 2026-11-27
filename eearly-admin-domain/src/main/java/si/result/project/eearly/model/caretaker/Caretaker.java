package si.result.project.eearly.model.caretaker;

import com.google.common.base.Objects;
import jakarta.persistence.*;
import lombok.Getter;
import org.hibernate.annotations.UuidGenerator;
import si.result.project.eearly.exception.DomainExceptionCode;
import si.result.project.eearly.model.auditable.Auditable;
import si.result.project.eearly.model.caretaker.command.CreateCaretakerCommand;
import si.result.project.eearly.model.caretaker.command.UpdateCaretakerCommand;
import si.result.project.eearly.model.department.Department;
import si.result.project.eearly.model.organization.Organization;
import si.result.project.eearly.model.patient.Patient;
import si.result.spring.boot.bricks.validation.Condition;

import java.util.List;
import java.util.UUID;

@Entity
@Getter
@Table(name = "t_caretaker")
public class Caretaker extends Auditable {

    @Id
    @UuidGenerator
    @Column(name = "id")
    private UUID id;

    @Column(name = "keycloak_id")
    private UUID keycloakId;

    @Column(name = "first_name", length = 50)
    private String firstName;

    @Column(name = "last_name", length = 50)
    private String lastName;

    @Column(name = "email", length = 50)
    private String email;

    @Column(name = "language", length = 3)
    private String language;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_organization")
    private Organization organization;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_department")
    private Department department;

    @ManyToMany(fetch = FetchType.LAZY, mappedBy = "caretakers")
    private List<Patient> patients;

    protected Caretaker() {}

    public static Caretaker create(final CreateCaretakerCommand command) {
        final Caretaker caretaker = new Caretaker();
        caretaker.setKeycloakId(command.getKeycloakId());
        caretaker.setFirstName(command.getFirstName());
        caretaker.setLastName(command.getLastName());
        caretaker.setEmail(command.getEmail());
        caretaker.setLanguage(command.getLanguage());
        caretaker.setOrganization(command.getOrganization());
        caretaker.setDepartment(command.getDepartment());
        return caretaker;
    }

    public void update(final UpdateCaretakerCommand command) {
        setFirstName(command.getFirstName());
        setLastName(command.getLastName());
        setEmail(command.getEmail());
        setLanguage(command.getLanguage());
        // The organization is set when the caretaker is created and is not changed here.
        setDepartment(command.getDepartment());
    }

    private void setKeycloakId(final UUID keycloakId) {
        this.keycloakId = keycloakId;
    }

    private void setFirstName(final String firstName) {
        Condition.checkNotNull(firstName, DomainExceptionCode.CARETAKER_FIRSTNAME_NULL);
        Condition.check(firstName.length() <= 50, DomainExceptionCode.CARETAKER_LASTNAME_TOO_LONG);
        this.firstName = firstName;
    }

    private void setLastName(final String lastName) {
        Condition.checkNotNull(lastName, DomainExceptionCode.CARETAKER_LASTNAME_NULL);
        Condition.check(lastName.length() <= 50, DomainExceptionCode.CARETAKER_LASTNAME_TOO_LONG);
        this.lastName = lastName;
    }

    private void setEmail(final String email) {
        Condition.checkNotNull(email, DomainExceptionCode.CARETAKER_EMAIL_NULL);
        this.email = email;
    }

    private void setLanguage(final String language) {
        Condition.checkNotNull(language, DomainExceptionCode.CARETAKER_LANGUAGE_NULL);
        this.language = language;
    }

    private void setOrganization(final Organization organization) {
        Condition.checkNotNull(organization, DomainExceptionCode.CARETAKER_ORGANIZATION_NULL);
        this.organization = organization;
    }

    private void setDepartment(final Department department) {
        Condition.checkNotNull(department, DomainExceptionCode.CARETAKER_DEPARTMENT_NULL);
        this.department = department;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Caretaker entity)) {
            return false;
        }
        return Objects.equal(id, entity.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
