package si.result.project.eearly.model.caretaker.command;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import si.result.project.eearly.model.department.Department;
import si.result.project.eearly.model.organization.Organization;

import java.util.UUID;

@Getter
@EqualsAndHashCode(callSuper = true)
public class UpdateCaretakerCommand extends CreateCaretakerCommand {

    private final UUID id;

    @SuppressWarnings("squid:S00107")
    public UpdateCaretakerCommand(UUID id, UUID keycloakId, String firstName, String lastName, String email,
                                  String language, Organization organization, Department department) {
        super(keycloakId, firstName, lastName, email, language, organization, department);
        this.id = id;
    }

}
