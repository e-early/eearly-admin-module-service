package si.result.project.eearly.trigger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import si.result.project.eearly.dao.algorithm.AlgorithmDAO;
import si.result.project.eearly.dao.analysis.AnalysisDAO;
import si.result.project.eearly.dao.patient.PatientDAO;
import si.result.project.eearly.facade.analysis.AnalysisServiceFacade;
import si.result.project.eearly.mapper.analysis.AnalysisMapperImpl;
import si.result.project.eearly.model.analysis.AnalysisTriggerType;
import si.result.project.eearly.port.algorithm.AlgorithmService;
import si.result.project.eearly.port.algorithm.AlgorithmRepository;
import si.result.project.eearly.port.analysis.AnalysisDetectionBoxService;
import si.result.project.eearly.port.analysis.AnalysisService;
import si.result.project.eearly.port.patient.PatientService;
import si.result.project.eearly.port.patient.PatientRepository;
import si.result.project.eearly.resource.analysis.AnalysisController;

/**
 * Creates an analysis through the REST controller, facade, domain service and JPA repository against a
 * migrated PostgreSQL database (Hibernate validates the mapping against the schema).
 */
@DataJpaTest
@ContextConfiguration(classes = AnalysisTriggerTypeRestIntegrationTest.Cfg.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@TestPropertySource(properties = {
        "spring.config.name=analysis-trigger-type-test",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.hibernate.naming.physical-strategy=org.hibernate.boot.model.naming.PhysicalNamingStrategyStandardImpl"
})
class AnalysisTriggerTypeRestIntegrationTest {

    private static final UUID PATIENT_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID ALGORITHM_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

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
    private AnalysisDAO analysisDAO;

    @Autowired
    private PatientDAO patientDAO;

    @Autowired
    private AlgorithmDAO algorithmDAO;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void createdAnalysisIsManualInResponseAndInDatabase() throws Exception {
        jdbc.update("INSERT INTO t_patient (id, first_name, last_name, date_of_birth, gender, health_insurance_id, "
                + "street, city, state, country, email, phone_number) VALUES (?, 'A', 'B', DATE '1990-01-01', "
                + "'OTHER', 'X1', 's', 'c', 'st', 'co', 'a@b.c', '1')", PATIENT_ID);
        jdbc.update("INSERT INTO t_algorithm (id, name) VALUES (?, 'algo')", ALGORITHM_ID);

        final var patientService = mock(PatientService.class);
        final PatientRepository patientRepository = patientDAO;
        final AlgorithmRepository algorithmRepository = algorithmDAO;
        // Loaded when the facade asks for it, inside the request's transaction (as in the running service), so
        // that the patient's lazily loaded associations can be mapped.
        when(patientService.findById(PATIENT_ID)).thenAnswer(invocation -> patientRepository.findById(PATIENT_ID).orElseThrow());
        final var algorithmService = mock(AlgorithmService.class);
        when(algorithmService.findById(ALGORITHM_ID))
                .thenReturn(algorithmRepository.findById(ALGORITHM_ID).orElseThrow());
        final var facade = new AnalysisServiceFacade(
                new AnalysisService(analysisDAO),
                algorithmService,
                new AnalysisMapperImpl(),
                patientService,
                mock(AnalysisDetectionBoxService.class));
        final MockMvc mvc = MockMvcBuilders.standaloneSetup(new AnalysisController(facade)).build();

        final String body = """
                {
                  "name": "Apnea detection",
                  "patientId": "%s",
                  "algorithmId": "%s",
                  "triggerType": "SCHEDULED",
                  "selections": []
                }
                """.formatted(PATIENT_ID, ALGORITHM_ID);

        final String response = new TransactionTemplate(transactionManager).execute(transaction -> {
            try {
                return mvc.perform(post("/api/v1/analyses")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.payload.triggerType").value("MANUAL"))
                        .andReturn().getResponse().getContentAsString();
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });

        assertThat(response).contains("\"MANUAL\"");
        assertThat(jdbc.queryForObject("SELECT trigger_type::text FROM t_analysis WHERE name = 'Apnea detection'",
                String.class)).isEqualTo(AnalysisTriggerType.MANUAL.name());
        assertThat(analysisDAO.findAll()).singleElement()
                .satisfies(a -> assertThat(a.getTriggerType()).isEqualTo(AnalysisTriggerType.MANUAL));
    }
}
