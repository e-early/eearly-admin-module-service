package si.result.project.eearly.config.auditing;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Optional;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
@Configuration
public class ZonedDateTimeConfig {

    @Bean("zonedDateTimeProvider")
    public DateTimeProvider dateTimeProvider(ZoneId defaultZoneId) {
        return () -> Optional.of(ZonedDateTime.now(defaultZoneId));
    }
}