package si.result.project.eearly.model.department.command;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import si.result.project.eearly.model.organization.Organization;

import java.util.UUID;

@Getter
@EqualsAndHashCode(callSuper = true)
public class UpdateDepartmentCommand extends CreateDepartmentCommand {

    private final UUID id;

    public UpdateDepartmentCommand(UUID id, String name, Organization organization) {
        super(name, organization);
        this.id = id;
    }

}
