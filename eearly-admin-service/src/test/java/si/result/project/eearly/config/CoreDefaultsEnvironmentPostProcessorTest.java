package si.result.project.eearly.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.mock.env.MockEnvironment;

class CoreDefaultsEnvironmentPostProcessorTest {

    private final CoreDefaultsEnvironmentPostProcessor processor = new CoreDefaultsEnvironmentPostProcessor();

    @Test
    void defaultsAreTheLowestPrecedenceSource() {
        MockEnvironment environment = new MockEnvironment();
        processor.postProcessEnvironment(environment, null);

        MutablePropertySources sources = environment.getPropertySources();
        assertThat(sources.contains(CoreDefaultsEnvironmentPostProcessor.PROPERTY_SOURCE_NAME)).isTrue();
        assertThat(sources.precedenceOf(sources.get(CoreDefaultsEnvironmentPostProcessor.PROPERTY_SOURCE_NAME)))
                .isEqualTo(sources.size() - 1);
    }

    @Test
    void higherPrecedenceSourceOverridesADefault_andDefaultOnlyKeysStillResolve() {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("application",
                Map.of("server.port", "8123", "email.from", "care@example.org")));

        processor.postProcessEnvironment(environment, null);

        assertThat(environment.getProperty("server.port")).isEqualTo("8123");
        assertThat(environment.getProperty("email.from")).isEqualTo("care@example.org");
        // only present in the defaults
        assertThat(environment.getProperty("spring.mail.host")).isEqualTo("localhost");
        assertThat(environment.getProperty("spring.jpa.open-in-view")).isEqualTo("false");
    }

    @Test
    void withNothingSet_theDefaultsApply_andPlaceholdersResolveWithTheirFallbacks() {
        StandardEnvironment environment = new StandardEnvironment();
        processor.postProcessEnvironment(environment, null);

        assertThat(environment.getProperty("server.port")).isEqualTo("9000");
        assertThat(environment.getProperty("spring.mail.port")).isEqualTo("25");
        assertThat(environment.getProperty("email.from")).isEqualTo("noreply@example.org");
    }

    @Test
    void anEnvironmentValueFeedsAPlaceholderOfTheDefaults() {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("env", Map.of("MAIL_HOST", "smtp.example.org")));
        processor.postProcessEnvironment(environment, null);

        assertThat(environment.getProperty("spring.mail.host")).isEqualTo("smtp.example.org");
    }

    @Test
    void runningTwiceDoesNotDuplicateTheSource() {
        StandardEnvironment environment = new StandardEnvironment();
        processor.postProcessEnvironment(environment, null);
        int size = environment.getPropertySources().size();
        processor.postProcessEnvironment(environment, null);

        assertThat(environment.getPropertySources().size()).isEqualTo(size);
    }

    @Test
    void aCustomConfigName_replacesTheDefaultConfigurationFilesAndSoTheDefaults() {
        MockEnvironment environment = new MockEnvironment().withProperty("spring.config.name", "other-config");
        processor.postProcessEnvironment(environment, null);

        assertThat(environment.getPropertySources().contains(CoreDefaultsEnvironmentPostProcessor.PROPERTY_SOURCE_NAME))
                .isFalse();
    }

    @Test
    void theStandardConfigName_keepsTheDefaults() {
        MockEnvironment environment = new MockEnvironment().withProperty("spring.config.name", "application");
        processor.postProcessEnvironment(environment, null);

        assertThat(environment.getPropertySources().contains(CoreDefaultsEnvironmentPostProcessor.PROPERTY_SOURCE_NAME))
                .isTrue();
    }
}
