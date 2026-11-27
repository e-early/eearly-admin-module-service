package si.result.project.eearly;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import si.result.project.eearly.config.async.AsyncConfig;
import si.result.project.eearly.config.scheduling.SchedulingConfig;

/**
 * The full context starts from the application class, with the core defaults of the library and the application's
 * own application.yaml, against a migrated PostgreSQL database.
 */
@SpringBootTest(classes = EEarlyAdminApplication.class, webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                // Settings the core defaults require from the environment of a deployment
                "LOGGING_FILE_PATH=target/test-logs/eearly-admin-app.log",
                "DB_SCHEMA=eearly_admin",
                "DB_AUDIT_SCHEMA=eearly_admin_audit",
                "DB_FLYWAY_USER=test",
                "DB_FLYWAY_PASS=test",
                "DB_SERVICE_USER=test",
                "DB_SERVICE_PASS=test",
                "JWK_SET_URI=http://localhost:1/jwks",
                "SERVICE_DOMAIN_URL=http://localhost:9000",
                "MOBILE_SERVICE_BASE_URL=http://localhost:1/api/v1",
                "MOBILE_KEYCLOAK_BASE_URL=http://localhost:1",
                "MOBILE_KEYCLOAK_REALM=test",
                "MOBILE_KEYCLOAK_ADMIN_CLIENT_SECRET=test",
                "ALGORITHM_STUCK_SWEEP_ENABLED=false",
                "PATIENT_DEMOGRAPHICS_SYNC_ON_STARTUP=false"
        })
@Testcontainers
class EEarlyAdminApplicationIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void db(DynamicPropertyRegistry registry) {
        registry.add("DB_URL", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.flyway.user", POSTGRES::getUsername);
        registry.add("spring.flyway.password", POSTGRES::getPassword);
    }

    @Autowired
    private ApplicationContext context;

    @Autowired
    private Environment environment;

    @Test
    void contextStarts_withTheLibraryConfigurationAndCoreDefaults() {
        assertThat(context.getBeansOfType(AsyncConfig.class)).hasSize(1);
        assertThat(context.getBeansOfType(SchedulingConfig.class)).hasSize(1);
        // from the application's own application.yaml
        assertThat(environment.getProperty("spring.application.name")).isEqualTo("eearly-admin-service");
        // only in the core defaults
        assertThat(environment.getProperty("server.port")).isEqualTo("9000");
        assertThat(environment.getProperty("spring.jpa.open-in-view")).isEqualTo("false");
    }
}
