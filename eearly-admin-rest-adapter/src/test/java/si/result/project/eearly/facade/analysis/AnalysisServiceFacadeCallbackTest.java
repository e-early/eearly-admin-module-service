package si.result.project.eearly.facade.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;
import si.result.project.eearly.dto.analysis.AnalysisDTO;
import si.result.project.eearly.dto.analysis.AnalysisResultCallbackDTO;
import si.result.project.eearly.exception.CallbackTokenRejectedException;
import si.result.project.eearly.exception.CallbackTokenRejectedException.Reason;
import si.result.project.eearly.mapper.analysis.AnalysisMapper;
import si.result.project.eearly.model.algorithmexecution.AlgorithmExecution;
import si.result.project.eearly.model.algorithmexecution.AlgorithmExecutionStatus;
import si.result.project.eearly.model.analysis.Analysis;
import si.result.project.eearly.model.analysis.AnalysisState;
import si.result.project.eearly.model.analysis.FeedbackState;
import si.result.project.eearly.model.patient.Patient;
import si.result.project.eearly.port.algorithmexecution.AlgorithmExecutionService;
import si.result.project.eearly.port.algorithmexecution.CallbackTokenService;
import si.result.project.eearly.port.analysis.AnalysisDetectionBoxService;
import si.result.project.eearly.port.analysis.AnalysisRepository;
import si.result.project.eearly.port.analysis.AnalysisService;

/** The result endpoints: callback token use and completion of the linked execution, in one transaction. */
class AnalysisServiceFacadeCallbackTest {

    private final UUID analysisId = UUID.randomUUID();
    private final UUID executionId = UUID.randomUUID();

    private CallbackTokenService tokens;
    private AlgorithmExecutionService executions;
    private AnalysisRepository repository;
    private AnalysisServiceFacade facade;

    @BeforeEach
    void wire() {
        final Patient patient = mock(Patient.class);
        lenient().when(patient.getId()).thenReturn(UUID.randomUUID());
        final Analysis analysis = mock(Analysis.class);
        lenient().when(analysis.getId()).thenReturn(analysisId);
        lenient().when(analysis.getPatient()).thenReturn(patient);
        lenient().when(analysis.getFeedback()).thenReturn(FeedbackState.NONE);
        repository = mock(AnalysisRepository.class);
        lenient().when(repository.findById(analysisId)).thenReturn(Optional.of(analysis));
        lenient().when(repository.save(any(Analysis.class))).thenAnswer(i -> i.getArgument(0));
        final AnalysisMapper mapper = mock(AnalysisMapper.class);
        lenient().when(mapper.toDTO(any(Analysis.class), any(AnalysisState.class), org.mockito.ArgumentMatchers.anyList()))
                .thenReturn(mock(AnalysisDTO.class));
        tokens = mock(CallbackTokenService.class);
        executions = mock(AlgorithmExecutionService.class);
        facade = new AnalysisServiceFacade(
                new AnalysisService(repository, mock(ApplicationEventPublisher.class)),
                null, mapper, null, mock(AnalysisDetectionBoxService.class), executions, tokens);
    }

    private AnalysisResultCallbackDTO callback(final AnalysisState state) {
        return new AnalysisResultCallbackDTO(UUID.randomUUID(), analysisId, state, true, "model", List.of());
    }

    private void activeExecution() {
        final AlgorithmExecution execution = AlgorithmExecution.builder().id(executionId)
                .executionStatus(AlgorithmExecutionStatus.RUNNING).build();
        when(executions.findLatestActiveByAnalysisId(analysisId)).thenReturn(Optional.of(execution));
    }

    @Test
    @DisplayName("A callback token is verified against the analysis and used up before the result is processed")
    void tokenIsConsumedFirst() {
        activeExecution();

        facade.updateResult(analysisId, callback(AnalysisState.COMPLETED), "the-token");

        final InOrder order = inOrder(tokens, repository, executions);
        order.verify(tokens).verifyAndConsume("the-token", analysisId);
        order.verify(repository, org.mockito.Mockito.atLeastOnce()).findById(analysisId);
        order.verify(executions).completeFromCallback(eq(executionId), anyString());
    }

    @Test
    @DisplayName("A request authenticated by a JWT carries no token and is not checked against one")
    void jwtRequest_hasNoTokenCheck() {
        activeExecution();

        facade.updateResult(analysisId, callback(AnalysisState.COMPLETED), null);

        verifyNoInteractions(tokens);
        verify(executions).completeFromCallback(eq(executionId), anyString());
    }

    @Test
    @DisplayName("A refused token stops everything: nothing is saved and no execution is touched")
    void refusedToken_doesNothing() {
        doThrow(new CallbackTokenRejectedException(Reason.ALREADY_USED)).when(tokens)
                .verifyAndConsume(anyString(), any());

        assertThatThrownBy(() -> facade.updateResult(analysisId, callback(AnalysisState.COMPLETED), "used"))
                .isInstanceOf(CallbackTokenRejectedException.class);

        verify(repository, never()).save(any(Analysis.class));
        verifyNoInteractions(executions);
    }

    @Test
    @DisplayName("A result in the ERROR state fails the linked execution")
    void errorResult_failsTheExecution() {
        activeExecution();

        facade.updateResult(analysisId, callback(AnalysisState.ERROR), "t");

        verify(executions).failFromCallback(executionId, "Callback reported ERROR state");
        verify(executions, never()).completeFromCallback(any(), any());
    }

    @Test
    void withoutAnActiveExecution_onlyTheAnalysisIsUpdated() {
        when(executions.findLatestActiveByAnalysisId(analysisId)).thenReturn(Optional.empty());

        facade.updateResult(analysisId, callback(AnalysisState.COMPLETED), "t");

        verify(executions, never()).completeFromCallback(any(), any());
        verify(executions, never()).failFromCallback(any(), any());
    }

    @Test
    @DisplayName("The internal two-argument path (simulation) uses no token and completes no execution")
    void internalPath_bypassesTokenAndCompletion() {
        facade.updateResult(analysisId, callback(AnalysisState.COMPLETED));

        verifyNoInteractions(tokens);
        verifyNoInteractions(executions);
    }

    @Test
    @DisplayName("Both the entry points are transactional, so the token use and the result commit together")
    void entryPointsAreTransactional() throws NoSuchMethodException {
        final Method external = AnalysisServiceFacade.class.getMethod(
                "updateResult", UUID.class, AnalysisResultCallbackDTO.class, String.class);
        final Method internal = AnalysisServiceFacade.class.getMethod(
                "updateResult", UUID.class, AnalysisResultCallbackDTO.class);

        assertThat(external.getAnnotation(Transactional.class)).isNotNull();
        assertThat(external.getAnnotation(Transactional.class).readOnly()).isFalse();
        assertThat(internal.getAnnotation(Transactional.class)).isNotNull();
    }
}
