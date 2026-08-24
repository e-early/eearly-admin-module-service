package si.result.project.eearly;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@ComponentScan(basePackages = "si.result")
@SpringBootApplication
@EnableAsync
@EnableScheduling
public class EEarlyAdminService {

  public static void main(String[] args) {
    SpringApplication.run(EEarlyAdminService.class, args);
  }

}
