package si.result.project.eearly.email;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;
import si.result.project.eearly.model.email.QRCodeEmailContent;
import si.result.project.eearly.port.email.EmailProperties;
import si.result.project.eearly.port.email.EmailTemplateService;

/**
 * The default QR code templates shipped with the service, rendered with made-up data: both languages, neutral
 * content, every placeholder filled.
 */
class QRCodeEmailTemplatesTest {

    private static final String PATIENT = "Test Patient";
    private static final String CARETAKER = "Test Caretaker";
    private static final String APP = "Test App";

    private final EmailTemplateService templates = new EmailTemplateService(
            new DefaultResourceLoader(),
            new EmailProperties("noreply@example.org", APP, "*", "", "",
                    new EmailProperties.Templates("classpath:templates/email/"),
                    new EmailProperties.Qrcode("qr-code-email.html")));

    private String render(final String template, final String downloadUrl, final boolean withLogo) {
        return templates.generateQRCodeEmail(
                new QRCodeEmailContent(PATIENT, CARETAKER, APP, "cid:qrcode", downloadUrl, withLogo), template);
    }

    @Test
    void slovenianTemplate_isRenderedInSlovenian() {
        String html = render("qr-code-email.html", null, false);

        assertThat(html)
                .contains("<html lang=\"sl\">")
                .contains("Pozdravljeni, " + PATIENT)
                .contains("<strong>" + CARETAKER + "</strong>")
                .contains("<h1>" + APP + "</h1>")
                .contains("<img src=\"cid:qrcode\"")
                .contains("Navodila za nastavitev");
        assertFilled(html);
    }

    @Test
    void englishTemplate_isRenderedInEnglish() {
        String html = render("qr-code-email-en.html", null, false);

        assertThat(html)
                .contains("<html lang=\"en\">")
                .contains("Dear " + PATIENT)
                .contains("<strong>" + CARETAKER + "</strong>")
                .contains("<h1>" + APP + "</h1>")
                .contains("<img src=\"cid:qrcode\"")
                .contains("Setup Instructions");
        assertFilled(html);
    }

    @Test
    void downloadLinkAndLogo_appearOnlyWhenConfigured() {
        for (String template : new String[]{"qr-code-email.html", "qr-code-email-en.html"}) {
            String plain = render(template, "", false);
            assertThat(plain).doesNotContain("<a ").doesNotContain("cid:logo").doesNotContain("http");

            String branded = render(template, "https://app.example.org/get", true);
            assertThat(branded)
                    .contains("<a href=\"https://app.example.org/get\">https://app.example.org/get</a>")
                    .contains("cid:logo");
            assertFilled(branded);
        }
    }

    @Test
    void templates_carryNoBrandingOrLinksOfTheirOwn() {
        for (String template : new String[]{"qr-code-email.html", "qr-code-email-en.html"}) {
            String html = render(template, null, false);
            assertThat(html.toLowerCase()).doesNotContain("http://", "https://", "result.si", "e-early", "internal");
        }
    }

    private static void assertFilled(final String html) {
        assertThat(Pattern.compile("\\{\\{[A-Z_]+}}").matcher(html).find()).as("unreplaced placeholder").isFalse();
    }
}
