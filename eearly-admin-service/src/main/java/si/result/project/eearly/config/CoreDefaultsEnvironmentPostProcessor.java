package si.result.project.eearly.config;

import java.io.IOException;
import java.util.List;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

/**
 * Adds the core default settings of the library ({@code eearly-admin-defaults.yaml}) to the environment with the
 * lowest precedence, so that an application's own {@code application.yaml}, profile files, environment variables and
 * system properties override them. Placeholders such as {@code ${DB_URL:...}} are resolved when properties are read.
 *
 * <p>The defaults play the role of the library's own {@code application.yaml}: when {@code spring.config.name} is set
 * to something other than {@code application}, the default configuration files are replaced, and so are these defaults.
 */
public class CoreDefaultsEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

  public static final String PROPERTY_SOURCE_NAME = "eearly-admin-defaults";
  public static final String DEFAULTS_LOCATION = "eearly-admin-defaults.yaml";

  @Override
  public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
    if (environment.getPropertySources().contains(PROPERTY_SOURCE_NAME)) {
      return;
    }
    String configName = environment.getProperty("spring.config.name");
    if (configName != null && !configName.isBlank() && !"application".equals(configName.trim())) {
      return;
    }
    Resource resource = new ClassPathResource(DEFAULTS_LOCATION);
    if (!resource.exists()) {
      return;
    }
    List<PropertySource<?>> sources;
    try {
      sources = new YamlPropertySourceLoader().load(PROPERTY_SOURCE_NAME, resource);
    } catch (IOException e) {
      throw new IllegalStateException("Cannot load core defaults " + DEFAULTS_LOCATION, e);
    }
    // Several YAML documents are loaded in file order; a later document overrides an earlier one, so they are
    // appended last-first. All of them stay below every other property source.
    for (int i = sources.size() - 1; i >= 0; i--) {
      environment.getPropertySources().addLast(sources.get(i));
    }
  }

  @Override
  public int getOrder() {
    return Ordered.LOWEST_PRECEDENCE;
  }
}
