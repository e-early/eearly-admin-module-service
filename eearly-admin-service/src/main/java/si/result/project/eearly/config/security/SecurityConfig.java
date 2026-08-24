package si.result.project.eearly.config.security;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import si.result.spring.boot.bricks.auth.jwt.JwtConverter;

@Profile(value = {"!unrestricted"})
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
@EnableMethodSecurity(securedEnabled = true)
public class SecurityConfig {

  @Value("${spring.security.cors.allowed-origins}")
  private List<String> allowedOrigins;

  @Value("${spring.security.cors.allowed-methods}")
  private List<String> allowedMethods;

  private final List<String> allowedHeaders = List.of("Authorization",
          "Content-Type",
          "Accept",
          "X-Requested-With",
          "Origin",
          "Access-Control-Request-Headers",
          "Access-Control-Request-Method",
          "User-Agent",
          "Last-Event-ID",
          "Cache-Control");

  @SuppressWarnings({"java:S4502", "Intentionally suppressed warning, since this API is statelessand no-session or no-cookie based authentication is used"})
  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http
        .authorizeHttpRequests(auth -> auth
            .requestMatchers("/swagger-ui/**").permitAll()
            .requestMatchers("/v3/api-docs/**").permitAll()
            .requestMatchers("/v3/api-docs/swagger-config").permitAll()
            .requestMatchers("/v3/api-docs").permitAll()
            .requestMatchers("/api/v1/mobile/*").permitAll()
            .anyRequest().authenticated());
    http.csrf(AbstractHttpConfigurer::disable);
    http.cors(cors -> cors.configurationSource(corsConfigurationSource()));
    http.oauth2ResourceServer(resourceServer -> resourceServer.jwt(
        conf -> conf.jwtAuthenticationConverter(new JwtConverter())));
    return http.build();
  }

  private UrlBasedCorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration configuration = new CorsConfiguration();
    configuration.setAllowedOriginPatterns(allowedOrigins);
    configuration.setAllowedMethods(allowedMethods);
    configuration.setAllowedHeaders(allowedHeaders);
    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", configuration);
    return source;
  }

}
