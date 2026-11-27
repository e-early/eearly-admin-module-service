package si.result.project.eearly.port.email;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import si.result.project.eearly.model.email.command.SendQRCodeEmailCommand;

import java.util.concurrent.CompletableFuture;

/**
 * Sends e-mails on the {@code emailTaskExecutor} thread pool, so the caller is not held up by the mail server.
 *
 * <p>A failure never reaches the caller: it is logged (by {@link EmailService}, without personal data) and the
 * returned future still completes normally.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AsyncEmailService {

    private final EmailService emailService;

    @Async("emailTaskExecutor")
    public CompletableFuture<Void> sendQRCodeEmailAsync(final SendQRCodeEmailCommand command) {
        try {
            emailService.sendQRCodeToPatient(command);
        } catch (Exception e) {
            // EmailService has logged the failure; here the exception, which may quote an address, is dropped.
            log.error("Asynchronous email of type {} was not delivered: {}",
                    EmailService.QR_CODE_MESSAGE_TYPE, e.getClass().getSimpleName());
        }
        return CompletableFuture.completedFuture(null);
    }
}
