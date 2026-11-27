package si.result.project.eearly.model.algorithmexecution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import si.result.project.eearly.exception.CallbackTokenRejectedException;
import si.result.project.eearly.exception.CallbackTokenRejectedException.Reason;

class AlgorithmExecutionCallbackTokenTest {

    private static final Instant NOW = Instant.parse("2026-06-01T10:00:00Z");

    private AlgorithmExecution execution(final AlgorithmExecutionStatus status) {
        return AlgorithmExecution.builder().id(UUID.randomUUID()).executionStatus(status).build();
    }

    private static Reason reasonOf(final Runnable action) {
        try {
            action.run();
        } catch (CallbackTokenRejectedException exception) {
            return exception.getReason();
        }
        throw new AssertionError("not rejected");
    }

    @Test
    void issuedToken_canBeConsumedOnce() {
        final AlgorithmExecution execution = execution(AlgorithmExecutionStatus.RUNNING);
        final UUID jti = UUID.randomUUID();
        execution.issueCallbackToken(jti, NOW.plusSeconds(60));

        execution.consumeCallbackToken(jti, NOW);

        assertThat(execution.getCallbackTokenConsumedAt()).isEqualTo(NOW);
        assertThat(reasonOf(() -> execution.consumeCallbackToken(jti, NOW))).isEqualTo(Reason.ALREADY_USED);
    }

    @Test
    void noTokenIssued_nothingCanBeConsumed() {
        final AlgorithmExecution execution = execution(AlgorithmExecutionStatus.RUNNING);

        assertThat(reasonOf(() -> execution.consumeCallbackToken(UUID.randomUUID(), NOW))).isEqualTo(Reason.SUPERSEDED);
        assertThat(reasonOf(() -> execution.consumeCallbackToken(null, NOW))).isEqualTo(Reason.SUPERSEDED);
    }

    @Test
    void anotherTokenId_isRefused() {
        final AlgorithmExecution execution = execution(AlgorithmExecutionStatus.RUNNING);
        execution.issueCallbackToken(UUID.randomUUID(), NOW.plusSeconds(60));

        assertThat(reasonOf(() -> execution.consumeCallbackToken(UUID.randomUUID(), NOW))).isEqualTo(Reason.SUPERSEDED);
        assertThat(execution.getCallbackTokenConsumedAt()).isNull();
    }

    @Test
    @DisplayName("Issuing again resets the use, so the new token works and the old one does not")
    void reissue_replacesTheOldToken() {
        final AlgorithmExecution execution = execution(AlgorithmExecutionStatus.RUNNING);
        final UUID first = UUID.randomUUID();
        final UUID second = UUID.randomUUID();
        execution.issueCallbackToken(first, NOW.plusSeconds(60));
        execution.consumeCallbackToken(first, NOW);

        execution.issueCallbackToken(second, NOW.plusSeconds(120));

        assertThat(execution.getCallbackTokenConsumedAt()).isNull();
        assertThat(reasonOf(() -> execution.consumeCallbackToken(first, NOW))).isEqualTo(Reason.SUPERSEDED);
        assertThatCode(() -> execution.consumeCallbackToken(second, NOW)).doesNotThrowAnyException();
    }

    @Test
    void expiredToken_isRefused_atTheExactExpiry() {
        final AlgorithmExecution execution = execution(AlgorithmExecutionStatus.RUNNING);
        final UUID jti = UUID.randomUUID();
        execution.issueCallbackToken(jti, NOW);

        assertThat(reasonOf(() -> execution.consumeCallbackToken(jti, NOW))).isEqualTo(Reason.EXPIRED);
        assertThat(execution.getCallbackTokenConsumedAt()).isNull();
    }

    @Test
    void endedExecution_refusesTheToken() {
        for (final AlgorithmExecutionStatus ended : new AlgorithmExecutionStatus[] {AlgorithmExecutionStatus.COMPLETED,
                AlgorithmExecutionStatus.FAILED, AlgorithmExecutionStatus.CANCELLED}) {
            final AlgorithmExecution execution = execution(ended);
            final UUID jti = UUID.randomUUID();
            execution.issueCallbackToken(jti, NOW.plusSeconds(60));

            assertThat(reasonOf(() -> execution.consumeCallbackToken(jti, NOW)))
                    .as("%s", ended).isEqualTo(Reason.EXECUTION_NOT_ACTIVE);
        }
    }

    @Test
    void pendingSubmittedAndRunningExecutions_acceptTheToken() {
        for (final AlgorithmExecutionStatus open : new AlgorithmExecutionStatus[] {AlgorithmExecutionStatus.PENDING,
                AlgorithmExecutionStatus.SUBMITTED, AlgorithmExecutionStatus.RUNNING}) {
            final AlgorithmExecution execution = execution(open);
            final UUID jti = UUID.randomUUID();
            execution.issueCallbackToken(jti, NOW.plusSeconds(60));

            assertThatCode(() -> execution.consumeCallbackToken(jti, NOW)).doesNotThrowAnyException();
        }
    }
}
