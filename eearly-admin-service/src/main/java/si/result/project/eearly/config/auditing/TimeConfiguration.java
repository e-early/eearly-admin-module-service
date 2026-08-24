package si.result.project.eearly.config.auditing;

import java.time.ZoneId;
import java.time.ZoneOffset;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TimeConfiguration {

    @Bean
    public ZoneId defaultZoneId() {
        // Can be UTC, system default, or from application properties
        return ZoneOffset.UTC;
    }
}
