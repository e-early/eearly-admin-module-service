package si.result.project.eearly.email;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.core.env.Environment;
import si.result.project.eearly.config.email.EmailConfig;
import si.result.project.eearly.port.email.EmailProperties;

/**
 * Binding of the {@code email.*} settings, and the defaults of the shipped application.yaml: neutral values only, no
 * host or address of any deployment.
 */
class EmailConfigurationBindingTest {

    private static final String APP = "spring.application.name=eearly-admin-service";

    private final ApplicationContextRunner shipped = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withUserConfiguration(EmailConfig.class)
            .withPropertyValues(APP);

    @Test
    void shippedDefaults_areNeutral() {
        shipped.run(context -> {
            EmailProperties properties = context.getBean(EmailProperties.class);
            assertThat(properties.from()).isEqualTo("noreply@example.org");
            assertThat(properties.appName()).isEqualTo("eEarly");
            assertThat(properties.allowedRecipientDomains()).isEqualTo("*");
            assertThat(properties.appDownloadUrl()).isEmpty();
            assertThat(properties.logo()).isEqualTo("classpath:images/email-logo.png");
            assertThat(properties.templates().path()).isEqualTo("classpath:templates/email/");
            assertThat(properties.qrcode().template()).isEqualTo("qr-code-email.html");

            Environment env = context.getEnvironment();
            assertThat(env.getProperty("spring.mail.host")).isEqualTo("localhost");
            assertThat(env.getProperty("spring.mail.port")).isEqualTo("25");
            assertThat(env.getProperty("spring.mail.username")).isEmpty();
            assertThat(env.getProperty("spring.mail.password")).isEmpty();
            assertThat(env.getProperty("spring.mail.properties.mail.smtp.auth")).isEqualTo("false");
            assertThat(env.getProperty("spring.mail.properties.mail.smtp.starttls.enable")).isEqualTo("false");
            assertThat(env.getProperty("spring.mail.properties.mail.smtp.ssl.enable")).isEqualTo("false");
        });
    }

    @Test
    void developmentProfile_pointsToALocalCatchAllServer() {
        shipped.withPropertyValues("spring.profiles.active=development").run(context -> {
            Environment env = context.getEnvironment();
            assertThat(env.getProperty("spring.mail.host")).isEqualTo("localhost");
            assertThat(env.getProperty("spring.mail.port")).isEqualTo("1025");
            assertThat(context.getBean(EmailProperties.class).from()).isEqualTo("noreply@example.org");
        });
    }

    @Test
    void environmentValuesOverrideTheDefaults() {
        shipped.withPropertyValues(
                "MAIL_HOST=smtp.example.org",
                "MAIL_PORT=587",
                "MAIL_USERNAME=mailer",
                "MAIL_SMTP_AUTH=true",
                "MAIL_SMTP_STARTTLS=true",
                "EMAIL_FROM=care@example.org",
                "EMAIL_APP_NAME=Care App",
                "EMAIL_ALLOWED_RECIPIENT_DOMAINS=example.org,example.com",
                "EMAIL_APP_DOWNLOAD_URL=https://app.example.org/get",
                "EMAIL_LOGO=file:/etc/app/logo.png",
                "EMAIL_TEMPLATE_FOLDER=file:/etc/app/templates/",
                "EMAIL_TEMPLATE=brand-qr.html").run(context -> {
                    EmailProperties properties = context.getBean(EmailProperties.class);
                    assertThat(properties.from()).isEqualTo("care@example.org");
                    assertThat(properties.appName()).isEqualTo("Care App");
                    assertThat(properties.allowedRecipientDomains()).isEqualTo("example.org,example.com");
                    assertThat(properties.appDownloadUrl()).isEqualTo("https://app.example.org/get");
                    assertThat(properties.logo()).isEqualTo("file:/etc/app/logo.png");
                    assertThat(properties.templates().path()).isEqualTo("file:/etc/app/templates/");
                    assertThat(properties.qrcode().template()).isEqualTo("brand-qr.html");

                    Environment env = context.getEnvironment();
                    assertThat(env.getProperty("spring.mail.host")).isEqualTo("smtp.example.org");
                    assertThat(env.getProperty("spring.mail.port")).isEqualTo("587");
                    assertThat(env.getProperty("spring.mail.username")).isEqualTo("mailer");
                    assertThat(env.getProperty("spring.mail.properties.mail.smtp.auth")).isEqualTo("true");
                    assertThat(env.getProperty("spring.mail.properties.mail.smtp.starttls.enable")).isEqualTo("true");
                });
    }

    @Test
    void withoutAnySettings_thePropertiesClassStillHasNeutralDefaults() {
        new ApplicationContextRunner()
                .withUserConfiguration(EmailConfig.class)
                .withPropertyValues(APP)
                .run(context -> {
                    EmailProperties properties = context.getBean(EmailProperties.class);
                    assertThat(properties.from()).isEqualTo("noreply@example.org");
                    assertThat(properties.templates().path()).isEqualTo("classpath:templates/email/");
                    assertThat(properties.qrcode().template()).isEqualTo("qr-code-email.html");
                });
    }
}
