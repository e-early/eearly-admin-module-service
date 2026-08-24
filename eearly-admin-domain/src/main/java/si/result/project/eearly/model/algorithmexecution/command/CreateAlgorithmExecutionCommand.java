package si.result.project.eearly.model.algorithmexecution.command;

import si.result.project.eearly.model.algorithm.Algorithm;
import si.result.project.eearly.model.algorithmexecution.AlgorithmExecutionTriggerType;
import si.result.project.eearly.model.analysis.Analysis;
import si.result.project.eearly.model.patient.Patient;

public record CreateAlgorithmExecutionCommand(
        Algorithm algorithm,

        Patient patient,

        Analysis analysis,

        AlgorithmExecutionTriggerType triggerType,

        String inputParameters

) {

}
