package si.result.project.eearly.model.analysis.command;

import java.util.UUID;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import si.result.project.eearly.model.analysis.AnalysisState;
import si.result.project.eearly.model.analysis.FeedbackState;
import si.result.project.eearly.model.patient.Patient;

@Getter
@EqualsAndHashCode(callSuper = true)
public class UpdateAnalysisCommand extends CreateAnalysisCommand {

    private final UUID id;

    public UpdateAnalysisCommand(UUID id, String name, AnalysisState state, FeedbackState feedback,
                                 Boolean detected, Patient idPatient) {
        super(name, state, feedback, detected, idPatient, null, null, null);
        this.id = id;
    }
}
