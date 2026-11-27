package si.result.project.eearly.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configures the sweep that fails an algorithm execution stuck in SUBMITTED or RUNNING for too long: the safety net
 * for a webhook-style provider that never reports back, since such a provider has no error callback to report a
 * silent failure with.
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "algorithm.stuck-execution-sweep")
public class AlgorithmExecutionStuckSweepProperties {

    public static final long DEFAULT_POLL_INTERVAL_MS = 300_000L;
    public static final long DEFAULT_TIMEOUT_MINUTES = 90;

    private boolean enabled = true;
    private long pollIntervalMs = DEFAULT_POLL_INTERVAL_MS;
    /** Must exceed the algorithm HTTP timeout (algorithm.api.default-timeout-seconds). */
    private long timeoutMinutes = DEFAULT_TIMEOUT_MINUTES;
}
