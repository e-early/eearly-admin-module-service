package si.result.project.eearly.model.organization.command;

import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.util.UUID;

@Getter
@EqualsAndHashCode(callSuper = true)
public class UpdateOrganizationCommand extends CreateOrganizationCommand {

    private final UUID id;

    public UpdateOrganizationCommand(UUID id, String name) {
        super(name);
        this.id = id;
    }

}
