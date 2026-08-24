package si.result.project.eearly.model.analysis.command;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import java.util.List;
import si.result.project.eearly.model.algorithm.Algorithm;
import si.result.project.eearly.model.analysis.AnalysisSelection;
import si.result.project.eearly.model.analysis.AnalysisState;
import si.result.project.eearly.model.analysis.FeedbackState;
import si.result.project.eearly.model.patient.Patient;

@Getter
@EqualsAndHashCode
@RequiredArgsConstructor
public class CreateAnalysisCommand {

    private final String name;

    private final AnalysisState state;

    private final FeedbackState feedback;

    private final Boolean detected;

    private final Patient patient;

    private final Algorithm algorithm;

    private final List<AnalysisSelection> inputParameters;

    private final String chartDisplayOptions;

}
