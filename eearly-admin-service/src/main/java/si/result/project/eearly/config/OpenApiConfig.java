package si.result.project.eearly.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.security.SecurityScheme.Type;
import io.swagger.v3.oas.models.servers.Server;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.info.BuildProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
@RequiredArgsConstructor
public class OpenApiConfig {

  private final BuildProperties buildProperties;

  @Value("${service.domain}")
  private String serviceDomain;

  @Bean
  public OpenAPI customOpenApi() {
    return new OpenAPI()
        .info(apiInfo())
        .servers(List.of(new Server().url(serviceDomain)))
        .addSecurityItem(new SecurityRequirement().addList("bearerAuth"))
        .components(new Components()
            .addSecuritySchemes("bearerAuth", new SecurityScheme()
                .type(Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT")
                .in(SecurityScheme.In.HEADER)
                .name("Authorization")
            ));
  }

  private Info apiInfo() {
    return new Info()
        .title("Project Domain Service")
        .description(String.format(
            """
                <strong>
                Build time: %s<br />
                Version: %s
                </strong>
                """,
            buildProperties.getTime(),
            buildProperties.getVersion()))
        .version(buildProperties.getVersion());
  }

}
