package si.result.project.eearly.model.organization.command;

import lombok.EqualsAndHashCode;
import lombok.Getter;

import si.result.project.eearly.model.department.Department;

import java.util.List;
import java.util.UUID;

@Getter
@EqualsAndHashCode(callSuper = true)
public class UpdateOrganizationCommand extends CreateOrganizationCommand {

    private final UUID id;

    public UpdateOrganizationCommand(UUID id, String name, List<Department> departments) {
        super(name, departments);
        this.id = id;
    }

}
