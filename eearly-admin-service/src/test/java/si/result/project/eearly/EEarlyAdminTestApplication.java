package si.result.project.eearly;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

/**
 * Test-only {@code @SpringBootConfiguration} of the library module (the runnable application class lives in
 * {@code eearly-admin-app}), so that {@code @SpringBootTest} and slice tests find a configuration.
 */
@ComponentScan(basePackages = "si.result")
@SpringBootApplication
public class EEarlyAdminTestApplication {
}
