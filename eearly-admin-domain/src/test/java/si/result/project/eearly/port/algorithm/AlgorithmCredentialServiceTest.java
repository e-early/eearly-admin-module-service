package si.result.project.eearly.port.algorithm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.security.SecureRandom;
import java.util.Base64;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import si.result.project.eearly.config.AlgorithmCredentialsProperties;
import si.result.project.eearly.exception.DomainExceptionCode;
import si.result.project.eearly.model.algorithm.Algorithm;
import si.result.spring.boot.bricks.exception.DomainException;

class AlgorithmCredentialServiceTest {

    private static AlgorithmCredentialCipher cipher(final boolean configured) {
        final AlgorithmCredentialsProperties properties = new AlgorithmCredentialsProperties();
        if (configured) {
            final byte[] key = new byte[32];
            new SecureRandom().nextBytes(key);
            properties.setEncryptionKey(Base64.getEncoder().encodeToString(key));
        }
        return new AlgorithmCredentialCipher(properties);
    }

    private static Algorithm algorithm(final String username, final String storedPassword) {
        final Algorithm algorithm = mock(Algorithm.class);
        when(algorithm.getAuthUsername()).thenReturn(username);
        when(algorithm.getAuthPassword()).thenReturn(storedPassword);
        return algorithm;
    }

    @Test
    void resolveUsername_nullKeepsBlankClearsOtherwiseTrims() {
        final AlgorithmCredentialService service = new AlgorithmCredentialService(cipher(true));

        assertThat(service.resolveUsername(null, "stored")).isEqualTo("stored");
        assertThat(service.resolveUsername("  ", "stored")).isNull();
        assertThat(service.resolveUsername("", "stored")).isNull();
        assertThat(service.resolveUsername(" new ", "stored")).isEqualTo("new");
    }

    @Test
    @DisplayName("The submitted password is stored as ciphertext and resolves back to the plaintext")
    void protectThenResolve_roundTrip() {
        final AlgorithmCredentialService service = new AlgorithmCredentialService(cipher(true));
        final String password = "pw-" + System.nanoTime();

        final String stored = service.protectPassword(password, null, "user");

        assertThat(stored).isNotEqualTo(password).doesNotContain(password);
        assertThat(AlgorithmCredentialService.hasPassword(algorithm("user", stored))).isTrue();
        assertThat(service.resolve(algorithm("user", stored)))
                .hasValueSatisfying(c -> {
                    assertThat(c.username()).isEqualTo("user");
                    assertThat(c.password()).isEqualTo(password);
                    assertThat(c.toString()).doesNotContain(password);
                });
    }

    @Test
    void protectPassword_nullKeepsEmptyClears() {
        final AlgorithmCredentialService service = new AlgorithmCredentialService(cipher(true));

        assertThat(service.protectPassword(null, "stored-cipher", "user")).isEqualTo("stored-cipher");
        assertThat(service.protectPassword("", "stored-cipher", "user")).isNull();
    }

    @Test
    @DisplayName("Without a username no password is kept")
    void noUsername_dropsThePassword() {
        final AlgorithmCredentialService service = new AlgorithmCredentialService(cipher(true));

        assertThat(service.protectPassword("pw", "stored-cipher", null)).isNull();
        assertThat(service.protectPassword("pw", "stored-cipher", " ")).isNull();
    }

    @Test
    void tooLongPassword_isRefused() {
        final AlgorithmCredentialService service = new AlgorithmCredentialService(cipher(true));

        assertThat(service.protectPassword("x".repeat(512), null, "user")).isNotNull();
        assertThatThrownBy(() -> service.protectPassword("x".repeat(513), null, "user"))
                .isInstanceOf(DomainException.class)
                .satisfies(e -> assertThat(((DomainException) e).getCode())
                        .isEqualTo(DomainExceptionCode.ALGORITHM_CREDENTIALS_PASSWORD_TOO_LONG));
    }

    @Test
    @DisplayName("Without an encryption key a password cannot be set, with a clear domain error")
    void withoutKey_settingAPasswordFails() {
        final AlgorithmCredentialService service = new AlgorithmCredentialService(cipher(false));

        assertThatThrownBy(() -> service.protectPassword("pw", null, "user"))
                .isInstanceOf(DomainException.class)
                .satisfies(e -> assertThat(((DomainException) e).getCode())
                        .isEqualTo(DomainExceptionCode.ALGORITHM_CREDENTIALS_ENCRYPTION_NOT_CONFIGURED));
        // keeping what is stored, or clearing it, needs no key
        assertThat(service.protectPassword(null, "stored", "user")).isEqualTo("stored");
        assertThat(service.protectPassword("", "stored", "user")).isNull();
    }

    @Test
    @DisplayName("Without a key a stored password cannot be used, and nothing else is substituted")
    void withoutKey_storedPasswordIsUnavailable() {
        final String stored = new AlgorithmCredentialService(cipher(true)).protectPassword("pw", null, "user");
        final AlgorithmCredentialService service = new AlgorithmCredentialService(cipher(false));

        assertThatThrownBy(() -> service.resolve(algorithm("user", stored)))
                .isInstanceOf(DomainException.class)
                .satisfies(e -> assertThat(((DomainException) e).getCode())
                        .isEqualTo(DomainExceptionCode.ALGORITHM_CREDENTIALS_UNAVAILABLE));
    }

    @Test
    void resolve_withoutUsernameMeansGlobalCredentials() {
        final AlgorithmCredentialService service = new AlgorithmCredentialService(cipher(true));

        assertThat(service.resolve(null)).isEmpty();
        assertThat(service.resolve(algorithm(null, null))).isEmpty();
        assertThat(service.resolve(algorithm(" ", "x"))).isEmpty();
        assertThat(AlgorithmCredentialService.hasPassword(null)).isFalse();
        assertThat(AlgorithmCredentialService.hasPassword(algorithm("user", ""))).isFalse();
    }

    @Test
    void usernameOnly_resolvesWithAnEmptyPassword() {
        final AlgorithmCredentialService service = new AlgorithmCredentialService(cipher(true));

        assertThat(service.resolve(algorithm("user", null))).hasValueSatisfying(c -> assertThat(c.password()).isEmpty());
    }
}
