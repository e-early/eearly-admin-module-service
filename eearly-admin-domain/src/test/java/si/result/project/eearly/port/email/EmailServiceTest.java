package si.result.project.eearly.port.email;

import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import si.result.project.eearly.exception.DomainExceptionCode;
import si.result.project.eearly.model.email.QRCodeEmailContent;
import si.result.project.eearly.model.email.command.SendQRCodeEmailCommand;
import si.result.spring.boot.bricks.exception.DomainException;

import java.io.ByteArrayOutputStream;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class EmailServiceTest {

    private static final String PATIENT_EMAIL = "jane.patient@example.com";
    private static final String CARETAKER_EMAIL = "caretaker@clinic.example";

    @Mock
    private JavaMailSender mailSender;

    @Mock
    private EmailTemplateService emailTemplateService;

    @Mock
    private ResourceLoader resourceLoader;

    private EmailService emailService;

    @BeforeEach
    void setUp() {
        emailService = new EmailService(mailSender, emailTemplateService, resourceLoader, properties("*", ""));
        lenient().when(mailSender.createMimeMessage())
                .thenAnswer(invocation -> new JavaMailSenderImpl().createMimeMessage());
        lenient().when(emailTemplateService.generateQRCodeEmail(any(), any())).thenReturn("<p>QR</p>");
    }

    @Test
    void sendQRCodeToPatient_sendsFromConfiguredAddressWithCaretakerAsReplyTo() throws Exception {
        emailService.sendQRCodeToPatient(qrCommand(PATIENT_EMAIL, "sl"));

        MimeMessage message = sentMessage();
        assertEquals("noreply@example.org", ((InternetAddress) message.getFrom()[0]).getAddress());
        assertEquals(CARETAKER_EMAIL, ((InternetAddress) message.getReplyTo()[0]).getAddress());
        assertEquals(PATIENT_EMAIL, ((InternetAddress) message.getAllRecipients()[0]).getAddress());
    }

    @Test
    void sendQRCodeToPatient_withoutCaretakerAddress_hasNoReplyToOfItsOwn() throws Exception {
        emailService.sendQRCodeToPatient(new SendQRCodeEmailCommand(
                "App", PATIENT_EMAIL, "Jane Patient", "Dr. Caretaker", null, null, "sl"));

        MimeMessage message = sentMessage();
        assertEquals("noreply@example.org", ((InternetAddress) message.getReplyTo()[0]).getAddress());
    }

    @Test
    void sendQRCodeToPatient_embedsQrCodeAsInlineImageAndAttachment() throws Exception {
        String png = Base64.getEncoder().encodeToString(new byte[]{(byte) 0x89, 'P', 'N', 'G'});

        emailService.sendQRCodeToPatient(new SendQRCodeEmailCommand(
                "App", PATIENT_EMAIL, "Jane Patient", "Dr. Caretaker", CARETAKER_EMAIL,
                "data:image/png;base64," + png, "sl"));

        ArgumentCaptor<QRCodeEmailContent> content = ArgumentCaptor.forClass(QRCodeEmailContent.class);
        verify(emailTemplateService).generateQRCodeEmail(content.capture(), any());
        assertEquals("cid:qrcode", content.getValue().qrCodeImage());
        assertFalse(content.getValue().withLogo());
        MimeMessage message = sentMessage();
        message.saveChanges();
        ByteArrayOutputStream raw = new ByteArrayOutputStream();
        message.writeTo(raw);
        String text = raw.toString();
        assertTrue(text.contains("Content-ID: <qrcode>"));
        assertTrue(text.contains("qr-code.png"));
    }

    @Test
    void sendQRCodeToPatient_withALogo_embedsItInline() throws Exception {
        emailService = new EmailService(mailSender, emailTemplateService, resourceLoader,
                properties("*", "classpath:logo.png"));
        when(resourceLoader.getResource("classpath:logo.png")).thenReturn(new ByteArrayResource(new byte[]{1, 2, 3}));

        emailService.sendQRCodeToPatient(qrCommand(PATIENT_EMAIL, "sl"));

        ArgumentCaptor<QRCodeEmailContent> content = ArgumentCaptor.forClass(QRCodeEmailContent.class);
        verify(emailTemplateService).generateQRCodeEmail(content.capture(), any());
        assertTrue(content.getValue().withLogo());
        MimeMessage message = sentMessage();
        message.saveChanges();
        ByteArrayOutputStream raw = new ByteArrayOutputStream();
        message.writeTo(raw);
        assertTrue(raw.toString().contains("Content-ID: <logo>"));
    }

    @Test
    void sendQRCodeToPatient_withAMissingLogo_sendsWithoutIt() {
        emailService = new EmailService(mailSender, emailTemplateService, resourceLoader,
                properties("*", "file:/does/not/exist.png"));
        when(resourceLoader.getResource("file:/does/not/exist.png"))
                .thenReturn(new org.springframework.core.io.FileSystemResource("/does/not/exist.png"));

        emailService.sendQRCodeToPatient(qrCommand(PATIENT_EMAIL, "sl"));

        ArgumentCaptor<QRCodeEmailContent> content = ArgumentCaptor.forClass(QRCodeEmailContent.class);
        verify(emailTemplateService).generateQRCodeEmail(content.capture(), any());
        assertFalse(content.getValue().withLogo());
        verify(mailSender).send(any(MimeMessage.class));
    }

    @Test
    void sendQRCodeToPatient_blankAppName_fallsBackToTheConfiguredOne() {
        emailService.sendQRCodeToPatient(new SendQRCodeEmailCommand(
                " ", PATIENT_EMAIL, "Jane Patient", "Dr. Caretaker", CARETAKER_EMAIL, null, "sl"));

        ArgumentCaptor<QRCodeEmailContent> content = ArgumentCaptor.forClass(QRCodeEmailContent.class);
        verify(emailTemplateService).generateQRCodeEmail(content.capture(), any());
        assertEquals("Configured App", content.getValue().appName());
    }

    @Test
    void sendQRCodeToPatient_slovenianPatient_getsTheDefaultTemplateAndASlovenianSubject() throws Exception {
        emailService.sendQRCodeToPatient(qrCommand(PATIENT_EMAIL, "sl"));

        verify(emailTemplateService).generateQRCodeEmail(any(), eq("qr-code-email.html"));
        assertEquals("Vaša QR koda za spremljanje zdravja", sentMessage().getSubject());
    }

    @Test
    void sendQRCodeToPatient_englishPatient_getsTheEnglishTemplateAndSubject() throws Exception {
        emailService.sendQRCodeToPatient(qrCommand(PATIENT_EMAIL, "en"));

        verify(emailTemplateService).generateQRCodeEmail(any(), eq("qr-code-email-en.html"));
        assertEquals("Your Healthcare Monitoring QR Code", sentMessage().getSubject());
    }

    @Test
    void sendQRCodeToPatient_withoutALanguage_isInSlovenian() {
        emailService.sendQRCodeToPatient(qrCommand(PATIENT_EMAIL, null));

        verify(emailTemplateService).generateQRCodeEmail(any(), eq("qr-code-email.html"));
    }

    @Test
    void sendQRCodeToPatient_unsupportedLanguage_isInSlovenian() {
        emailService.sendQRCodeToPatient(qrCommand(PATIENT_EMAIL, "de"));

        verify(emailTemplateService).generateQRCodeEmail(any(), eq("qr-code-email.html"));
    }

    @Test
    void qrCodeTemplate_appendsTheEnglishSuffixEvenWithoutAnHtmlExtension() {
        EmailProperties custom = new EmailProperties("noreply@example.org", "App", "*", "", "",
                new EmailProperties.Templates("classpath:templates/email/"), new EmailProperties.Qrcode("brand-qr"));
        emailService = new EmailService(mailSender, emailTemplateService, resourceLoader, custom);

        assertEquals("brand-qr-en", emailService.qrCodeTemplate("en"));
        assertEquals("brand-qr", emailService.qrCodeTemplate("sl"));
    }

    @Test
    void sendQRCodeToPatient_skipsRecipientOutsideAllowedDomains_withoutLoggingTheAddress(CapturedOutput output) {
        emailService = new EmailService(mailSender, emailTemplateService, resourceLoader,
                properties("clinic.example, early.example", ""));

        emailService.sendQRCodeToPatient(qrCommand(PATIENT_EMAIL, "sl"));

        verify(mailSender, never()).send(any(MimeMessage.class));
        assertThat(output).contains("QR_CODE").contains("not sent");
        assertThat(output).doesNotContain(PATIENT_EMAIL).doesNotContain("example.com");
    }

    @Test
    void isRecipientAllowed_matchesDomainCaseInsensitively() {
        emailService = new EmailService(mailSender, emailTemplateService, resourceLoader,
                properties("clinic.example,early.example", ""));

        assertTrue(emailService.isRecipientAllowed("Someone@Clinic.EXAMPLE"));
        assertTrue(emailService.isRecipientAllowed("a@early.example"));
        assertFalse(emailService.isRecipientAllowed("a@example.com"));
        assertFalse(emailService.isRecipientAllowed("a@sub.clinic.example"));
        assertFalse(emailService.isRecipientAllowed(null));
    }

    @Test
    void isRecipientAllowed_wildcardAllowsAll() {
        assertTrue(emailService.isRecipientAllowed("a@example.com"));
    }

    @Test
    void sendQRCodeToPatient_whenTheServerFails_throwsWithoutCauseAndLogsNoPersonalData(CapturedOutput output) {
        when(mailSender.createMimeMessage()).thenReturn(new JavaMailSenderImpl().createMimeMessage());
        org.mockito.Mockito.doThrow(new MailSendException("550 5.1.1 <" + PATIENT_EMAIL + "> Jane Patient unknown"))
                .when(mailSender).send(any(MimeMessage.class));

        assertThatThrownBy(() -> emailService.sendQRCodeToPatient(qrCommand(PATIENT_EMAIL, "sl")))
                .isInstanceOfSatisfying(DomainException.class, e -> {
                    assertEquals(DomainExceptionCode.EMAIL_SEND_FAILED, e.getCode());
                    assertThat(e.getCause()).isNull();
                });
        assertThat(output).contains("QR_CODE").contains("MailSendException");
        assertThat(output)
                .doesNotContain(PATIENT_EMAIL).doesNotContain("Jane Patient")
                .doesNotContain(CARETAKER_EMAIL).doesNotContain("Dr. Caretaker");
    }

    @Test
    void sendQRCodeToPatient_withAnInvalidImage_failsAsASendFailure() {
        assertThatThrownBy(() -> emailService.sendQRCodeToPatient(new SendQRCodeEmailCommand(
                "App", PATIENT_EMAIL, "Jane Patient", "Dr. Caretaker", CARETAKER_EMAIL, "not base64 !!", "sl")))
                .isInstanceOfSatisfying(DomainException.class,
                        e -> assertEquals(DomainExceptionCode.EMAIL_SEND_FAILED, e.getCode()));
        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    @Test
    void sendQRCodeToPatient_successLogsOnlyTheTypeAndAnId(CapturedOutput output) {
        emailService.sendQRCodeToPatient(qrCommand(PATIENT_EMAIL, "sl"));

        assertThat(output).contains("Email sent: type=QR_CODE, id=");
        assertThat(output)
                .doesNotContain(PATIENT_EMAIL).doesNotContain("Jane Patient").doesNotContain(CARETAKER_EMAIL);
    }

    private MimeMessage sentMessage() {
        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(captor.capture());
        return captor.getValue();
    }

    private static EmailProperties properties(String allowedDomains, String logo) {
        return new EmailProperties("noreply@example.org", "Configured App", allowedDomains, "", logo,
                new EmailProperties.Templates("classpath:templates/email/"),
                new EmailProperties.Qrcode("qr-code-email.html"));
    }

    private static SendQRCodeEmailCommand qrCommand(String patientEmail, String language) {
        return new SendQRCodeEmailCommand(
                "App", patientEmail, "Jane Patient", "Dr. Caretaker", CARETAKER_EMAIL, null, language);
    }
}
