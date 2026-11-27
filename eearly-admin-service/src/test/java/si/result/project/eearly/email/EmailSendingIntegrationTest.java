package si.result.project.eearly.email;

import static org.assertj.core.api.Assertions.assertThat;

import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.ServerSetupTest;
import jakarta.mail.BodyPart;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.net.ServerSocket;
import java.util.Base64;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.mail.MailSenderAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import si.result.project.eearly.config.email.EmailConfig;
import si.result.project.eearly.model.email.command.SendQRCodeEmailCommand;
import si.result.project.eearly.port.email.AsyncEmailService;
import si.result.project.eearly.port.email.EmailService;
import si.result.project.eearly.port.email.EmailTemplateService;

/**
 * The e-mail stack (properties, templates, mail sender, asynchronous sending on its own thread pool) against an
 * embedded SMTP server, with made-up data.
 */
@ExtendWith(OutputCaptureExtension.class)
class EmailSendingIntegrationTest {

    private static final String PATIENT_EMAIL = "test.patient@example.com";
    private static final String PATIENT_NAME = "Test Patient";
    private static final String CARETAKER_NAME = "Test Caretaker";
    private static final String CARETAKER_EMAIL = "test.caretaker@example.org";
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0d, 0x0a, 0x1a, 0x0a};

    @RegisterExtension
    static final GreenMailExtension GREEN_MAIL = new GreenMailExtension(ServerSetupTest.SMTP.dynamicPort());

    private ApplicationContextRunner runner(final int smtpPort) {
        return new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(MailSenderAutoConfiguration.class))
                .withUserConfiguration(
                        EmailConfig.class, EmailTemplateService.class, EmailService.class, AsyncEmailService.class)
                .withPropertyValues(
                        "spring.application.name=eearly-admin-service",
                        "spring.mail.host=127.0.0.1",
                        "spring.mail.port=" + smtpPort,
                        "email.from=care@example.org",
                        "email.app-name=Care App",
                        "email.app-download-url=https://app.example.org/get");
    }

    private static SendQRCodeEmailCommand command(final String language) {
        return new SendQRCodeEmailCommand(
                "Test App", PATIENT_EMAIL, PATIENT_NAME, CARETAKER_NAME, CARETAKER_EMAIL,
                "data:image/png;base64," + Base64.getEncoder().encodeToString(PNG), language);
    }

    @Test
    void sendsOneMessageFromTheConfiguredAddressWithTheRenderedBody() {
        runner(GREEN_MAIL.getSmtp().getPort()).run(context -> {
            context.getBean(AsyncEmailService.class).sendQRCodeEmailAsync(command("en")).get(30, TimeUnit.SECONDS);

            assertThat(GREEN_MAIL.waitForIncomingEmail(1)).isTrue();
            MimeMessage[] messages = GREEN_MAIL.getReceivedMessages();
            assertThat(messages).hasSize(1);
            MimeMessage message = messages[0];
            assertThat(((InternetAddress) message.getFrom()[0]).getAddress()).isEqualTo("care@example.org");
            assertThat(((InternetAddress) message.getFrom()[0]).getPersonal()).isEqualTo("Test App");
            assertThat(((InternetAddress) message.getReplyTo()[0]).getAddress()).isEqualTo(CARETAKER_EMAIL);
            assertThat(((InternetAddress) message.getAllRecipients()[0]).getAddress()).isEqualTo(PATIENT_EMAIL);
            assertThat(message.getSubject()).isEqualTo("Your Healthcare Monitoring QR Code");

            String html = htmlOf(message);
            assertThat(html)
                    .contains("<html lang=\"en\">")
                    .contains("Dear " + PATIENT_NAME)
                    .contains(CARETAKER_NAME)
                    .contains("cid:qrcode")
                    .contains("cid:logo")
                    .contains("https://app.example.org/get")
                    .doesNotContain("{{");
            assertThat(partsWithContentId(message)).contains("<qrcode>", "<logo>");
        });
    }

    @Test
    void aSlovenianPatientGetsTheSlovenianMessage() {
        runner(GREEN_MAIL.getSmtp().getPort()).run(context -> {
            context.getBean(AsyncEmailService.class).sendQRCodeEmailAsync(command(null)).get(30, TimeUnit.SECONDS);

            assertThat(GREEN_MAIL.waitForIncomingEmail(1)).isTrue();
            MimeMessage message = GREEN_MAIL.getReceivedMessages()[0];
            assertThat(message.getSubject()).isEqualTo("Vaša QR koda za spremljanje zdravja");
            assertThat(htmlOf(message)).contains("<html lang=\"sl\">").contains("Pozdravljeni, " + PATIENT_NAME);
        });
    }

    @Test
    void aFailingServerNeverFailsTheCaller_andTheLogHasNoPersonalData(final CapturedOutput output) throws Exception {
        int closedPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            closedPort = socket.getLocalPort();
        }

        runner(closedPort).run(context -> {
            context.getBean(AsyncEmailService.class).sendQRCodeEmailAsync(command("sl")).get(30, TimeUnit.SECONDS);

            assertThat(GREEN_MAIL.getReceivedMessages()).isEmpty();
        });

        assertThat(output).contains("QR_CODE");
        assertThat(output)
                .doesNotContain(PATIENT_EMAIL).doesNotContain(PATIENT_NAME)
                .doesNotContain(CARETAKER_EMAIL).doesNotContain(CARETAKER_NAME);
    }

    private static String htmlOf(final Part part) throws Exception {
        if (part.isMimeType("text/html")) {
            return (String) part.getContent();
        }
        if (part.isMimeType("multipart/*")) {
            Multipart multipart = (Multipart) part.getContent();
            for (int i = 0; i < multipart.getCount(); i++) {
                String html = htmlOf(multipart.getBodyPart(i));
                if (html != null) {
                    return html;
                }
            }
        }
        return null;
    }

    private static java.util.List<String> partsWithContentId(final Part part) throws Exception {
        java.util.List<String> ids = new java.util.ArrayList<>();
        String[] header = part.getHeader("Content-ID");
        if (header != null) {
            ids.add(header[0]);
        }
        if (part.isMimeType("multipart/*")) {
            Multipart multipart = (Multipart) part.getContent();
            for (int i = 0; i < multipart.getCount(); i++) {
                BodyPart child = multipart.getBodyPart(i);
                ids.addAll(partsWithContentId(child));
            }
        }
        return ids;
    }
}
