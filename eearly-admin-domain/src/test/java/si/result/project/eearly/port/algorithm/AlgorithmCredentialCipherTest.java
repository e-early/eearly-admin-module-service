package si.result.project.eearly.port.algorithm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.security.SecureRandom;
import java.util.Base64;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import si.result.project.eearly.config.AlgorithmCredentialsProperties;
import si.result.project.eearly.exception.DomainExceptionCode;
import si.result.spring.boot.bricks.exception.DomainException;

class AlgorithmCredentialCipherTest {

    private static String randomKey(final int bytes) {
        final byte[] key = new byte[bytes];
        new SecureRandom().nextBytes(key);
        return Base64.getEncoder().encodeToString(key);
    }

    private static AlgorithmCredentialCipher cipher(final String key) {
        final AlgorithmCredentialsProperties properties = new AlgorithmCredentialsProperties();
        properties.setEncryptionKey(key);
        return new AlgorithmCredentialCipher(properties);
    }

    private static DomainExceptionCode codeOf(final Runnable action) {
        try {
            action.run();
        } catch (DomainException exception) {
            return (DomainExceptionCode) exception.getCode();
        }
        throw new AssertionError("no domain error");
    }

    @Test
    @DisplayName("Round trip, with a value that is not the plaintext and differs on every call")
    void roundTrip() {
        final AlgorithmCredentialCipher cipher = cipher(randomKey(32));
        final String plaintext = "pä55 wörd/Ω " + System.nanoTime();

        final String first = cipher.encrypt(plaintext);
        final String second = cipher.encrypt(plaintext);

        assertThat(cipher.isConfigured()).isTrue();
        assertThat(first).startsWith("v1:").doesNotContain(plaintext).isNotEqualTo(second);
        assertThat(cipher.decrypt(first)).isEqualTo(plaintext);
        assertThat(cipher.decrypt(second)).isEqualTo(plaintext);
    }

    @Test
    void withoutKey_encryptingFailsWithAClearDomainError() {
        for (final String blank : new String[] {null, "", "  "}) {
            final AlgorithmCredentialCipher cipher = cipher(blank);
            assertThat(cipher.isConfigured()).isFalse();
            assertThat(codeOf(() -> cipher.encrypt("x")))
                    .isEqualTo(DomainExceptionCode.ALGORITHM_CREDENTIALS_ENCRYPTION_NOT_CONFIGURED);
            assertThat(codeOf(() -> cipher.decrypt("v1:AAAA")))
                    .isEqualTo(DomainExceptionCode.ALGORITHM_CREDENTIALS_UNAVAILABLE);
        }
    }

    @Test
    void wrongKey_cannotDecrypt() {
        final String stored = cipher(randomKey(32)).encrypt("secret-value");
        final AlgorithmCredentialCipher other = cipher(randomKey(32));

        assertThat(codeOf(() -> other.decrypt(stored))).isEqualTo(DomainExceptionCode.ALGORITHM_CREDENTIALS_UNAVAILABLE);
    }

    @Test
    void tamperedOrForeignValues_areRejected() {
        final AlgorithmCredentialCipher cipher = cipher(randomKey(32));
        final String stored = cipher.encrypt("secret-value");
        final byte[] raw = Base64.getDecoder().decode(stored.substring(3));
        raw[raw.length - 1] ^= 1;
        final String tampered = "v1:" + Base64.getEncoder().encodeToString(raw);

        for (final String bad : new String[] {tampered, "plain-text", "v1:", "v1:@@@", "v1:AAAA", null}) {
            assertThat(codeOf(() -> cipher.decrypt(bad)))
                    .as("value %s", bad)
                    .isEqualTo(DomainExceptionCode.ALGORITHM_CREDENTIALS_UNAVAILABLE);
        }
    }

    @Test
    @DisplayName("A key that is not Base64 of 32 bytes fails startup, without echoing the key")
    void badKey_failsFast() {
        final String notBase64 = "%%% not base64 %%%";
        assertThatThrownBy(() -> cipher(notBase64)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Base64").hasMessageNotContaining(notBase64);
        final String shortKey = randomKey(16);
        assertThatThrownBy(() -> cipher(shortKey)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32 bytes").hasMessageNotContaining(shortKey);
    }

    @Test
    void propertiesPrintNoKey() {
        final AlgorithmCredentialsProperties properties = new AlgorithmCredentialsProperties();
        final String key = randomKey(32);
        properties.setEncryptionKey(key);
        assertThat(properties.toString()).doesNotContain(key);
    }
}
