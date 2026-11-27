package si.result.project.eearly.model.healthcondition.command;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@EqualsAndHashCode
@RequiredArgsConstructor
public class CreateHealthConditionCommand {

    private final String name;

}
