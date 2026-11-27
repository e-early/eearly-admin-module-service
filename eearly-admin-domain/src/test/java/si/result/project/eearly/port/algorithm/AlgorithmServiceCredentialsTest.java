package si.result.project.eearly.port.algorithm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import si.result.project.eearly.config.AlgorithmCredentialsProperties;
import si.result.project.eearly.exception.DomainExceptionCode;
import si.result.project.eearly.model.algorithm.Algorithm;
import si.result.project.eearly.model.algorithm.command.CreateAlgorithmCommand;
import si.result.project.eearly.model.algorithm.command.UpdateAlgorithmCommand;
import si.result.spring.boot.bricks.exception.DomainException;

class AlgorithmServiceCredentialsTest {

    private AlgorithmRepository repository;
    private AlgorithmCredentialCipher cipher;
    private AlgorithmService service;

    private static AlgorithmCredentialCipher cipher(final boolean configured) {
        final AlgorithmCredentialsProperties properties = new AlgorithmCredentialsProperties();
        if (configured) {
            final byte[] key = new byte[32];
            new SecureRandom().nextBytes(key);
            properties.setEncryptionKey(Base64.getEncoder().encodeToString(key));
        }
        return new AlgorithmCredentialCipher(properties);
    }

    private void init(final boolean keyConfigured) {
        repository = mock(AlgorithmRepository.class);
        when(repository.save(any(Algorithm.class))).thenAnswer(i -> i.getArgument(0));
        cipher = cipher(keyConfigured);
        service = new AlgorithmService(repository, new AlgorithmCredentialService(cipher));
    }

    @BeforeEach
    void setUp() {
        init(true);
    }

    private static CreateAlgorithmCommand create(final String username, final String password) {
        return new CreateAlgorithmCommand("name", null, "1", null, null, null, null, "http://x", "/run", null, null,
                null, null, null, "author", List.of(), List.of(), List.of(), null, null, null, null, null, username,
                password);
    }

    private static UpdateAlgorithmCommand update(final UUID id, final String username, final String password) {
        return new UpdateAlgorithmCommand(id, "name", null, "1", null, null, null, null, "http://x", "/run", null, null,
                null, null, null, "author", List.of(), List.of(), List.of(), null, null, null, null, null, username,
                password);
    }

    @Test
    @DisplayName("Create stores the password as ciphertext only")
    void create_storesCiphertext() {
        final Algorithm saved = service.create(create(" svc-user ", "pw-" + System.nanoTime()));

        assertThat(saved.getAuthUsername()).isEqualTo("svc-user");
        assertThat(saved.getAuthPassword()).startsWith("v1:").doesNotContain("pw-");
        assertThat(cipher.decrypt(saved.getAuthPassword())).startsWith("pw-");
    }

    @Test
    void create_withoutCredentials_storesNone() {
        final Algorithm saved = service.create(create(null, null));

        assertThat(saved.getAuthUsername()).isNull();
        assertThat(saved.getAuthPassword()).isNull();
    }

    @Test
    @DisplayName("Without an encryption key, creating with a password fails and saves nothing")
    void create_withPasswordButNoKey_fails() {
        init(false);

        assertThatThrownBy(() -> service.create(create("user", "pw")))
                .isInstanceOf(DomainException.class)
                .satisfies(e -> assertThat(((DomainException) e).getCode())
                        .isEqualTo(DomainExceptionCode.ALGORITHM_CREDENTIALS_ENCRYPTION_NOT_CONFIGURED));
        verify(repository, never()).save(any());
    }

    @Test
    void create_withoutPasswordNeedsNoKey() {
        init(false);

        assertThat(service.create(create("user", null)).getAuthUsername()).isEqualTo("user");
    }

    @Test
    @DisplayName("Update with null credentials keeps what is stored")
    void update_nullKeepsStoredCredentials() {
        final UUID id = UUID.randomUUID();
        final Algorithm existing = service.create(create("user", "pw"));
        final String stored = existing.getAuthPassword();
        when(repository.findById(id)).thenReturn(Optional.of(existing));

        final Algorithm updated = service.update(update(id, null, null));

        assertThat(updated.getAuthUsername()).isEqualTo("user");
        assertThat(updated.getAuthPassword()).isEqualTo(stored);
    }

    @Test
    void update_newPasswordReplacesTheOldOne() {
        final UUID id = UUID.randomUUID();
        final Algorithm existing = service.create(create("user", "old"));
        when(repository.findById(id)).thenReturn(Optional.of(existing));

        final Algorithm updated = service.update(update(id, null, "new"));

        assertThat(cipher.decrypt(updated.getAuthPassword())).isEqualTo("new");
    }

    @Test
    void update_emptyPasswordClearsIt_blankUsernameClearsBoth() {
        final UUID id = UUID.randomUUID();
        final Algorithm existing = service.create(create("user", "pw"));
        when(repository.findById(id)).thenReturn(Optional.of(existing));

        assertThat(service.update(update(id, null, "")).getAuthPassword()).isNull();

        final Algorithm again = service.create(create("user", "pw"));
        when(repository.findById(id)).thenReturn(Optional.of(again));
        final Algorithm cleared = service.update(update(id, "", null));
        assertThat(cleared.getAuthUsername()).isNull();
        assertThat(cleared.getAuthPassword()).isNull();
    }

    @Test
    @DisplayName("Without a key, an update that does not touch the password still works")
    void update_withoutKey_keepsExistingPassword() {
        final UUID id = UUID.randomUUID();
        final Algorithm existing = service.create(create("user", "pw"));
        final String stored = existing.getAuthPassword();
        init(false);
        when(repository.findById(id)).thenReturn(Optional.of(existing));

        assertThat(service.update(update(id, null, null)).getAuthPassword()).isEqualTo(stored);
        assertThatThrownBy(() -> service.update(update(id, null, "other"))).isInstanceOf(DomainException.class);
    }

    @Test
    void commandsNeverPrintThePassword() {
        assertThat(create("user", "super-secret").toString()).doesNotContain("super-secret");
        assertThat(update(UUID.randomUUID(), "user", "super-secret").toString()).doesNotContain("super-secret");
    }
}
