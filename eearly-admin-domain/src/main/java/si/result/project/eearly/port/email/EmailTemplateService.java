package si.result.project.eearly.port.email;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;
import si.result.project.eearly.exception.DomainExceptionCode;
import si.result.project.eearly.model.email.QRCodeEmailContent;
import si.result.spring.boot.bricks.exception.DomainException;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Renders the HTML templates of the e-mails. A template is an HTML file with {@code {{NAME}}} placeholders, loaded
 * from {@code email.templates.path} so a deployment can brand it. Values are HTML-escaped and substituted in a single
 * pass, so a value can never introduce another placeholder or markup. Unknown placeholders are left as they are.
 *
 * <p>QR code template placeholders: {@code APP_NAME}, {@code PATIENT_NAME}, {@code CARETAKER_NAME},
 * {@code QR_CODE_IMAGE} (image source), {@code APP_DOWNLOAD_LINK} (empty, or a link to the application in
 * parentheses, with a leading space) and {@code LOGO} (empty, or the logo image).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailTemplateService {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{([A-Z_]+)}}");

    private final ResourceLoader resourceLoader;
    private final EmailProperties properties;

    public String generateQRCodeEmail(final QRCodeEmailContent content, final String templateName) {
        final String template = loadTemplate(templateName);
        final String downloadUrl = safeHttpUrl(content.appDownloadUrl());

        final Map<String, String> variables = Map.of(
                "APP_NAME", escape(content.appName()),
                "PATIENT_NAME", escape(content.patientName()),
                "CARETAKER_NAME", escape(content.caretakerName()),
                "QR_CODE_IMAGE", escape(content.qrCodeImage()),
                "APP_DOWNLOAD_LINK", downloadUrl == null
                        ? ""
                        : " (<a href=\"" + escape(downloadUrl) + "\">" + escape(downloadUrl) + "</a>)",
                "LOGO", content.withLogo()
                        ? "<img src=\"cid:" + EmailService.LOGO_CONTENT_ID
                                + "\" alt=\"\" style=\"max-height: 64px; margin-bottom: 12px;\" />"
                        : "");
        return replaceVariables(template, variables);
    }

    private String loadTemplate(final String templateName) {
        try {
            final Resource resource = resourceLoader.getResource(templatesPath() + templateName);
            if (!resource.exists()) {
                log.error("Email template {} not found", templateName);
                throw new DomainException(DomainExceptionCode.EMAIL_TEMPLATE_NOT_FOUND);
            }
            return resource.getContentAsString(StandardCharsets.UTF_8);
        } catch (DomainException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to load email template {}: {}", templateName, e.getClass().getSimpleName());
            throw new DomainException(DomainExceptionCode.EMAIL_TEMPLATE_LOAD_FAILED);
        }
    }

    private String templatesPath() {
        final String path = properties.templates().path();
        return path.endsWith("/") ? path : path + "/";
    }

    private static String replaceVariables(final String template, final Map<String, String> variables) {
        final Matcher matcher = PLACEHOLDER.matcher(template);
        final StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            final String value = variables.get(matcher.group(1));
            matcher.appendReplacement(result, Matcher.quoteReplacement(value != null ? value : matcher.group()));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private static String escape(final String value) {
        return value == null ? "" : HtmlUtils.htmlEscape(value);
    }

    /** The URL if it is an absolute http(s) URL, otherwise null (rules out javascript: and similar links). */
    private static String safeHttpUrl(final String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        try {
            final URI uri = URI.create(url.trim());
            final String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            return ("http".equals(scheme) || "https".equals(scheme)) && uri.getHost() != null ? uri.toString() : null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
