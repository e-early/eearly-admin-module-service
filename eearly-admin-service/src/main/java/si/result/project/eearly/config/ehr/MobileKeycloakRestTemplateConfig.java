package si.result.project.eearly.config.ehr;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
public class MobileKeycloakRestTemplateConfig {

  @Value("${mobile.keycloak.base-url}")
  private String baseUrl;

  @Bean(name = "mobileKeycloakRestTemplate")
  public RestTemplate mobileKeycloakRestTemplate(RestTemplateBuilder builder) {
    return builder
        .rootUri(baseUrl)
        .build();
  }
}