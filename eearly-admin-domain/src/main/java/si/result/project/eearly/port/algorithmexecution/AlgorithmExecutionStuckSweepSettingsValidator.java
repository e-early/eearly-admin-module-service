package si.result.project.eearly.port.algorithmexecution;

import jakarta.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import si.result.project.eearly.config.AlgorithmExecutionStuckSweepProperties;

/**
 * Fails the start of the application when the settings of the stuck-execution sweep are unusable. Most importantly the
 * sweep timeout must be longer than the timeout of the algorithm HTTP client
 * ({@code algorithm.api.default-timeout-seconds}, env {@code BENTOML_TIMEOUT}): otherwise the sweep would fail
 * executions the adapter is still legitimately waiting for.
 */
@Component
@ConditionalOnProperty(prefix = "algorithm.stuck-execution-sweep", name = "enabled", havingValue = "true",
        matchIfMissing = true)
public class AlgorithmExecutionStuckSweepSettingsValidator {

    private final AlgorithmExecutionStuckSweepProperties properties;
    private final long adapterTimeoutSeconds;

    public AlgorithmExecutionStuckSweepSettingsValidator(
            final AlgorithmExecutionStuckSweepProperties properties,
            @Value("${algorithm.api.default-timeout-seconds:3600}") final long adapterTimeoutSeconds) {
        this.properties = properties;
        this.adapterTimeoutSeconds = adapterTimeoutSeconds;
    }

    @PostConstruct
    void validateAtStartup() {
        validate(properties, adapterTimeoutSeconds);
    }

    /** @throws IllegalStateException if the settings cannot be used */
    public static void validate(final AlgorithmExecutionStuckSweepProperties properties,
                                final long adapterTimeoutSeconds) {
        if (properties.getPollIntervalMs() <= 0) {
            throw new IllegalStateException(
                    "algorithm.stuck-execution-sweep.poll-interval-ms must be positive, but is "
                            + properties.getPollIntervalMs());
        }
        if (properties.getTimeoutMinutes() <= 0) {
            throw new IllegalStateException(
                    "algorithm.stuck-execution-sweep.timeout-minutes must be positive, but is "
                            + properties.getTimeoutMinutes());
        }
        final long timeoutSeconds = properties.getTimeoutMinutes() * 60;
        if (timeoutSeconds <= adapterTimeoutSeconds) {
            throw new IllegalStateException("algorithm.stuck-execution-sweep.timeout-minutes ("
                    + properties.getTimeoutMinutes() + " min = " + timeoutSeconds + " s) must be longer than the "
                    + "algorithm HTTP timeout algorithm.api.default-timeout-seconds / BENTOML_TIMEOUT ("
                    + adapterTimeoutSeconds + " s); raise the sweep timeout, lower the HTTP timeout, or disable "
                    + "the sweep with algorithm.stuck-execution-sweep.enabled=false");
        }
    }
}
