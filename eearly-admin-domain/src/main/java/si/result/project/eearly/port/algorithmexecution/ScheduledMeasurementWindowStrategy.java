package si.result.project.eearly.port.algorithmexecution;

import java.util.Optional;
import si.result.project.eearly.model.algorithmexecution.AlgorithmExecution;

/**
 * Extension point for executions that were triggered by a schedule: tells {@link AlgorithmExecutionPayloadBuilder}
 * which measurement window the input data of such an execution covers.
 *
 * <p>This core has no scheduling of executions, so it ships no implementation, and without one a scheduled
 * execution is prepared like any other from the input schema of its algorithm. An add-on that schedules executions
 * provides a Spring bean of this type. If several beans exist the first one in Spring's order is used.
 */
public interface ScheduledMeasurementWindowStrategy {

    /**
     * The window of the scheduled execution, or empty if this strategy has none for it, in which case the default
     * preparation is used. It may throw a {@link si.result.spring.boot.bricks.exception.DomainException} for an
     * execution that must not run without a window.
     */
    Optional<MeasurementWindow> resolve(AlgorithmExecution execution);

    /** Start and end of the window, as ISO-8601 date-times with an offset. */
    record MeasurementWindow(String start, String end) {
    }

    /** The default: no window, so the standard preparation applies. */
    ScheduledMeasurementWindowStrategy NONE = execution -> Optional.empty();
}
