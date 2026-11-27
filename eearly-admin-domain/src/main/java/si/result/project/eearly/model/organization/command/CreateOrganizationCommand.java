package si.result.project.eearly.model.organization.command;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import si.result.project.eearly.model.department.Department;

import java.util.List;

@Getter
@EqualsAndHashCode
@RequiredArgsConstructor
public class CreateOrganizationCommand {

    private final String name;

    /**
     * Carried for API compatibility only: departments belong to an organization through their own
     * {@code id_organization}, so the organization does not apply this list when it is saved.
     */
    private final List<Department> departments;

}
