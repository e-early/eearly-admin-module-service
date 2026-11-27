package si.result.project.eearly.healthcondition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Bean;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import si.result.project.eearly.config.algorithm.AlgorithmHttpClient;
import si.result.project.eearly.dao.algorithm.AlgorithmDAO;
import si.result.project.eearly.dao.healthcondition.HealthConditionDAO;
import si.result.project.eearly.dao.measurement.MeasurementDAO;
import si.result.project.eearly.dao.patient.PatientDAO;
import si.result.project.eearly.facade.algorithm.AlgorithmServiceFacade;
import si.result.project.eearly.facade.healthcondition.HealthConditionServiceFacade;
import si.result.project.eearly.facade.patient.PatientServiceFacade;
import si.result.project.eearly.mapper.algorithm.AlgorithmMapper;
import si.result.project.eearly.mapper.algorithm.AlgorithmMapperImpl;
import si.result.project.eearly.mapper.healthcondition.HealthConditionMapper;
import si.result.project.eearly.mapper.healthcondition.HealthConditionMapperImpl;
import si.result.project.eearly.mapper.patient.PatientMapper;
import si.result.project.eearly.mapper.patient.PatientMapperImpl;
import si.result.project.eearly.port.algorithm.AlgorithmService;
import si.result.project.eearly.port.caretaker.CaretakerService;
import si.result.project.eearly.port.healthcondition.HealthConditionService;
import si.result.project.eearly.port.measurement.MeasurementService;
import si.result.project.eearly.port.mobile.MobileKeycloakService;
import si.result.project.eearly.port.mobile.MobileServiceService;
import si.result.project.eearly.port.patient.PatientDemographicsSync;
import si.result.project.eearly.port.patient.PatientMeasurementClient;
import si.result.project.eearly.port.patient.PatientService;
import si.result.project.eearly.resource.algorithm.AlgorithmController;
import si.result.project.eearly.resource.healthcondition.HealthConditionController;
import si.result.project.eearly.resource.patient.PatientController;
import si.result.rest.filter.config.RestFilterArgumentResolver;
import si.result.spring.boot.bricks.advice.ErrorResponseHandler;

/**
 * Health conditions through the REST controller, facade, domain service and JPA repository against a migrated
 * PostgreSQL database (Hibernate validates the mapping against the schema), including the links to patients and
 * algorithms and the seeded catalogue.
 */
@DataJpaTest
@ContextConfiguration(classes = HealthConditionRestIntegrationTest.Cfg.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@TestPropertySource(properties = {
        "spring.config.name=health-condition-rest-test",
        // The first test context of a JVM initialises logging for all later ones (the log format
        // characterization test reads service.name), so name the application as the service does.
        "spring.application.name=eearly-admin-service",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.hibernate.naming.physical-strategy=org.hibernate.boot.model.naming.PhysicalNamingStrategyStandardImpl"
})
class HealthConditionRestIntegrationTest {

    @SpringBootConfiguration
    @EntityScan(basePackages = "si.result.project.eearly.model")
    @EnableJpaRepositories(basePackages = "si.result.project.eearly.dao")
    static class Cfg {

        @Bean
        HealthConditionMapper healthConditionMapper() {
            return new HealthConditionMapperImpl();
        }

        @Bean
        PatientMapper patientMapper() {
            return new PatientMapperImpl();
        }

        @Bean
        AlgorithmMapper algorithmMapper() {
            return new AlgorithmMapperImpl();
        }

        @Bean
        HealthConditionService healthConditionService(HealthConditionDAO healthConditionDAO) {
            return new HealthConditionService(healthConditionDAO);
        }

        @Bean
        PatientService patientService(PatientDAO patientDAO) {
            return new PatientService(patientDAO);
        }

        @Bean
        AlgorithmService algorithmService(AlgorithmDAO algorithmDAO) {
            return new AlgorithmService(algorithmDAO);
        }

        @Bean
        MeasurementService measurementService(MeasurementDAO measurementDAO) {
            return new MeasurementService(measurementDAO);
        }

        @Bean
        PatientDemographicsSync patientDemographicsSync() {
            return mock(PatientDemographicsSync.class);
        }

        // Spring-managed facades, so that their transactional methods keep the session open while the
        // lazily loaded associations are mapped (as in the running service).
        @Bean
        HealthConditionServiceFacade healthConditionServiceFacade(HealthConditionService healthConditionService,
                HealthConditionMapper healthConditionMapper) {
            return new HealthConditionServiceFacade(healthConditionService, healthConditionMapper);
        }

        @Bean
        PatientServiceFacade patientServiceFacade(PatientService patientService, PatientMapper patientMapper,
                MeasurementService measurementService, HealthConditionService healthConditionService,
                AlgorithmService algorithmService, PatientDemographicsSync patientDemographicsSync) {
            return new PatientServiceFacade(patientService, patientMapper, mock(CaretakerService.class),
                    measurementService, healthConditionService, algorithmService, mock(MobileKeycloakService.class),
                    mock(MobileServiceService.class), mock(PatientMeasurementClient.class), patientDemographicsSync);
        }

        @Bean
        AlgorithmServiceFacade algorithmServiceFacade(AlgorithmService algorithmService, PatientService patientService,
                MeasurementService measurementService, HealthConditionService healthConditionService,
                AlgorithmMapper algorithmMapper) {
            return new AlgorithmServiceFacade(algorithmService, mock(AlgorithmHttpClient.class), patientService,
                    measurementService, healthConditionService, algorithmMapper);
        }
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

    private static final ObjectMapper JSON = new ObjectMapper();

    /** The catalogue the migrations seed; every other row belongs to a test. */
    private static final Map<String, String> SEEDED = Map.of(
            "01234567-89ab-cdef-0123-456789abcdef", "HYPERTENSION",
            "11234567-89ab-cdef-0123-456789abcdef", "TYPE_2_DIABETES",
            "21234567-89ab-cdef-0123-456789abcdef", "CORONARY_ARTERY_DISEASE",
            "31234567-89ab-cdef-0123-456789abcdef", "ASTHMA",
            "41234567-89ab-cdef-0123-456789abcdef", "CHRONIC_KIDNEY_DISEASE",
            "a7475581-4f5c-487a-b97d-c10b1f67e331", "SLEEP_APNEA");

    @Autowired
    private HealthConditionServiceFacade healthConditionFacade;

    @Autowired
    private PatientServiceFacade patientFacade;

    @Autowired
    private AlgorithmServiceFacade algorithmFacade;

    @Autowired
    private JdbcTemplate jdbc;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM t_patient_health_condition");
        jdbc.update("DELETE FROM t_health_condition_algorithm");
        jdbc.update("DELETE FROM t_patient");
        jdbc.update("DELETE FROM t_algorithm");
        jdbc.update("DELETE FROM t_health_condition WHERE name LIKE 'Test %'");
        mvc = MockMvcBuilders
                .standaloneSetup(new HealthConditionController(healthConditionFacade),
                        new PatientController(patientFacade), new AlgorithmController(algorithmFacade))
                .setCustomArgumentResolvers(new RestFilterArgumentResolver(), new PageableHandlerMethodArgumentResolver())
                .setControllerAdvice(new ErrorResponseHandler())
                .build();
    }

    private String conditionBody(final String name) throws Exception {
        final Map<String, Object> body = new HashMap<>();
        body.put("name", name);
        return JSON.writeValueAsString(body);
    }

    private UUID createCondition(final String name) throws Exception {
        final String response = mvc.perform(post("/api/v1/healthconditions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(conditionBody(name)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(JSON.readTree(response).get("payload").get("id").asText());
    }

    private UUID insertPatient(final String healthInsuranceId) {
        final UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO t_patient (id, keycloak_id, first_name, last_name, date_of_birth, gender, "
                + "health_insurance_id, street, city, state, country, email, phone_number) "
                + "VALUES (?, ?, 'A', 'B', DATE '1990-01-01', 'OTHER', ?, 's', 'c', 'st', 'co', 'a@b.c', '1')",
                id, id, healthInsuranceId);
        return id;
    }

    private String patientBody(final List<UUID> healthConditionIds) throws Exception {
        final Map<String, Object> body = new HashMap<>();
        body.put("firstName", "Ana");
        body.put("lastName", "Novak");
        body.put("dateOfBirth", "1990-01-01");
        body.put("gender", "FEMALE");
        body.put("healthInsuranceId", "HC-1");
        body.put("street", "Street");
        body.put("city", "City");
        body.put("state", "State");
        body.put("zip", 1000);
        body.put("country", "Country");
        body.put("email", "ana@example.org");
        body.put("phoneNumber", "+38640000000");
        body.put("healthConditionList", healthConditionIds == null ? null
                : healthConditionIds.stream().map(UUID::toString).toList());
        return JSON.writeValueAsString(body);
    }

    private String algorithmBody(final UUID id, final List<UUID> healthConditionIds) throws Exception {
        final Map<String, Object> body = new HashMap<>();
        body.put("id", id == null ? null : id.toString());
        body.put("name", "Test Algorithm");
        body.put("category", "CARDIOVASCULAR");
        body.put("status", "DRAFT");
        body.put("pricingModel", "FREE");
        body.put("costPerExecution", 0.0);
        body.put("currency", "EUR");
        body.put("healthConditionList", healthConditionIds == null ? null
                : healthConditionIds.stream().map(UUID::toString).toList());
        return JSON.writeValueAsString(body);
    }

    private List<UUID> linkedConditionsOfPatient(final UUID patientId) {
        return jdbc.queryForList("SELECT condition_id FROM t_patient_health_condition WHERE patient_id = ?",
                UUID.class, patientId);
    }

    private List<UUID> linkedConditionsOfAlgorithm(final UUID algorithmId) {
        return jdbc.queryForList("SELECT condition_id FROM t_health_condition_algorithm WHERE algorithm_id = ?",
                UUID.class, algorithmId);
    }

    @Test
    void createdHealthConditionIsStoredAndReadable() throws Exception {
        final UUID id = createCondition("Test Condition");

        assertThat(jdbc.queryForObject("SELECT name FROM t_health_condition WHERE id = ?", String.class, id))
                .isEqualTo("Test Condition");
        assertThat(jdbc.queryForObject("SELECT is_deleted FROM t_health_condition WHERE id = ?", Boolean.class, id))
                .isFalse();

        mvc.perform(get("/api/v1/healthconditions/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payload.id").value(id.toString()))
                .andExpect(jsonPath("$.payload.name").value("Test Condition"));
    }

    @Test
    void unknownHealthConditionIsNotFound() throws Exception {
        mvc.perform(get("/api/v1/healthconditions/" + UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    @Test
    void healthConditionWithoutNameIsRejectedAndNotStored() throws Exception {
        mvc.perform(post("/api/v1/healthconditions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(conditionBody(null)))
                .andExpect(status().isBadRequest());

        assertThat(jdbc.queryForObject("SELECT count(*) FROM t_health_condition WHERE name IS NULL", Integer.class))
                .isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM t_health_condition", Integer.class))
                .isEqualTo(SEEDED.size());
    }

    @Test
    void listIsPagedSortedAndFiltered() throws Exception {
        createCondition("Test Alpha");
        createCondition("Test Beta");
        createCondition("Test Gamma");

        mvc.perform(get("/api/v1/healthconditions").param("filter", "name:LIKE:Test ")
                        .param("sort", "name,asc").param("size", "2").param("page", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payload.content.length()").value(2))
                .andExpect(jsonPath("$.payload.content[0].name").value("Test Alpha"))
                .andExpect(jsonPath("$.payload.content[1].name").value("Test Beta"))
                .andExpect(jsonPath("$.payload.page.totalElements").value(3))
                .andExpect(jsonPath("$.payload.page.totalPages").value(2));

        mvc.perform(get("/api/v1/healthconditions").param("filter", "name:LIKE:Test ")
                        .param("sort", "name,asc").param("size", "2").param("page", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payload.content.length()").value(1))
                .andExpect(jsonPath("$.payload.content[0].name").value("Test Gamma"));

        mvc.perform(get("/api/v1/healthconditions").param("filter", "name:EQ:Test Beta"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payload.content.length()").value(1))
                .andExpect(jsonPath("$.payload.content[0].name").value("Test Beta"));
    }

    @Test
    void migrationSeedsTheCatalogueOfHealthConditions() throws Exception {
        final Map<String, String> stored = new HashMap<>();
        jdbc.query("SELECT id, name FROM t_health_condition WHERE id::text IN ("
                        + String.join(",", SEEDED.keySet().stream().map(id -> "'" + id + "'").toList()) + ")",
                rs -> {
                    stored.put(rs.getString("id"), rs.getString("name"));
                });

        assertThat(stored).isEqualTo(SEEDED);
        for (Map.Entry<String, String> seeded : SEEDED.entrySet()) {
            mvc.perform(get("/api/v1/healthconditions/" + seeded.getKey()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.payload.name").value(seeded.getValue()));
        }
    }

    @Test
    void patientHealthConditionsAreLinkedReadAndReplaced() throws Exception {
        final UUID first = createCondition("Test First");
        final UUID second = createCondition("Test Second");
        final UUID patientId = insertPatient("HC-1");

        mvc.perform(put("/api/v1/patients/" + patientId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(patientBody(List.of(first, second))))
                .andExpect(status().isOk());

        assertThat(linkedConditionsOfPatient(patientId)).containsExactlyInAnyOrder(first, second);
        mvc.perform(get("/api/v1/patients/" + patientId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payload.healthConditions.length()").value(2))
                .andExpect(jsonPath("$.payload.healthConditions[?(@.id == '" + first + "')].name").value("Test First"))
                .andExpect(jsonPath("$.payload.healthConditions[?(@.id == '" + second + "')].name")
                        .value("Test Second"));

        mvc.perform(put("/api/v1/patients/" + patientId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(patientBody(List.of(second))))
                .andExpect(status().isOk());
        assertThat(linkedConditionsOfPatient(patientId)).containsExactly(second);

        mvc.perform(put("/api/v1/patients/" + patientId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(patientBody(null)))
                .andExpect(status().isOk());
        assertThat(linkedConditionsOfPatient(patientId)).isEmpty();
        mvc.perform(get("/api/v1/patients/" + patientId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payload.healthConditions.length()").value(0));
    }

    @Test
    void unknownHealthConditionOfAPatientIsRejectedAndKeepsTheLinks() throws Exception {
        final UUID first = createCondition("Test First");
        final UUID patientId = insertPatient("HC-1");
        mvc.perform(put("/api/v1/patients/" + patientId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(patientBody(List.of(first))))
                .andExpect(status().isOk());

        mvc.perform(put("/api/v1/patients/" + patientId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(patientBody(List.of(first, UUID.randomUUID()))))
                .andExpect(status().isNotFound());

        assertThat(linkedConditionsOfPatient(patientId)).containsExactly(first);
    }

    @Test
    void algorithmHealthConditionsAreLinkedReadAndReplaced() throws Exception {
        final UUID first = createCondition("Test First");
        final UUID second = createCondition("Test Second");

        final String created = mvc.perform(post("/api/v1/algorithms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(algorithmBody(null, List.of(first, second))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.payload.healthConditions.length()").value(2))
                .andReturn().getResponse().getContentAsString();
        final JsonNode payload = JSON.readTree(created).get("payload");
        final UUID algorithmId = UUID.fromString(payload.get("id").asText());

        assertThat(linkedConditionsOfAlgorithm(algorithmId)).containsExactlyInAnyOrder(first, second);
        mvc.perform(get("/api/v1/algorithms/" + algorithmId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payload.healthConditions[?(@.id == '" + first + "')].name").value("Test First"))
                .andExpect(jsonPath("$.payload.healthConditions[?(@.id == '" + second + "')].name")
                        .value("Test Second"));

        mvc.perform(put("/api/v1/algorithms/" + algorithmId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(algorithmBody(algorithmId, List.of(first))))
                .andExpect(status().isOk());
        assertThat(linkedConditionsOfAlgorithm(algorithmId)).containsExactly(first);

        mvc.perform(put("/api/v1/algorithms/" + algorithmId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(algorithmBody(algorithmId, null)))
                .andExpect(status().isOk());
        assertThat(linkedConditionsOfAlgorithm(algorithmId)).isEmpty();
    }

    @Test
    void unknownHealthConditionOfAnAlgorithmIsRejectedAndNotStored() throws Exception {
        mvc.perform(post("/api/v1/algorithms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(algorithmBody(null, List.of(UUID.randomUUID()))))
                .andExpect(status().isNotFound());

        assertThat(jdbc.queryForObject("SELECT count(*) FROM t_algorithm", Integer.class)).isZero();
    }

    @Test
    void migrationCreatesTheHealthConditionTableWithAuditColumns() {
        final var columns = jdbc.queryForList(
                "SELECT column_name, data_type, character_maximum_length, is_nullable FROM information_schema.columns "
                        + "WHERE table_name = 't_health_condition' ORDER BY column_name");

        assertThat(columns).extracting(c -> c.get("column_name")).containsExactlyInAnyOrder(
                "id", "name", "created_by", "created_at", "updated_by", "updated_at", "is_deleted", "version");
        assertThat(columns).filteredOn(c -> "name".equals(c.get("column_name"))).singleElement().satisfies(c -> {
            assertThat(c.get("data_type")).isEqualTo("character varying");
            assertThat(c.get("character_maximum_length")).isEqualTo(255);
            assertThat(c.get("is_nullable")).isEqualTo("NO");
        });
    }

    @Test
    void relationTablesHaveCompositeKeysAndTheirDeleteRules() {
        for (String table : List.of("t_patient_health_condition", "t_health_condition_algorithm")) {
            final var keyColumns = jdbc.queryForList(
                    "SELECT kcu.column_name FROM information_schema.table_constraints tc "
                            + "JOIN information_schema.key_column_usage kcu ON kcu.constraint_name = tc.constraint_name "
                            + "WHERE tc.table_name = ? AND tc.constraint_type = 'PRIMARY KEY'", String.class, table);
            assertThat(keyColumns).hasSize(2);
        }

        assertThat(deleteRules("t_patient_health_condition")).containsEntry("patient_id", "CASCADE")
                .containsEntry("condition_id", "RESTRICT");
        assertThat(deleteRules("t_health_condition_algorithm")).containsEntry("condition_id", "CASCADE")
                .containsEntry("algorithm_id", "RESTRICT");
    }

    private Map<String, String> deleteRules(final String table) {
        final Map<String, String> rules = new HashMap<>();
        jdbc.query("SELECT kcu.column_name, rc.delete_rule "
                        + "FROM information_schema.referential_constraints rc "
                        + "JOIN information_schema.key_column_usage kcu ON kcu.constraint_name = rc.constraint_name "
                        + "WHERE kcu.table_name = ?",
                rs -> {
                    rules.put(rs.getString("column_name"), rs.getString("delete_rule"));
                }, table);
        return rules;
    }

    @Test
    void aLinkedHealthConditionCannotBeDeletedAndDeletingThePatientDropsTheLink() throws Exception {
        final UUID condition = createCondition("Test First");
        final UUID patientId = insertPatient("HC-1");
        jdbc.update("INSERT INTO t_patient_health_condition (patient_id, condition_id) VALUES (?, ?)",
                patientId, condition);

        assertThatThrownBy(() -> jdbc.update("DELETE FROM t_health_condition WHERE id = ?", condition))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO t_patient_health_condition (patient_id, condition_id) VALUES (?, ?)",
                patientId, UUID.randomUUID()))
                .isInstanceOf(DataIntegrityViolationException.class);

        jdbc.update("DELETE FROM t_patient WHERE id = ?", patientId);
        assertThat(linkedConditionsOfPatient(patientId)).isEmpty();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM t_health_condition WHERE id = ?", Integer.class,
                condition)).isEqualTo(1);
    }
}
