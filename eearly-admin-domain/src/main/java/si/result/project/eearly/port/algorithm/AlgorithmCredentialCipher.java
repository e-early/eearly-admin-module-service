package si.result.project.eearly.port.algorithm;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import si.result.project.eearly.config.AlgorithmCredentialsProperties;
import si.result.project.eearly.exception.DomainExceptionCode;
import si.result.spring.boot.bricks.exception.DomainException;

/**
 * Encrypts the passwords of the algorithms at rest with AES-256-GCM under the key {@code
 * algorithm.credentials.encryption-key} (Base64 of 32 bytes). A stored value is {@code v1:} and the Base64 of the
 * random 12-byte nonce followed by the ciphertext and the authentication tag, so every encryption of the same
 * password differs and a value changed in the database fails to decrypt instead of yielding another password.
 *
 * <p>Without a key nothing can be encrypted or decrypted: setting a password fails with
 * {@link DomainExceptionCode#ALGORITHM_CREDENTIALS_ENCRYPTION_NOT_CONFIGURED} and a stored one cannot be used
 * ({@link DomainExceptionCode#ALGORITHM_CREDENTIALS_UNAVAILABLE}). A key that is set but is not 32 bytes of Base64
 * fails the start of the application. Neither the key nor any password is ever logged or put in a message.
 */
@Slf4j
@Component
public class AlgorithmCredentialCipher {

    static final String PREFIX = "v1:";
    private static final int KEY_BYTES = 32;
    private static final int NONCE_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final byte[] ASSOCIATED_DATA = "eearly.algorithm.auth-password.v1".getBytes(StandardCharsets.UTF_8);

    private final SecureRandom random = new SecureRandom();
    private final SecretKeySpec key;

    public AlgorithmCredentialCipher(final AlgorithmCredentialsProperties properties) {
        final String configured = properties.getEncryptionKey();
        if (configured == null || configured.isBlank()) {
            this.key = null;
            log.info("Algorithm passwords cannot be stored: algorithm.credentials.encryption-key is not set");
            return;
        }
        final byte[] keyBytes;
        try {
            keyBytes = Base64.getDecoder().decode(configured.trim());
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException(
                    "algorithm.credentials.encryption-key must be Base64 (for example `openssl rand -base64 32`)");
        }
        if (keyBytes.length != KEY_BYTES) {
            throw new IllegalStateException("algorithm.credentials.encryption-key must be Base64 of exactly "
                    + KEY_BYTES + " bytes (for example `openssl rand -base64 32`)");
        }
        this.key = new SecretKeySpec(keyBytes, "AES");
    }

    /** Whether an encryption key is configured. */
    public boolean isConfigured() {
        return key != null;
    }

    /** @throws DomainException ALGORITHM_CREDENTIALS_ENCRYPTION_NOT_CONFIGURED without a key */
    public String encrypt(final String plaintext) {
        if (key == null) {
            throw new DomainException(DomainExceptionCode.ALGORITHM_CREDENTIALS_ENCRYPTION_NOT_CONFIGURED);
        }
        try {
            final byte[] nonce = new byte[NONCE_BYTES];
            random.nextBytes(nonce);
            final Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, nonce));
            cipher.updateAAD(ASSOCIATED_DATA);
            final byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            final ByteBuffer buffer = ByteBuffer.allocate(nonce.length + encrypted.length);
            buffer.put(nonce).put(encrypted);
            return PREFIX + Base64.getEncoder().encodeToString(buffer.array());
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Unable to encrypt the algorithm password", exception);
        }
    }

    /** @throws DomainException ALGORITHM_CREDENTIALS_UNAVAILABLE without a key, or if the value is not ours */
    public String decrypt(final String stored) {
        if (key == null || stored == null || !stored.startsWith(PREFIX)) {
            throw new DomainException(DomainExceptionCode.ALGORITHM_CREDENTIALS_UNAVAILABLE);
        }
        try {
            final byte[] all = Base64.getDecoder().decode(stored.substring(PREFIX.length()));
            if (all.length <= NONCE_BYTES) {
                throw new DomainException(DomainExceptionCode.ALGORITHM_CREDENTIALS_UNAVAILABLE);
            }
            final Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, all, 0, NONCE_BYTES));
            cipher.updateAAD(ASSOCIATED_DATA);
            return new String(cipher.doFinal(all, NONCE_BYTES, all.length - NONCE_BYTES), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException exception) {
            // Wrong key or a value that was changed: say neither which nor why beyond the code.
            throw new DomainException(DomainExceptionCode.ALGORITHM_CREDENTIALS_UNAVAILABLE);
        }
    }
}
