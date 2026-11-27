package si.result.project.eearly.config.security;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import si.result.project.eearly.auth.AdminRoles;
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

  @Value("${admin.keycloak.ui-client-id}")
  private String uiClientId;

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
            // Administration: organizations are managed by administrators only.
            .requestMatchers("/api/v1/organizations", "/api/v1/organizations/**")
                .hasRole(AdminRoles.ADMINISTRATOR)
            // Departments are created and changed by administrators only; reading them (profile,
            // patient form) stays open to any authenticated caller.
            .requestMatchers(HttpMethod.POST, "/api/v1/departments").hasRole(AdminRoles.ADMINISTRATOR)
            .requestMatchers(HttpMethod.PUT, "/api/v1/departments/*").hasRole(AdminRoles.ADMINISTRATOR)
            // Caretakers are listed and created by administrators only. Reading or updating one
            // is for the caretaker themself or an administrator (CaretakerServiceFacade).
            .requestMatchers(HttpMethod.GET, "/api/v1/caretakers").hasRole(AdminRoles.ADMINISTRATOR)
            .requestMatchers(HttpMethod.POST, "/api/v1/caretakers").hasRole(AdminRoles.ADMINISTRATOR)
            // Measurement types are created and changed by administrators only; reading them stays
            // open to any authenticated caller.
            .requestMatchers(HttpMethod.POST, "/api/v1/measurements").hasRole(AdminRoles.ADMINISTRATOR)
            .requestMatchers(HttpMethod.PUT, "/api/v1/measurements/*").hasRole(AdminRoles.ADMINISTRATOR)
            // Health conditions are created by administrators only; reading them stays open to any
            // authenticated caller.
            .requestMatchers(HttpMethod.POST, "/api/v1/healthconditions").hasRole(AdminRoles.ADMINISTRATOR)
            // Service-to-service calls from the mobile backend (e.g. the language a patient picked
            // in the app), made with its own service account's token.
            .requestMatchers("/api/v1/internal/**").hasRole(AdminRoles.MOBILE_SERVICE)
            .anyRequest().authenticated());
    http.csrf(AbstractHttpConfigurer::disable);
    http.cors(cors -> cors.configurationSource(corsConfigurationSource()));
    http.oauth2ResourceServer(resourceServer -> resourceServer.jwt(
        conf -> conf.jwtAuthenticationConverter(jwtConverter(uiClientId))));
    return http.build();
  }

  /**
   * Roles are client roles of the configured Keycloak client, in
   * resource_access.&lt;client id&gt;.roles, not realm roles (JwtConverter's default).
   */
  static JwtConverter jwtConverter(final String uiClientId) {
    return new JwtConverter("sub", "resource_access." + uiClientId + ".roles", "ROLE_");
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
