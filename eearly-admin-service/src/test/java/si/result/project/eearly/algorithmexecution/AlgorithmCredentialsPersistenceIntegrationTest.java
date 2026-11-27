package si.result.project.eearly.algorithmexecution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import si.result.project.eearly.config.AlgorithmCredentialsProperties;
import si.result.project.eearly.dao.algorithm.AlgorithmDAO;
import si.result.project.eearly.exception.DomainExceptionCode;
import si.result.project.eearly.model.algorithm.Algorithm;
import si.result.project.eearly.model.algorithm.command.CreateAlgorithmCommand;
import si.result.project.eearly.port.algorithm.AlgorithmCredentialCipher;
import si.result.project.eearly.port.algorithm.AlgorithmCredentialService;
import si.result.project.eearly.port.algorithm.AlgorithmRepository;
import si.result.project.eearly.port.algorithm.AlgorithmService;
import si.result.spring.boot.bricks.exception.DomainException;

/** The password of an algorithm as the database holds it: ciphertext, and only with an encryption key. */
@DataJpaTest
@ContextConfiguration(classes = AlgorithmCredentialsPersistenceIntegrationTest.Cfg.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@TestPropertySource(properties = {
        "spring.config.name=algorithm-credentials-persistence-test",
        "spring.application.name=eearly-admin-service",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.hibernate.naming.physical-strategy=org.hibernate.boot.model.naming.PhysicalNamingStrategyStandardImpl"
})
class AlgorithmCredentialsPersistenceIntegrationTest {

    @SpringBootConfiguration
    @EntityScan(basePackages = "si.result.project.eearly.model")
    @EnableJpaRepositories(basePackages = "si.result.project.eearly.dao")
    @EnableJpaAuditing(dateTimeProviderRef = "testDateTimeProvider")
    static class Cfg {
        @org.springframework.context.annotation.Bean
        DateTimeProvider testDateTimeProvider() {
            return () -> java.util.Optional.of(java.time.ZonedDateTime.now());
        }
    }

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void db(final DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.flyway.url", POSTGRES::getJdbcUrl);
        registry.add("spring.flyway.user", POSTGRES::getUsername);
        registry.add("spring.flyway.password", POSTGRES::getPassword);
    }

    @Autowired
    private AlgorithmDAO algorithmDAOBean;

    private AlgorithmRepository algorithmDAO;

    @org.junit.jupiter.api.BeforeEach
    void repository() {
        algorithmDAO = algorithmDAOBean;
    }
    @Autowired
    private JdbcTemplate jdbc;

    private static AlgorithmCredentialService credentials(final boolean withKey) {
        final AlgorithmCredentialsProperties properties = new AlgorithmCredentialsProperties();
        if (withKey) {
            final byte[] key = new byte[32];
            new SecureRandom().nextBytes(key);
            properties.setEncryptionKey(Base64.getEncoder().encodeToString(key));
        }
        return new AlgorithmCredentialService(new AlgorithmCredentialCipher(properties));
    }

    private static CreateAlgorithmCommand create(final String name, final String user, final String password) {
        return new CreateAlgorithmCommand(name, null, "1", si.result.project.eearly.model.algorithm.AlgorithmCategoryType.GENERAL,
                si.result.project.eearly.model.algorithm.AlgorithmStatusType.DRAFT, null, null, "http://x", "/run", null, null,
                null, null, null, "author", List.of(), List.of(), List.of(),
                si.result.project.eearly.model.algorithm.AlgorithmPricingModelType.FREE, java.math.BigDecimal.ZERO, java.util.Currency.getInstance("EUR"), null, null, user,
                password);
    }

    @Test
    @DisplayName("The database holds ciphertext, never the password, and the service reads it back")
    void passwordIsStoredEncrypted() {
        final AlgorithmCredentialService credentialService = credentials(true);
        final AlgorithmService service = new AlgorithmService(algorithmDAO, credentialService);
        final String password = "pw-" + UUID.randomUUID();

        final Algorithm saved = service.create(create("with-credentials", "svc", password));

        final String stored = jdbc.queryForObject("SELECT auth_password FROM t_algorithm WHERE id = ?",
                String.class, saved.getId());
        assertThat(stored).startsWith("v1:").doesNotContain(password);
        assertThat(jdbc.queryForObject("SELECT auth_username FROM t_algorithm WHERE id = ?", String.class,
                saved.getId())).isEqualTo("svc");
        final Algorithm reloaded = algorithmDAO.findById(saved.getId()).orElseThrow();
        assertThat(credentialService.resolve(reloaded))
                .hasValueSatisfying(c -> assertThat(c.password()).isEqualTo(password));
    }

    @Test
    @DisplayName("Without a key nothing is stored for a password, and a stored one cannot be used")
    void withoutKey() {
        final AlgorithmService keyed = new AlgorithmService(algorithmDAO, credentials(true));
        final Algorithm saved = keyed.create(create("keyed", "svc", "pw"));

        final AlgorithmCredentialService withoutKey = credentials(false);
        final AlgorithmService unkeyed = new AlgorithmService(algorithmDAO, withoutKey);

        assertThatThrownBy(() -> unkeyed.create(create("unkeyed", "svc", "pw")))
                .isInstanceOf(DomainException.class)
                .satisfies(e -> assertThat(((DomainException) e).getCode())
                        .isEqualTo(DomainExceptionCode.ALGORITHM_CREDENTIALS_ENCRYPTION_NOT_CONFIGURED));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM t_algorithm WHERE name = 'unkeyed'", Integer.class))
                .isZero();
        assertThatThrownBy(() -> withoutKey.resolve(algorithmDAO.findById(saved.getId()).orElseThrow()))
                .isInstanceOf(DomainException.class)
                .satisfies(e -> assertThat(((DomainException) e).getCode())
                        .isEqualTo(DomainExceptionCode.ALGORITHM_CREDENTIALS_UNAVAILABLE));
    }
}
