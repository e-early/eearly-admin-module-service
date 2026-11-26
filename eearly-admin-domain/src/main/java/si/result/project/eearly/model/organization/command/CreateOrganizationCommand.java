package si.result.project.eearly.model.organization.command;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@EqualsAndHashCode
@RequiredArgsConstructor
public class CreateOrganizationCommand {

    private final String name;

}
