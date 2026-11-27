package si.result.project.eearly.port.email;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Settings of the e-mails the service sends (prefix {@code email}). The SMTP connection itself is configured with the
 * standard {@code spring.mail.*} properties.
 *
 * @param from                    sender address of every message
 * @param appName                 application name shown as the sender name and in the message text, unless the
 *                                request supplies its own
 * @param allowedRecipientDomains comma-separated recipient domains; messages to any other domain are skipped.
 *                                {@code *} allows every domain
 * @param appDownloadUrl          optional link to the mobile application, shown in the QR code message; blank omits it
 * @param logo                    resource location of the logo shown in the messages (for example
 *                                {@code classpath:images/email-logo.png} or {@code file:/etc/app/logo.png}); blank or
 *                                missing sends the messages without a logo
 * @param templates               where the templates are loaded from
 * @param qrcode                  the QR code message
 */
@ConfigurationProperties(prefix = "email")
public record EmailProperties(
        @DefaultValue("noreply@example.org") String from,
        @DefaultValue("eEarly") String appName,
        @DefaultValue("*") String allowedRecipientDomains,
        @DefaultValue("") String appDownloadUrl,
        @DefaultValue("classpath:images/email-logo.png") String logo,
        @DefaultValue Templates templates,
        @DefaultValue Qrcode qrcode) {

    /**
     * @param path resource folder of the templates, for example {@code classpath:templates/email/} or
     *             {@code file:/etc/app/email-templates/}
     */
    public record Templates(@DefaultValue("classpath:templates/email/") String path) {
    }

    /**
     * @param template file name of the default-language (Slovenian) template; the English variant is the same name
     *                 with an {@code -en} suffix before the extension
     */
    public record Qrcode(@DefaultValue("qr-code-email.html") String template) {
    }
}
