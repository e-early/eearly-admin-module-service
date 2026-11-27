package si.result.project.eearly.port.algorithm;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import si.result.project.eearly.exception.DomainExceptionCode;
import si.result.project.eearly.model.algorithm.Algorithm;
import si.result.spring.boot.bricks.exception.DomainException;

/**
 * The Basic Auth credentials of an algorithm's own service endpoint: turns what an administrator submits into what is
 * stored (the password encrypted by {@link AlgorithmCredentialCipher}) and, for the HTTP calls to the algorithm, back
 * into the credentials to use. Without credentials on the algorithm the global ones of the HTTP client apply.
 */
@Service
@RequiredArgsConstructor
public class AlgorithmCredentialService {

    /** Longest accepted password, in characters. */
    public static final int MAX_PASSWORD_LENGTH = 512;

    private final AlgorithmCredentialCipher cipher;

    /**
     * The username to store. {@code null} leaves the stored one unchanged; blank removes the credentials.
     */
    public String resolveUsername(final String submittedUsername, final String storedUsername) {
        if (submittedUsername == null) {
            return storedUsername;
        }
        return submittedUsername.isBlank() ? null : submittedUsername.trim();
    }

    /**
     * The value to store in the password column: the encrypted password.
     *
     * @param submittedPassword the password as submitted: {@code null} keeps the stored one, empty removes it
     * @param storedPassword    the stored (encrypted) value, or {@code null}
     * @param resultingUsername the username that results from {@link #resolveUsername}; without one there is no
     *                          password either
     * @throws DomainException if a password is submitted while no encryption key is configured, or if it is too long
     */
    public String protectPassword(
            final String submittedPassword, final String storedPassword, final String resultingUsername) {
        if (resultingUsername == null || resultingUsername.isBlank()) {
            return null;
        }
        if (submittedPassword == null) {
            return storedPassword;
        }
        if (submittedPassword.isEmpty()) {
            return null;
        }
        if (submittedPassword.length() > MAX_PASSWORD_LENGTH) {
            throw new DomainException(DomainExceptionCode.ALGORITHM_CREDENTIALS_PASSWORD_TOO_LONG);
        }
        return cipher.encrypt(submittedPassword);
    }

    /**
     * The credentials to call the algorithm with, if it has its own.
     *
     * @throws DomainException ALGORITHM_CREDENTIALS_UNAVAILABLE if it has a stored password that cannot be
     *                         decrypted. The global credentials are not substituted: they belong to another
     *                         endpoint and must not be sent to this one.
     */
    public Optional<AlgorithmCredentials> resolve(final Algorithm algorithm) {
        if (algorithm == null || algorithm.getAuthUsername() == null || algorithm.getAuthUsername().isBlank()) {
            return Optional.empty();
        }
        final String stored = algorithm.getAuthPassword();
        final String password = stored == null || stored.isEmpty() ? "" : cipher.decrypt(stored);
        return Optional.of(new AlgorithmCredentials(algorithm.getAuthUsername(), password));
    }

    /** Whether a password is stored for the algorithm (never the password itself). */
    public static boolean hasPassword(final Algorithm algorithm) {
        return algorithm != null && algorithm.getAuthPassword() != null && !algorithm.getAuthPassword().isEmpty();
    }

    /** Username and password to authenticate with. The password is not part of {@code toString()}. */
    public record AlgorithmCredentials(String username, String password) {
        @Override
        public String toString() {
            return "AlgorithmCredentials(username=" + username + ", password=***)";
        }
    }
}
