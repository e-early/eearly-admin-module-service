package si.result.project.eearly.algorithmexecution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
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
import si.result.project.eearly.config.CallbackTokenProperties;
import si.result.project.eearly.dao.algorithmexecution.AlgorithmExecutionDAO;
import si.result.project.eearly.dao.analysis.AnalysisDAO;
import si.result.project.eearly.dto.analysis.AnalysisResultCallbackDTO;
import si.result.project.eearly.exception.CallbackTokenRejectedException;
import si.result.project.eearly.facade.analysis.AnalysisServiceFacade;
import si.result.project.eearly.mapper.analysis.AnalysisMapperImpl;
import si.result.project.eearly.model.analysis.AnalysisState;
import si.result.project.eearly.port.algorithmexecution.AlgorithmExecutionService;
import si.result.project.eearly.port.algorithmexecution.CallbackTokenService;
import si.result.project.eearly.port.analysis.AnalysisDetectionBoxService;
import si.result.project.eearly.port.analysis.AnalysisService;

/**
 * The callback token and the processing of the result it authorises commit or roll back together, against a real
 * PostgreSQL and the real transaction proxy of the facade: a result that fails to process does not burn the token,
 * and the provider may retry with it.
 */
@DataJpaTest
@ContextConfiguration(classes = CallbackTokenTransactionIntegrationTest.Cfg.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@TestPropertySource(properties = {
        "spring.config.name=callback-token-transaction-test",
        "spring.application.name=eearly-admin-service",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.hibernate.naming.physical-strategy=org.hibernate.boot.model.naming.PhysicalNamingStrategyStandardImpl"
})
class CallbackTokenTransactionIntegrationTest {

    private static final UUID PATIENT_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID ALGORITHM_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final UUID ANALYSIS_ID = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
    private static final UUID EXECUTION_ID = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd");

    @SpringBootConfiguration
    @EntityScan(basePackages = "si.result.project.eearly.model")
    @EnableJpaRepositories(basePackages = "si.result.project.eearly.dao")
    @EnableJpaAuditing(dateTimeProviderRef = "testDateTimeProvider")
    static class Cfg {
        @Bean
        DateTimeProvider testDateTimeProvider() {
            return () -> Optional.of(ZonedDateTime.now());
        }

        @Bean
        AnalysisDetectionBoxService analysisDetectionBoxService() {
            return mock(AnalysisDetectionBoxService.class);
        }

        @Bean
        AnalysisService analysisService(final AnalysisDAO analysisDAO, final ApplicationEventPublisher publisher) {
            return new AnalysisService(analysisDAO, publisher);
        }

        @Bean
        AlgorithmExecutionService algorithmExecutionService(
                final AlgorithmExecutionDAO executionDAO, final ApplicationEventPublisher publisher) {
            return new AlgorithmExecutionService(executionDAO, publisher);
        }

        @Bean
        CallbackTokenService callbackTokenService(final AlgorithmExecutionDAO executionDAO) {
            final CallbackTokenProperties properties = new CallbackTokenProperties();
            properties.setTokenSecret(UUID.randomUUID() + "-" + UUID.randomUUID());
            return new CallbackTokenService(executionDAO, properties);
        }

        @Bean
        AnalysisServiceFacade analysisServiceFacade(
                final AnalysisService analysisService,
                final AnalysisDetectionBoxService detectionBoxService,
                final AlgorithmExecutionService executionService,
                final CallbackTokenService tokenService) {
            return new AnalysisServiceFacade(analysisService, null, new AnalysisMapperImpl(), null,
                    detectionBoxService, executionService, tokenService);
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
    private JdbcTemplate jdbc;
    @Autowired
    private AnalysisServiceFacade facade;
    @Autowired
    private CallbackTokenService tokens;
    @Autowired
    private AnalysisDetectionBoxService detectionBoxService;

    @BeforeEach
    void data() {
        jdbc.update("DELETE FROM t_algorithm_execution");
        jdbc.update("DELETE FROM t_analysis");
        jdbc.update("DELETE FROM t_algorithm");
        jdbc.update("DELETE FROM t_patient");
        jdbc.update("INSERT INTO t_patient (id, first_name, last_name, date_of_birth, gender, health_insurance_id, "
                + "street, city, state, country, email, phone_number) VALUES (?, 'A', 'B', DATE '1990-01-01', "
                + "'OTHER', 'X1', 's', 'c', 'st', 'co', 'a@b.c', '1')", PATIENT_ID);
        jdbc.update("INSERT INTO t_algorithm (id, name) VALUES (?, 'algo')", ALGORITHM_ID);
        jdbc.update("INSERT INTO t_analysis (id, name, state, feedback, id_patient, id_algorithm) "
                + "VALUES (?, 'analysis', 'IN_PROGRESS', 'NONE', ?, ?)", ANALYSIS_ID, PATIENT_ID, ALGORITHM_ID);
        jdbc.update("INSERT INTO t_algorithm_execution (id, id_algorithm, id_patient, id_analysis, trigger_type, "
                + "execution_status, started_at) VALUES (?, ?, ?, ?, 'MANUAL', 'RUNNING', now())",
                EXECUTION_ID, ALGORITHM_ID, PATIENT_ID, ANALYSIS_ID);
    }

    private AnalysisResultCallbackDTO callback() {
        return new AnalysisResultCallbackDTO(UUID.randomUUID(), ANALYSIS_ID, AnalysisState.COMPLETED, false,
                "model", List.of());
    }

    private String executionStatus() {
        return jdbc.queryForObject("SELECT execution_status::text FROM t_algorithm_execution WHERE id = ?",
                String.class, EXECUTION_ID);
    }

    private Object consumedAt() {
        return jdbc.queryForObject("SELECT callback_token_consumed_at FROM t_algorithm_execution WHERE id = ?",
                Object.class, EXECUTION_ID);
    }

    private String analysisState() {
        return jdbc.queryForObject("SELECT state::text FROM t_analysis WHERE id = ?", String.class, ANALYSIS_ID);
    }

    @Test
    @DisplayName("Issuing commits on its own: the token is stored before the provider can call back")
    void issuedTokenIsStored() {
        final String token = tokens.issueToken(EXECUTION_ID);

        assertThat(token).isNotBlank();
        assertThat(jdbc.queryForObject("SELECT callback_token_id FROM t_algorithm_execution WHERE id = ?",
                Object.class, EXECUTION_ID)).isNotNull();
        assertThat(consumedAt()).isNull();
    }

    @Test
    @DisplayName("A result that fails to process leaves the token usable, and the retry then succeeds exactly once")
    void failedProcessing_doesNotBurnTheToken() {
        final String token = tokens.issueToken(EXECUTION_ID);
        doThrow(new IllegalStateException("processing failed")).when(detectionBoxService)
                .replaceAlgorithmDetections(any(), any());

        assertThatThrownBy(() -> facade.updateResult(ANALYSIS_ID, callback(), token))
                .isInstanceOf(IllegalStateException.class).hasMessage("processing failed");

        // everything was rolled back together: the token is not used up, nothing else changed
        assertThat(consumedAt()).isNull();
        assertThat(executionStatus()).isEqualTo("RUNNING");
        assertThat(analysisState()).isEqualTo("IN_PROGRESS");

        doNothing().when(detectionBoxService).replaceAlgorithmDetections(any(), any());
        facade.updateResult(ANALYSIS_ID, callback(), token);

        assertThat(consumedAt()).isNotNull();
        assertThat(executionStatus()).isEqualTo("COMPLETED");
        assertThat(analysisState()).isNotEqualTo("IN_PROGRESS");

        // single use
        assertThatThrownBy(() -> facade.updateResult(ANALYSIS_ID, callback(), token))
                .isInstanceOf(CallbackTokenRejectedException.class);
    }

    @Test
    @DisplayName("A token for another analysis is refused and not used up")
    void wrongAnalysis_isRefusedAndNotBurnt() {
        final String token = tokens.issueToken(EXECUTION_ID);

        assertThatThrownBy(() -> facade.updateResult(UUID.randomUUID(), callback(), token))
                .isInstanceOf(CallbackTokenRejectedException.class);

        assertThat(consumedAt()).isNull();
        assertThat(executionStatus()).isEqualTo("RUNNING");
    }

    @Test
    @DisplayName("Without a token (a JWT caller) the result still completes the running execution")
    void jwtCaller_completesTheExecution() {
        facade.updateResult(ANALYSIS_ID, callback(), null);

        assertThat(executionStatus()).isEqualTo("COMPLETED");
        assertThat(consumedAt()).isNull();
    }

    @Test
    @DisplayName("A result in the ERROR state fails the execution in the same transaction")
    void errorResult_failsTheExecution() {
        final String token = tokens.issueToken(EXECUTION_ID);

        facade.updateResult(ANALYSIS_ID, new AnalysisResultCallbackDTO(UUID.randomUUID(), ANALYSIS_ID,
                AnalysisState.ERROR, false, "model", List.of()), token);

        assertThat(executionStatus()).isEqualTo("FAILED");
        assertThat(consumedAt()).isNotNull();
        assertThat(analysisState()).isEqualTo("ERROR");
    }
}
