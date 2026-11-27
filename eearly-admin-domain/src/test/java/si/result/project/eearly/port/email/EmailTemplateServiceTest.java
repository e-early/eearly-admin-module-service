package si.result.project.eearly.port.email;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.DefaultResourceLoader;
import si.result.project.eearly.exception.DomainExceptionCode;
import si.result.project.eearly.model.email.QRCodeEmailContent;
import si.result.spring.boot.bricks.exception.DomainException;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;

class EmailTemplateServiceTest {

    private static final String TEMPLATE = "<h1>{{APP_NAME}}</h1><p>{{PATIENT_NAME}} / {{CARETAKER_NAME}}</p>"
            + "<img src=\"{{QR_CODE_IMAGE}}\">{{LOGO}}<i>{{APP_DOWNLOAD_LINK}}</i>{{UNKNOWN}}";

    @TempDir
    Path templates;

    private EmailTemplateService service;

    @BeforeEach
    void setUp() throws Exception {
        Files.writeString(templates.resolve("test.html"), TEMPLATE, StandardCharsets.UTF_8);
        service = serviceFor(templates.toUri().toString());
    }

    @Test
    void rendersEveryPlaceholder() {
        String html = service.generateQRCodeEmail(
                new QRCodeEmailContent("Test Patient", "Test Caretaker", "Test App", "cid:qrcode",
                        "https://app.example.org/download", true), "test.html");

        assertThat(html)
                .contains("<h1>Test App</h1>")
                .contains("Test Patient / Test Caretaker")
                .contains("<img src=\"cid:qrcode\">")
                .contains("<img src=\"cid:logo\"")
                .contains("(<a href=\"https://app.example.org/download\">https://app.example.org/download</a>)")
                .contains("{{UNKNOWN}}");
    }

    @Test
    void optionalPartsAreEmptyWhenNotGiven() {
        String html = service.generateQRCodeEmail(
                new QRCodeEmailContent("P", "C", "A", null, null, false), "test.html");

        assertThat(html).contains("<img src=\"\">").contains("<i></i>").doesNotContain("cid:logo");
    }

    @Test
    void valuesAreHtmlEscapedAndCannotInjectPlaceholders() {
        String html = service.generateQRCodeEmail(
                new QRCodeEmailContent("<script>x</script> {{CARETAKER_NAME}}", "O'Neil & Co", "A", null, null, false),
                "test.html");

        assertThat(html)
                .doesNotContain("<script>")
                .contains("&lt;script&gt;x&lt;/script&gt; {{CARETAKER_NAME}}")
                .contains("O&#39;Neil &amp; Co");
    }

    @Test
    void onlyHttpLinksAreRendered() {
        for (String url : new String[]{"javascript:alert(1)", "ftp://example.org/app", "not a url", "/relative"}) {
            String html = service.generateQRCodeEmail(
                    new QRCodeEmailContent("P", "C", "A", null, url, false), "test.html");
            assertThat(html).contains("<i></i>").doesNotContain("<a href");
        }
    }

    @Test
    void templatesPathMayOmitTheTrailingSlash() {
        String withoutSlash = templates.toUri().toString().replaceAll("/$", "");

        String html = serviceFor(withoutSlash).generateQRCodeEmail(
                new QRCodeEmailContent("P", "C", "A", null, null, false), "test.html");

        assertThat(html).contains("<h1>A</h1>");
    }

    @Test
    void missingTemplate_isReportedAsNotFound() {
        assertThatThrownBy(() -> service.generateQRCodeEmail(
                new QRCodeEmailContent("P", "C", "A", null, null, false), "missing.html"))
                .isInstanceOfSatisfying(DomainException.class,
                        e -> assertEquals(DomainExceptionCode.EMAIL_TEMPLATE_NOT_FOUND, e.getCode()));
    }

    private static EmailTemplateService serviceFor(String path) {
        EmailProperties properties = new EmailProperties("noreply@example.org", "App", "*", "", "",
                new EmailProperties.Templates(path), new EmailProperties.Qrcode("test.html"));
        return new EmailTemplateService(new DefaultResourceLoader(), properties);
    }
}
