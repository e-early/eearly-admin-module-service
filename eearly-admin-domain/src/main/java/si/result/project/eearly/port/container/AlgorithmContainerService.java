package si.result.project.eearly.port.container;

import si.result.project.eearly.model.algorithmexecution.AlgorithmExecution;

public interface AlgorithmContainerService {

    void run(AlgorithmExecution algorithmExecution);

    void cancel(AlgorithmExecution algorithmExecution);

    void healthCheck(AlgorithmExecution algorithmExecution);
}
