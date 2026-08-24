package si.result.project.eearly.config.algorithm;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "algorithm.api")
public class AlgorithmApiProperties {

    private long defaultTimeoutSeconds = 3600;
    private long asyncPollIntervalMs = 5000;
    private String apiKey;
    private boolean verifySsl = true;
    private BasicAuth basicAuth = new BasicAuth();

    @Data
    public static class BasicAuth {
        private String username;
        private String password;
    }
}
