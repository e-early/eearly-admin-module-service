package si.result.project.eearly.model.caretaker.command;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.UUID;

@Getter
@EqualsAndHashCode
@RequiredArgsConstructor
public class CreateCaretakerCommand {

    private final UUID keycloakId;

    private final String firstName;

    private final String lastName;

    private final String email;

    private final String language;

}
