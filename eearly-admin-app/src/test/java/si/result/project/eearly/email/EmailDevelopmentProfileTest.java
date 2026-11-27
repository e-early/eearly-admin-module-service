package si.result.project.eearly.email;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.Environment;
import si.result.project.eearly.config.CoreDefaultsEnvironmentPostProcessor;
import si.result.project.eearly.config.email.EmailConfig;
import si.result.project.eearly.port.email.EmailProperties;

/**
 * Moved unchanged from the library's EmailConfigurationBindingTest: the {@code development} profile file
 * (application-development.yaml) now lives in this module.
 */
class EmailDevelopmentProfileTest {

    private static final String APP = "spring.application.name=eearly-admin-service";

    private final ApplicationContextRunner shipped = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            // ApplicationContextRunner does not run EnvironmentPostProcessors; apply the core defaults as the app does
            .withInitializer(context -> new CoreDefaultsEnvironmentPostProcessor()
                    .postProcessEnvironment(context.getEnvironment(), null))
            .withUserConfiguration(EmailConfig.class)
            .withPropertyValues(APP);

    @Test
    void developmentProfile_pointsToALocalCatchAllServer() {
        shipped.withPropertyValues("spring.profiles.active=development").run(context -> {
            Environment env = context.getEnvironment();
            assertThat(env.getProperty("spring.mail.host")).isEqualTo("localhost");
            assertThat(env.getProperty("spring.mail.port")).isEqualTo("1025");
            assertThat(context.getBean(EmailProperties.class).from()).isEqualTo("noreply@example.org");
        });
    }
}
