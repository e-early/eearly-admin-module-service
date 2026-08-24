package si.result.project.eearly.model.caretaker.command;

import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.util.UUID;

@Getter
@EqualsAndHashCode(callSuper = true)
public class UpdateCaretakerCommand extends CreateCaretakerCommand {

    private final UUID id;

    public UpdateCaretakerCommand(UUID id, UUID keycloakId, String firstName, String lastName, String email,
                                  String language) {
        super(keycloakId, firstName, lastName, email, language);
        this.id = id;
    }

}
