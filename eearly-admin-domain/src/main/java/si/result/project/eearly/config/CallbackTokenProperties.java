package si.result.project.eearly.config;

import java.time.Duration;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Settings of the single-use callback token a webhook-style algorithm provider presents when it reports its result
 * to {@code POST /api/v1/analyses/{id}/result}. Callback tokens are opt-in: they exist only while a
 * {@link #tokenSecret} of at least {@value #MIN_SECRET_BYTES} bytes is configured.
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "algorithm.callback")
public class CallbackTokenProperties {

    /** Smallest accepted secret, in bytes (UTF-8): the size of the HMAC-SHA256 key. */
    public static final int MIN_SECRET_BYTES = 32;

    /** Secret that signs the tokens. Blank (the default) switches callback tokens off. */
    private String tokenSecret;

    /** How long an issued token is accepted. */
    private Duration tokenTtl = Duration.ofMinutes(15);

    @Override
    public String toString() {
        return "CallbackTokenProperties(tokenSecret=" + (tokenSecret == null || tokenSecret.isBlank() ? "unset" : "***")
                + ", tokenTtl=" + tokenTtl + ")";
    }
}
