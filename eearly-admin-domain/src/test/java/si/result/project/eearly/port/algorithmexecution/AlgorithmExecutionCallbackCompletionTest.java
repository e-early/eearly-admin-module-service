package si.result.project.eearly.port.algorithmexecution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import si.result.project.eearly.model.algorithmexecution.AlgorithmExecution;
import si.result.project.eearly.model.algorithmexecution.AlgorithmExecutionStatus;

/** Completing an execution from the result callback, in the transaction that also uses up the token. */
@ExtendWith(MockitoExtension.class)
class AlgorithmExecutionCallbackCompletionTest {

    @Mock
    private AlgorithmExecutionRepository repository;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @InjectMocks
    private AlgorithmExecutionService service;

    private AlgorithmExecution stored(final UUID id, final AlgorithmExecutionStatus status) {
        final AlgorithmExecution execution = AlgorithmExecution.builder().id(id).executionStatus(status).build();
        when(repository.findById(id)).thenReturn(Optional.of(execution));
        org.mockito.Mockito.lenient().when(repository.save(any(AlgorithmExecution.class))).thenAnswer(i -> i.getArgument(0));
        return execution;
    }

    @Test
    void completeFromCallback_completesARunningExecution() {
        final UUID id = UUID.randomUUID();
        final AlgorithmExecution execution = stored(id, AlgorithmExecutionStatus.RUNNING);

        service.completeFromCallback(id, "{\"ok\":true}");

        assertThat(execution.getExecutionStatus()).isEqualTo(AlgorithmExecutionStatus.COMPLETED);
        assertThat(execution.getResultData()).isEqualTo("{\"ok\":true}");
        assertThat(execution.getErrorMessage()).isNull();
        verify(repository).save(execution);
    }

    @Test
    @DisplayName("An execution that has not reached RUNNING yet is advanced first")
    void completeFromCallback_advancesAnEarlierExecution() {
        for (final AlgorithmExecutionStatus early : new AlgorithmExecutionStatus[] {AlgorithmExecutionStatus.PENDING,
                AlgorithmExecutionStatus.SUBMITTED}) {
            final UUID id = UUID.randomUUID();
            final AlgorithmExecution execution = stored(id, early);

            service.completeFromCallback(id, "{}");

            assertThat(execution.getExecutionStatus()).as("%s", early).isEqualTo(AlgorithmExecutionStatus.COMPLETED);
        }
    }

    @Test
    void failFromCallback_failsAnActiveExecution() {
        final UUID id = UUID.randomUUID();
        final AlgorithmExecution execution = stored(id, AlgorithmExecutionStatus.RUNNING);

        service.failFromCallback(id, "Callback reported ERROR state");

        assertThat(execution.getExecutionStatus()).isEqualTo(AlgorithmExecutionStatus.FAILED);
        assertThat(execution.getErrorMessage()).isEqualTo("Callback reported ERROR state");
    }

    @Test
    void failFromCallback_leavesAnEndedExecutionAlone() {
        final UUID id = UUID.randomUUID();
        final AlgorithmExecution execution = stored(id, AlgorithmExecutionStatus.COMPLETED);

        service.failFromCallback(id, "late");

        assertThat(execution.getExecutionStatus()).isEqualTo(AlgorithmExecutionStatus.COMPLETED);
        verify(repository, never()).save(any(AlgorithmExecution.class));
    }

    /**
     * The token use holds a row lock until the surrounding transaction ends, so completing must not start a
     * transaction of its own: REQUIRES_NEW would wait for that lock forever.
     */
    @Test
    @DisplayName("The callback methods join the caller's transaction instead of starting their own")
    void callbackMethodsJoinTheCallersTransaction() throws NoSuchMethodException {
        final Method complete = AlgorithmExecutionService.class.getMethod("completeFromCallback", UUID.class, String.class);
        final Method fail = AlgorithmExecutionService.class.getMethod("failFromCallback", UUID.class, String.class);

        assertThat(complete.getAnnotation(Transactional.class).propagation()).isEqualTo(Propagation.REQUIRED);
        assertThat(fail.getAnnotation(Transactional.class).propagation()).isEqualTo(Propagation.REQUIRED);
        assertThat(AlgorithmExecutionService.class.getMethod("markFailed", UUID.class, String.class)
                .getAnnotation(Transactional.class).propagation()).isEqualTo(Propagation.REQUIRES_NEW);
    }

    @Test
    void findLatestActiveByAnalysisId_returnsTheNewestActiveExecution() {
        final UUID analysisId = UUID.randomUUID();
        final AlgorithmExecution newest = AlgorithmExecution.builder().id(UUID.randomUUID())
                .executionStatus(AlgorithmExecutionStatus.RUNNING).build();
        when(repository.findByAnalysisIdAndExecutionStatusIn(org.mockito.ArgumentMatchers.eq(analysisId),
                org.mockito.ArgumentMatchers.anyCollection(), any(Pageable.class))).thenReturn(List.of(newest));

        assertThat(service.findLatestActiveByAnalysisId(analysisId)).containsSame(newest);
    }

    @Test
    void findLatestActiveByAnalysisId_isEmptyWithoutAnActiveExecution() {
        when(repository.findByAnalysisIdAndExecutionStatusIn(any(), org.mockito.ArgumentMatchers.anyCollection(),
                any(Pageable.class))).thenReturn(List.of());

        assertThat(service.findLatestActiveByAnalysisId(UUID.randomUUID())).isEmpty();
    }
}
