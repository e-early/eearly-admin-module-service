package si.result.project.eearly.resource.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import si.result.project.eearly.exception.CallbackTokenRejectedException;
import si.result.project.eearly.exception.CallbackTokenRejectedException.Reason;

class AnalysisControllerCallbackTokenHandlerTest {

    private final AnalysisControllerExceptionHandler handler = new AnalysisControllerExceptionHandler();

    @Test
    void everyReasonAnswersTheSame401() {
        for (final Reason reason : Reason.values()) {
            final var response = handler.handleRejectedCallbackToken(new CallbackTokenRejectedException(reason));

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
            assertThat(response.getHeaders().getFirst("WWW-Authenticate")).isEqualTo("Bearer");
            assertThat(response.getBody()).isEqualTo(Map.of(
                    "code", "CALLBACK_TOKEN_INVALID", "message", "Callback token is not valid"));
        }
    }
}
