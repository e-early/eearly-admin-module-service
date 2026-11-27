package si.result.project.eearly.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Settings for the per-algorithm credentials. The password of an algorithm is encrypted at rest with AES-GCM under
 * {@link #encryptionKey}. Without a key no algorithm password can be stored or used.
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "algorithm.credentials")
public class AlgorithmCredentialsProperties {

    /** Base64 of a 32-byte (AES-256) key. Blank (the default) means no key. */
    private String encryptionKey;

    @Override
    public String toString() {
        return "AlgorithmCredentialsProperties(encryptionKey="
                + (encryptionKey == null || encryptionKey.isBlank() ? "unset" : "***") + ")";
    }
}
