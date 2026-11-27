package si.result.project.eearly.config.scheduling;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Enables {@code @Scheduled} methods for every application that includes this library. */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
