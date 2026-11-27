package si.result.project.eearly.port.algorithmexecution;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import si.result.project.eearly.config.AlgorithmExecutionStuckSweepProperties;

class AlgorithmExecutionStuckSweepSettingsValidatorTest {

    private static AlgorithmExecutionStuckSweepProperties props(final long timeoutMinutes, final long pollMs) {
        final AlgorithmExecutionStuckSweepProperties properties = new AlgorithmExecutionStuckSweepProperties();
        properties.setTimeoutMinutes(timeoutMinutes);
        properties.setPollIntervalMs(pollMs);
        return properties;
    }

    @Test
    @DisplayName("The defaults (90 min against 3600 s) are valid")
    void defaultsAreValid() {
        assertThatCode(() -> AlgorithmExecutionStuckSweepSettingsValidator.validate(
                new AlgorithmExecutionStuckSweepProperties(), 3600)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("A sweep timeout that is not longer than the HTTP timeout fails fast")
    void timeoutNotLongerThanHttpTimeout_fails() {
        assertThatThrownBy(() -> AlgorithmExecutionStuckSweepSettingsValidator.validate(props(60, 1000), 3600))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("must be longer than")
                .hasMessageContaining("BENTOML_TIMEOUT");
        assertThatThrownBy(() -> AlgorithmExecutionStuckSweepSettingsValidator.validate(props(30, 1000), 3600))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void timeoutJustAboveTheHttpTimeout_isValid() {
        assertThatCode(() -> AlgorithmExecutionStuckSweepSettingsValidator.validate(props(61, 1000), 3600))
                .doesNotThrowAnyException();
    }

    @Test
    void nonPositiveValues_fail() {
        assertThatThrownBy(() -> AlgorithmExecutionStuckSweepSettingsValidator.validate(props(0, 1000), 10))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("timeout-minutes");
        assertThatThrownBy(() -> AlgorithmExecutionStuckSweepSettingsValidator.validate(props(90, 0), 10))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("poll-interval-ms");
    }

    @Test
    void validatorRunsAtStartup() {
        final AlgorithmExecutionStuckSweepSettingsValidator validator =
                new AlgorithmExecutionStuckSweepSettingsValidator(props(10, 1000), 3600);
        assertThatThrownBy(validator::validateAtStartup).isInstanceOf(IllegalStateException.class);
    }
}
