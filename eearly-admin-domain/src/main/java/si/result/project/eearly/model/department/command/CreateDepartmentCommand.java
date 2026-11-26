package si.result.project.eearly.model.department.command;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import si.result.project.eearly.model.organization.Organization;

@Getter
@EqualsAndHashCode
@RequiredArgsConstructor
public class CreateDepartmentCommand {

    private final String name;

    private final Organization organization;

}
