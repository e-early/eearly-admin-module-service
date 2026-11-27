package si.result.project.eearly.port.email;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import si.result.project.eearly.exception.DomainExceptionCode;
import si.result.project.eearly.model.email.command.SendQRCodeEmailCommand;
import si.result.spring.boot.bricks.exception.DomainException;

import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class AsyncEmailServiceTest {

    private static final String PATIENT_EMAIL = "jane.patient@example.com";

    @Mock
    private EmailService emailService;

    private final SendQRCodeEmailCommand command = new SendQRCodeEmailCommand(
            "App", PATIENT_EMAIL, "Jane Patient", "Dr. Caretaker", "caretaker@clinic.example", null, "sl");

    @Test
    void sendQRCodeEmailAsync_delegatesAndCompletes() {
        CompletableFuture<Void> result = new AsyncEmailService(emailService).sendQRCodeEmailAsync(command);

        verify(emailService).sendQRCodeToPatient(command);
        assertThat(result).isCompleted();
    }

    @Test
    void sendQRCodeEmailAsync_aSendFailureIsSwallowedAndLoggedWithoutPersonalData(CapturedOutput output) {
        doThrow(new DomainException(DomainExceptionCode.EMAIL_SEND_FAILED)).when(emailService).sendQRCodeToPatient(command);

        CompletableFuture<Void> result = new AsyncEmailService(emailService).sendQRCodeEmailAsync(command);

        assertThat(result).isCompleted();
        assertThat(output).contains("QR_CODE").contains("DomainException");
        assertThat(output).doesNotContain(PATIENT_EMAIL).doesNotContain("Jane Patient").doesNotContain("Dr. Caretaker");
    }

    @Test
    void sendQRCodeEmailAsync_anUnexpectedExceptionQuotingTheAddressIsNeverLogged(CapturedOutput output) {
        doThrow(new IllegalStateException("cannot deliver to " + PATIENT_EMAIL + " (Jane Patient)"))
                .when(emailService).sendQRCodeToPatient(command);

        CompletableFuture<Void> result = new AsyncEmailService(emailService).sendQRCodeEmailAsync(command);

        assertThat(result).isCompleted();
        assertThat(output).contains("IllegalStateException");
        assertThat(output).doesNotContain(PATIENT_EMAIL).doesNotContain("Jane Patient");
    }
}
