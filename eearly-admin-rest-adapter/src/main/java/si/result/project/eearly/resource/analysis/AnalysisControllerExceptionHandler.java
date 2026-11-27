package si.result.project.eearly.resource.analysis;

import java.util.Map;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import si.result.project.eearly.exception.DomainExceptionCode;
import si.result.project.eearly.exception.CallbackTokenRejectedException;

@Slf4j
@RestControllerAdvice(assignableTypes = AnalysisController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AnalysisControllerExceptionHandler {

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Map<String, String>> handleMissingRequestParameter(
            MissingServletRequestParameterException ex) {
        return badRequest(
                DomainExceptionCode.ANALYSIS_FEEDBACK_PARAMETER_MISSING,
                "Required request parameter '" + ex.getParameterName() + "' is missing"
        );
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, String>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return badRequest(
                DomainExceptionCode.ANALYSIS_FEEDBACK_PARAMETER_INVALID,
                "Invalid value for request parameter '" + ex.getName() + "'"
        );
    }

    /**
     * A callback token the facade refused (used before, issued for another analysis, ...). Every refusal answers
     * the same, 401, so the caller learns nothing about the reason, which only the log has.
     */
    @ExceptionHandler(CallbackTokenRejectedException.class)
    public ResponseEntity<Map<String, String>> handleRejectedCallbackToken(CallbackTokenRejectedException ex) {
        log.warn("Result callback refused: callback token rejected ({})", ex.getReason());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .header("WWW-Authenticate", "Bearer")
                .body(Map.of(
                        "code", DomainExceptionCode.CALLBACK_TOKEN_INVALID.name(),
                        "message", CallbackTokenRejectedException.MESSAGE
                ));
    }

    private ResponseEntity<Map<String, String>> badRequest(final DomainExceptionCode code,
                                                           final String message) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                "code", code.name(),
                "message", message
        ));
    }
}
