package si.result.project.eearly.exception;

/**
 * A callback token was refused. Every refusal carries the same message, so a caller learns nothing about why;
 * {@link #getReason()} is for the log and for tests only and holds no token.
 */
public class CallbackTokenRejectedException extends RuntimeException {

    /** Why the token was refused. */
    public enum Reason {
        /** Callback tokens are not enabled. */
        DISABLED,
        /** Missing, not in the expected format, or not decodable. */
        MALFORMED,
        /** The signature does not match. */
        SIGNATURE,
        /** Past its expiry. */
        EXPIRED,
        /** Already used once. */
        ALREADY_USED,
        /** Not the token currently issued for the execution. */
        SUPERSEDED,
        /** The execution is unknown. */
        UNKNOWN_EXECUTION,
        /** Issued for a different analysis than the one the request is for. */
        ANALYSIS_MISMATCH,
        /** The execution has already ended (completed, failed or cancelled). */
        EXECUTION_NOT_ACTIVE
    }

    /** The only message a refusal ever has. */
    public static final String MESSAGE = "Callback token is not valid";

    private final Reason reason;

    public CallbackTokenRejectedException(final Reason reason) {
        super(MESSAGE);
        this.reason = reason;
    }

    public Reason getReason() {
        return reason;
    }
}
