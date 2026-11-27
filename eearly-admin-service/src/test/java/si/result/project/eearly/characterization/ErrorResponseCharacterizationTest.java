package si.result.project.eearly.characterization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.http.MediaType;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import si.result.project.eearly.exception.DomainExceptionCode;
import si.result.spring.boot.bricks.advice.ErrorResponseHandler;
import si.result.spring.boot.bricks.exception.DomainException;
import si.result.spring.boot.bricks.exception.ExceptionCode;
import si.result.spring.boot.bricks.exception.ExceptionReason;

/**
 * Characterization of the REST error contract produced by the internal library
 * {@code si.result.lib:spring-boot-bricks} ({@link ErrorResponseHandler}, {@code ErrorDTO},
 * {@link DomainException}). Pins HTTP status and the exact JSON body clients see today.
 *
 * Uses standalone MockMvc with a test-only controller; the production controllers are not involved.
 */
class ErrorResponseCharacterizationTest {

    /** Test-only code: no DomainExceptionCode uses UNAUTHORIZED or message placeholders. */
    enum TestCode implements ExceptionCode {
        FORBIDDEN_FOR_TEST("Forbidden for test", ExceptionReason.UNAUTHORIZED),
        NOT_FOUND_WITH_ARGS("{0} with id {1} not found", ExceptionReason.ENTITY_NOT_FOUND);

        private final String message;
        private final ExceptionReason reason;

        TestCode(String message, ExceptionReason reason) {
            this.message = message;
            this.reason = reason;
        }

        @Override
        public String getMessage() {
            return message;
        }

        @Override
        public ExceptionReason getReason() {
            return reason;
        }
    }

    record Body(String name) {
    }

    @RestController
    static class ThrowingController {

        @GetMapping("/domain/{code}")
        String domain(@PathVariable DomainExceptionCode code) {
            throw new DomainException(code);
        }

        @GetMapping("/test-code/{code}")
        String testCode(@PathVariable TestCode code) {
            throw new DomainException(code);
        }

        @GetMapping("/with-args")
        String withArgs() {
            throw new DomainException(TestCode.NOT_FOUND_WITH_ARGS, "Patient", "42");
        }

        @GetMapping("/with-too-few-args")
        String withTooFewArgs() {
            throw new DomainException(TestCode.NOT_FOUND_WITH_ARGS, "Patient");
        }

        @GetMapping("/denied")
        String denied() {
            throw new AuthorizationDeniedException("Access Denied", new AuthorizationDecision(false));
        }

        @GetMapping("/uuid/{id}")
        String uuid(@PathVariable UUID id) {
            return id.toString();
        }

        @GetMapping("/required-param")
        String requiredParam(@RequestParam String q) {
            return q;
        }

        @PostMapping(value = "/body", consumes = "application/json")
        String body(@RequestBody Body body) {
            return body.name();
        }

        @GetMapping("/boom")
        String boom() {
            throw new RuntimeException("internal detail");
        }

        @GetMapping("/boom-illegal-argument")
        String boomIllegalArgument() {
            throw new IllegalArgumentException("internal detail");
        }

        @GetMapping("/boom-checked")
        String boomChecked() throws Exception {
            throw new Exception("internal detail");
        }
    }

    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new ThrowingController())
            .setControllerAdvice(new ErrorResponseHandler())
            .build();

    private void assertError(org.springframework.test.web.servlet.RequestBuilder request, int status, String json)
            throws Exception {
        mvc.perform(request)
                .andExpect(status().is(status))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().json(json, JsonCompareMode.STRICT));
    }

    // ---- DomainException: one case per ExceptionReason --------------------------------------

    @Test
    void domainExceptionEntityNotFoundIs404() throws Exception {
        assertError(get("/domain/PATIENT_NOT_FOUND"), 404,
                "{\"code\":\"PATIENT_NOT_FOUND\",\"message\":\"Patient not found\"}");
    }

    @Test
    void domainExceptionIllegalArgumentIs400() throws Exception {
        assertError(get("/domain/PATIENT_ID_NOT_PROVIDED"), 400,
                "{\"code\":\"PATIENT_ID_NOT_PROVIDED\",\"message\":\"Patient ID must be provided\"}");
    }

    @Test
    void domainExceptionIllegalStateIs400() throws Exception {
        assertError(get("/domain/ALGORITHM_NOT_ACTIVE"), 400,
                "{\"code\":\"ALGORITHM_NOT_ACTIVE\",\"message\":\"Algorithm must be ACTIVE to launch execution\"}");
    }

    @Test
    void domainExceptionUnauthorizedIs403() throws Exception {
        assertError(get("/test-code/FORBIDDEN_FOR_TEST"), 403,
                "{\"code\":\"FORBIDDEN_FOR_TEST\",\"message\":\"Forbidden for test\"}");
    }

    @Test
    void domainExceptionRuntimeIs500() throws Exception {
        assertError(get("/domain/ALGORITHM_SERVICE_HEALTHCHECK_FAILED"), 500,
                "{\"code\":\"ALGORITHM_SERVICE_HEALTHCHECK_FAILED\",\"message\":\"Algorithm service health check failed\"}");
    }

    /** Every real code: status is derived from its reason only; body is {code: enum name, message}. */
    @ParameterizedTest
    @EnumSource(DomainExceptionCode.class)
    void everyDomainExceptionCodeMapsReasonToStatus(DomainExceptionCode code) throws Exception {
        int expectedStatus = switch (code.getReason()) {
            case ENTITY_NOT_FOUND -> 404;
            case ILLEGAL_ARGUMENT, ILLEGAL_STATE -> 400;
            case UNAUTHORIZED -> 403;
            case RUNTIME -> 500;
        };
        mvc.perform(get("/domain/" + code.name()))
                .andExpect(status().is(expectedStatus))
                .andExpect(content().json(
                        "{\"code\":\"" + code.name() + "\",\"message\":\"" + code.getMessage() + "\"}",
                        JsonCompareMode.STRICT));
    }

    // ---- DomainException with message arguments ---------------------------------------------

    @Test
    void domainExceptionReplacesNumberedPlaceholdersWithArguments() throws Exception {
        assertError(get("/with-args"), 404,
                "{\"code\":\"NOT_FOUND_WITH_ARGS\",\"message\":\"Patient with id 42 not found\"}");
    }

    /** Placeholders without a matching argument are left in the message verbatim. */
    @Test
    void currentDefectMissingArgumentLeavesRawPlaceholderInMessage() throws Exception {
        // Defect-like: a client would see "{1}" in the message when a caller passes too few arguments.
        assertError(get("/with-too-few-args"), 404,
                "{\"code\":\"NOT_FOUND_WITH_ARGS\",\"message\":\"Patient with id {1} not found\"}");
    }

    // ---- Spring / framework exceptions ------------------------------------------------------

    @Test
    void authorizationDeniedIs403AccessDeniedWithExceptionMessage() throws Exception {
        assertError(get("/denied"), 403, "{\"code\":\"ACCESS_DENIED\",\"message\":\"Access Denied\"}");
    }

    @Test
    void typeMismatchOnPathVariableIs400InvalidArgument() throws Exception {
        assertError(get("/uuid/not-a-uuid"), 400,
                "{\"code\":\"INVALID_ARGUMENT\",\"message\":\"Invalid argument\"}");
    }

    @Test
    void missingRequiredRequestParameterIs400InvalidArgumentWithoutParameterName() throws Exception {
        assertError(get("/required-param"), 400,
                "{\"code\":\"INVALID_ARGUMENT\",\"message\":\"Invalid argument\"}");
    }

    @Test
    void unreadableJsonBodyIs400MessageNotReadable() throws Exception {
        assertError(post("/body").contentType(MediaType.APPLICATION_JSON).content("{not json"), 400,
                "{\"code\":\"MESSAGE_NOT_READABLE\",\"message\":\"Message not readable\"}");
    }

    @Test
    void missingJsonBodyIs400MessageNotReadable() throws Exception {
        assertError(post("/body").contentType(MediaType.APPLICATION_JSON), 400,
                "{\"code\":\"MESSAGE_NOT_READABLE\",\"message\":\"Message not readable\"}");
    }

    // ---- Unexpected exceptions --------------------------------------------------------------

    @Test
    void unexpectedRuntimeExceptionIs500WithGenericMessageAndDoesNotLeakDetail() throws Exception {
        String body = mvc.perform(get("/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().json(
                        "{\"code\":\"UNKNOWN_EXCEPTION\",\"message\":\"Unexpected error\"}", JsonCompareMode.STRICT))
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("internal detail").doesNotContain("RuntimeException");
    }

    @Test
    void unexpectedIllegalArgumentExceptionIs500NotBadRequest() throws Exception {
        assertError(get("/boom-illegal-argument"), 500,
                "{\"code\":\"UNKNOWN_EXCEPTION\",\"message\":\"Unexpected error\"}");
    }

    @Test
    void unexpectedCheckedExceptionIs500() throws Exception {
        assertError(get("/boom-checked"), 500,
                "{\"code\":\"UNKNOWN_EXCEPTION\",\"message\":\"Unexpected error\"}");
    }

    // ---- Current defects: the catch-all swallows framework HTTP errors ----------------------

    /** Defect: wrong HTTP method should be 405, but the catch-all Exception handler turns it into 500. */
    @Test
    void currentDefectUnsupportedHttpMethodIs500InsteadOf405() throws Exception {
        assertError(post("/boom"), 500, "{\"code\":\"UNKNOWN_EXCEPTION\",\"message\":\"Unexpected error\"}");
    }

    /** Defect: unsupported Content-Type should be 415, but the catch-all handler turns it into 500. */
    @Test
    void currentDefectUnsupportedMediaTypeIs500InsteadOf415() throws Exception {
        assertError(post("/body").contentType(MediaType.TEXT_PLAIN).content("x"), 500,
                "{\"code\":\"UNKNOWN_EXCEPTION\",\"message\":\"Unexpected error\"}");
    }
}
