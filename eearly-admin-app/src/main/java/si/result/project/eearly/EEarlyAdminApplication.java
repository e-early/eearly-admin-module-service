package si.result.project.eearly;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

/**
 * Runnable eEarly admin service. All configuration (including {@code @EnableAsync} and {@code @EnableScheduling})
 * and the core default settings live in the {@code eearly-admin-service} library; this module only adds the main
 * class and the application-level settings.
 */
@ComponentScan(basePackages = "si.result")
@SpringBootApplication
public class EEarlyAdminApplication {

  public static void main(String[] args) {
    SpringApplication.run(EEarlyAdminApplication.class, args);
  }

}
