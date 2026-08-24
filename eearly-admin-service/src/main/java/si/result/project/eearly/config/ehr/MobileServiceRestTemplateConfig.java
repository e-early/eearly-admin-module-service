package si.result.project.eearly.config.ehr;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
public class MobileServiceRestTemplateConfig {

  @Value("${mobile.service.base-url}")
  private String baseUrl;

  @Bean(name = "mobileServiceRestTemplate")
  public RestTemplate mobileServiceRestTemplate(RestTemplateBuilder builder) {
    return builder
        .rootUri(baseUrl)
        .build();
  }
}