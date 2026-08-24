package si.result.project.eearly.config.auditing;

import java.util.Optional;
import lombok.AllArgsConstructor;
import lombok.NonNull;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import si.result.spring.boot.bricks.auth.AuthenticationProvider;

@Configuration
@EnableJpaAuditing(
    auditorAwareRef = "auditorAware",
    dateTimeProviderRef = "zonedDateTimeProvider"
)
@AllArgsConstructor
public class AuditorAwareImpl {

    private final AuthenticationProvider authenticationProvider;

    @NonNull
    public Optional<String> getCurrentAuditor() {
        try {
            return Optional.ofNullable(authenticationProvider.getName());
        } catch (ClassCastException | NullPointerException ex) {
            // anonimen ali ne-JWT request (npr. interni RestTemplate klic) -> brez auditorja
            return Optional.empty();
        }
    }

    @Bean
    public AuditorAware<String> auditorAware() {
        return this::getCurrentAuditor;
    }
}
