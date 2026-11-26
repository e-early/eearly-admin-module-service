package si.result.project.eearly.patient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
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
import si.result.project.eearly.dao.patient.PatientDAO;
import si.result.project.eearly.exception.DomainExceptionCode;
import si.result.project.eearly.port.patient.PatientRepository;
import si.result.project.eearly.port.patient.PatientService;
import si.result.spring.boot.bricks.exception.DomainException;

/**
 * Patient language against a migrated PostgreSQL database (Hibernate validates the mapping against the schema):
 * the migration default, and changing the language through the domain service and JPA repository.
 */
@DataJpaTest
@ContextConfiguration(classes = PatientLanguageIntegrationTest.Cfg.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@TestPropertySource(properties = {
        "spring.config.name=patient-language-test",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.hibernate.naming.physical-strategy=org.hibernate.boot.model.naming.PhysicalNamingStrategyStandardImpl"
})
class PatientLanguageIntegrationTest {

    @SpringBootConfiguration
    @EntityScan(basePackages = "si.result.project.eearly.model")
    @EnableJpaRepositories(basePackages = "si.result.project.eearly.dao")
    static class Cfg {
    }

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void db(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.flyway.url", POSTGRES::getJdbcUrl);
        registry.add("spring.flyway.user", POSTGRES::getUsername);
        registry.add("spring.flyway.password", POSTGRES::getPassword);
    }

    @Autowired
    private PatientDAO patientDAO;

    @Autowired
    private JdbcTemplate jdbc;

    private UUID insertPatient(final String healthInsuranceId) {
        final UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO t_patient (id, keycloak_id, first_name, last_name, date_of_birth, gender, "
                + "health_insurance_id, street, city, state, country, email, phone_number) "
                + "VALUES (?, ?, 'A', 'B', DATE '1990-01-01', 'OTHER', ?, 's', 'c', 'st', 'co', 'a@b.c', '1')",
                id, id, healthInsuranceId);
        return id;
    }

    @Test
    void aPatientWithoutALanguageIsSlovenian() {
        final UUID id = insertPatient("L1");

        assertThat(jdbc.queryForObject("SELECT language FROM t_patient WHERE id = ?", String.class, id))
                .isEqualTo("sl");
        final PatientRepository repository = patientDAO;
        assertThat(repository.findById(id).orElseThrow().getLanguage()).isEqualTo("sl");
    }

    @Test
    void changeLanguageIsFoundByKeycloakIdAndPersisted() {
        final UUID keycloakId = insertPatient("L2");
        final var service = new PatientService(patientDAO);

        service.changeLanguage(keycloakId, "en");

        assertThat(jdbc.queryForObject("SELECT language FROM t_patient WHERE keycloak_id = ?", String.class,
                keycloakId)).isEqualTo("en");
    }

    @Test
    void anUnsupportedLanguageIsRejectedAndNothingIsChanged() {
        final UUID keycloakId = insertPatient("L3");
        final var service = new PatientService(patientDAO);

        assertThatThrownBy(() -> service.changeLanguage(keycloakId, "de"))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).getCode())
                .isEqualTo(DomainExceptionCode.PATIENT_LANGUAGE_UNSUPPORTED);
        assertThat(jdbc.queryForObject("SELECT language FROM t_patient WHERE keycloak_id = ?", String.class,
                keycloakId)).isEqualTo("sl");
    }
}
