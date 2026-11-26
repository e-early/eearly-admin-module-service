package si.result.project.eearly.caretaker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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
import si.result.project.eearly.dao.caretaker.CaretakerDAO;
import si.result.project.eearly.dao.department.DepartmentDAO;
import si.result.project.eearly.dao.organization.OrganizationDAO;
import si.result.project.eearly.facade.caretaker.CaretakerServiceFacade;
import si.result.project.eearly.facade.department.DepartmentServiceFacade;
import si.result.project.eearly.facade.organization.OrganizationServiceFacade;
import si.result.project.eearly.mapper.caretaker.CaretakerMapper;
import si.result.project.eearly.mapper.caretaker.CaretakerMapperImpl;
import si.result.project.eearly.mapper.department.DepartmentMapper;
import si.result.project.eearly.mapper.department.DepartmentMapperImpl;
import si.result.project.eearly.mapper.organization.OrganizationMapper;
import si.result.project.eearly.mapper.organization.OrganizationMapperImpl;
import si.result.project.eearly.model.caretaker.Caretaker;
import si.result.project.eearly.model.department.Department;
import si.result.project.eearly.model.department.command.CreateDepartmentCommand;
import si.result.project.eearly.model.organization.Organization;
import si.result.project.eearly.model.organization.command.CreateOrganizationCommand;
import si.result.project.eearly.port.caretaker.CaretakerClient;
import si.result.project.eearly.port.caretaker.CaretakerService;
import si.result.project.eearly.port.department.DepartmentRepository;
import si.result.project.eearly.port.department.DepartmentService;
import si.result.project.eearly.port.organization.OrganizationClient;
import si.result.project.eearly.port.organization.OrganizationRepository;
import si.result.project.eearly.port.organization.OrganizationService;
import si.result.project.eearly.resource.caretaker.CaretakerController;
import si.result.project.eearly.resource.department.DepartmentController;
import si.result.project.eearly.resource.organization.OrganizationController;
import si.result.rest.filter.config.RestFilterArgumentResolver;
import si.result.spring.boot.bricks.advice.ErrorResponseHandler;

/**
 * Caretakers with their organization and department through the REST controller, facade, domain service and
 * JPA repository against a migrated PostgreSQL database (Hibernate validates the mapping against the schema).
 */
@DataJpaTest
@ContextConfiguration(classes = CaretakerRestIntegrationTest.Cfg.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@TestPropertySource(properties = {
        "spring.config.name=caretaker-rest-test",
        // The first test context of a JVM initialises logging for all later ones (the log format
        // characterization test reads service.name), so name the application as the service does.
        "spring.application.name=eearly-admin-service",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.hibernate.naming.physical-strategy=org.hibernate.boot.model.naming.PhysicalNamingStrategyStandardImpl"
})
class CaretakerRestIntegrationTest {

    @SpringBootConfiguration
    @EntityScan(basePackages = "si.result.project.eearly.model")
    @EnableJpaRepositories(basePackages = "si.result.project.eearly.dao")
    static class Cfg {

        @Bean
        CaretakerClient caretakerClient() {
            return mock(CaretakerClient.class);
        }

        @Bean
        OrganizationClient organizationClient() {
            return mock(OrganizationClient.class);
        }

        @Bean
        CaretakerMapper caretakerMapper() {
            return new CaretakerMapperImpl();
        }

        @Bean
        CaretakerService caretakerService(CaretakerDAO caretakerDAO, CaretakerClient caretakerClient) {
            return new CaretakerService(caretakerDAO, caretakerClient);
        }

        @Bean
        OrganizationService organizationService(OrganizationDAO organizationDAO, OrganizationClient organizationClient) {
            return new OrganizationService(organizationDAO, organizationClient);
        }

        @Bean
        DepartmentService departmentService(DepartmentDAO departmentDAO) {
            return new DepartmentService(departmentDAO);
        }

        @Bean
        OrganizationMapper organizationMapper() {
            return new OrganizationMapperImpl();
        }

        @Bean
        DepartmentMapper departmentMapper() {
            return new DepartmentMapperImpl();
        }

        @Bean
        OrganizationServiceFacade organizationServiceFacade(OrganizationService organizationService,
                OrganizationMapper organizationMapper) {
            return new OrganizationServiceFacade(organizationService, organizationMapper);
        }

        @Bean
        DepartmentServiceFacade departmentServiceFacade(DepartmentService departmentService,
                OrganizationService organizationService, DepartmentMapper departmentMapper) {
            return new DepartmentServiceFacade(departmentService, organizationService, departmentMapper);
        }

        // Spring-managed facades, so that their transactional methods keep the session open while the
        // lazily loaded associations are mapped (as in the running service).
        @Bean
        CaretakerServiceFacade caretakerServiceFacade(CaretakerService caretakerService,
                CaretakerMapper caretakerMapper, OrganizationService organizationService,
                DepartmentService departmentService) {
            return new CaretakerServiceFacade(caretakerService, caretakerMapper, organizationService,
                    departmentService);
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

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private DepartmentRepository departmentRepository;

    @Autowired
    private CaretakerServiceFacade caretakerFacade;

    @Autowired
    private OrganizationServiceFacade organizationFacade;

    @Autowired
    private DepartmentServiceFacade departmentFacade;

    @Autowired
    private CaretakerClient caretakerClient;

    @Autowired
    private JdbcTemplate jdbc;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        reset(caretakerClient);
        jdbc.update("DELETE FROM t_caretaker");
        jdbc.update("DELETE FROM t_department");
        jdbc.update("DELETE FROM t_organization");
        mvc = MockMvcBuilders
                .standaloneSetup(new CaretakerController(caretakerFacade),
                        new OrganizationController(organizationFacade), new DepartmentController(departmentFacade))
                .setCustomArgumentResolvers(new RestFilterArgumentResolver(), new PageableHandlerMethodArgumentResolver())
                .setControllerAdvice(new ErrorResponseHandler())
                .build();
    }

    private UUID createOrganization(final String name) {
        return organizationRepository.save(Organization.create(new CreateOrganizationCommand(name, null))).getId();
    }

    private UUID createDepartment(final String name, final UUID organizationId) {
        final Organization organization = organizationRepository.findById(organizationId).orElseThrow();
        return departmentRepository.save(Department.create(new CreateDepartmentCommand(name, organization))).getId();
    }

    private static String body(final String firstName, final UUID keycloakId, final UUID organizationId,
            final UUID departmentId) throws Exception {
        final Map<String, Object> body = new HashMap<>();
        body.put("keycloakId", keycloakId == null ? null : keycloakId.toString());
        body.put("firstName", firstName);
        body.put("lastName", "Caretaker");
        body.put("email", "test@example.org");
        body.put("language", "sl");
        body.put("organizationId", organizationId == null ? null : organizationId.toString());
        body.put("departmentId", departmentId == null ? null : departmentId.toString());
        return JSON.writeValueAsString(body);
    }

    private UUID createCaretaker(final UUID organizationId, final UUID departmentId) throws Exception {
        final String response = mvc.perform(post("/api/v1/caretakers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("Test", UUID.randomUUID(), organizationId, departmentId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        final JsonNode payload = JSON.readTree(response).get("payload");
        return UUID.fromString(payload.get("id").asText());
    }

    private int caretakerCount() {
        return jdbc.queryForObject("SELECT count(*) FROM t_caretaker", Integer.class);
    }

    @Test
    void createdCaretakerIsStoredWithOrganizationAndDepartmentSyncedAndReadable() throws Exception {
        final UUID organizationId = createOrganization("Test Organization");
        final UUID departmentId = createDepartment("Test Department", organizationId);
        final UUID keycloakId = UUID.randomUUID();

        final String response = mvc.perform(post("/api/v1/caretakers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("Test", keycloakId, organizationId, departmentId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.payload.firstName").value("Test"))
                .andExpect(jsonPath("$.payload.organization.id").value(organizationId.toString()))
                .andExpect(jsonPath("$.payload.organization.name").value("Test Organization"))
                .andExpect(jsonPath("$.payload.department.id").value(departmentId.toString()))
                .andExpect(jsonPath("$.payload.department.name").value("Test Department"))
                .andReturn().getResponse().getContentAsString();
        final UUID id = UUID.fromString(JSON.readTree(response).get("payload").get("id").asText());

        assertThat(jdbc.queryForObject("SELECT id_organization FROM t_caretaker WHERE id = ?", UUID.class, id))
                .isEqualTo(organizationId);
        assertThat(jdbc.queryForObject("SELECT id_department FROM t_caretaker WHERE id = ?", UUID.class, id))
                .isEqualTo(departmentId);

        final ArgumentCaptor<Caretaker> synced = ArgumentCaptor.forClass(Caretaker.class);
        verify(caretakerClient).upsertCaretaker(synced.capture());
        assertThat(synced.getValue().getId()).isEqualTo(id);
        assertThat(synced.getValue().getOrganization().getId()).isEqualTo(organizationId);
        assertThat(synced.getValue().getDepartment().getId()).isEqualTo(departmentId);

        mvc.perform(get("/api/v1/caretakers/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payload.organization.id").value(organizationId.toString()))
                .andExpect(jsonPath("$.payload.department.id").value(departmentId.toString()));
        mvc.perform(get("/api/v1/caretakers/keycloak/" + keycloakId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payload.id").value(id.toString()))
                .andExpect(jsonPath("$.payload.organization.name").value("Test Organization"))
                .andExpect(jsonPath("$.payload.department.name").value("Test Department"));
    }

    @Test
    void organizationAndDepartmentListTheirCaretakers() throws Exception {
        final UUID organizationId = createOrganization("Test Organization");
        final UUID otherOrganizationId = createOrganization("Other Test Organization");
        final UUID departmentId = createDepartment("Test Department", organizationId);
        final UUID otherDepartmentId = createDepartment("Other Test Department", otherOrganizationId);
        final UUID id = createCaretaker(organizationId, departmentId);
        createCaretaker(otherOrganizationId, otherDepartmentId);

        mvc.perform(get("/api/v1/organizations/" + organizationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payload.caretakers.length()").value(1))
                .andExpect(jsonPath("$.payload.caretakers[0].id").value(id.toString()));
        mvc.perform(get("/api/v1/departments/" + departmentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payload.caretakers.length()").value(1))
                .andExpect(jsonPath("$.payload.caretakers[0].id").value(id.toString()));
    }

    @Test
    void updateChangesTheDepartmentAndKeepsTheOrganization() throws Exception {
        final UUID organizationId = createOrganization("Test Organization");
        final UUID otherOrganizationId = createOrganization("Other Test Organization");
        final UUID departmentId = createDepartment("Test Department", organizationId);
        final UUID otherDepartmentId = createDepartment("Other Test Department", organizationId);
        final UUID id = createCaretaker(organizationId, departmentId);
        reset(caretakerClient);

        mvc.perform(put("/api/v1/caretakers/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("Renamed", UUID.randomUUID(), otherOrganizationId, otherDepartmentId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payload.firstName").value("Renamed"))
                .andExpect(jsonPath("$.payload.department.id").value(otherDepartmentId.toString()))
                .andExpect(jsonPath("$.payload.organization.id").value(organizationId.toString()));

        assertThat(jdbc.queryForObject("SELECT first_name FROM t_caretaker WHERE id = ?", String.class, id))
                .isEqualTo("Renamed");
        assertThat(jdbc.queryForObject("SELECT id_department FROM t_caretaker WHERE id = ?", UUID.class, id))
                .isEqualTo(otherDepartmentId);
        assertThat(jdbc.queryForObject("SELECT id_organization FROM t_caretaker WHERE id = ?", UUID.class, id))
                .isEqualTo(organizationId);
        assertThat(caretakerCount()).isEqualTo(1);
        verify(caretakerClient).upsertCaretaker(any(Caretaker.class));
    }

    @Test
    void createWithoutOrganizationOrDepartmentIsBadRequestAndNotStored() throws Exception {
        final UUID organizationId = createOrganization("Test Organization");
        final UUID departmentId = createDepartment("Test Department", organizationId);

        mvc.perform(post("/api/v1/caretakers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("Test", UUID.randomUUID(), null, departmentId)))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/caretakers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("Test", UUID.randomUUID(), organizationId, null)))
                .andExpect(status().isBadRequest());

        assertThat(caretakerCount()).isZero();
        verify(caretakerClient, never()).upsertCaretaker(any());
    }

    @Test
    void updateWithoutDepartmentIsBadRequestAndKeepsTheCaretaker() throws Exception {
        final UUID organizationId = createOrganization("Test Organization");
        final UUID departmentId = createDepartment("Test Department", organizationId);
        final UUID id = createCaretaker(organizationId, departmentId);

        mvc.perform(put("/api/v1/caretakers/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("Renamed", UUID.randomUUID(), organizationId, null)))
                .andExpect(status().isBadRequest());

        assertThat(jdbc.queryForObject("SELECT first_name FROM t_caretaker WHERE id = ?", String.class, id))
                .isEqualTo("Test");
        assertThat(jdbc.queryForObject("SELECT id_department FROM t_caretaker WHERE id = ?", UUID.class, id))
                .isEqualTo(departmentId);
    }

    @Test
    void unknownOrganizationDepartmentOrCaretakerIsNotFound() throws Exception {
        final UUID organizationId = createOrganization("Test Organization");
        final UUID departmentId = createDepartment("Test Department", organizationId);
        final UUID id = createCaretaker(organizationId, departmentId);

        mvc.perform(post("/api/v1/caretakers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("Test", UUID.randomUUID(), UUID.randomUUID(), departmentId)))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/caretakers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("Test", UUID.randomUUID(), organizationId, UUID.randomUUID())))
                .andExpect(status().isNotFound());
        mvc.perform(put("/api/v1/caretakers/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("Renamed", UUID.randomUUID(), UUID.randomUUID(), departmentId)))
                .andExpect(status().isNotFound());
        mvc.perform(put("/api/v1/caretakers/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("Renamed", UUID.randomUUID(), organizationId, UUID.randomUUID())))
                .andExpect(status().isNotFound());
        mvc.perform(put("/api/v1/caretakers/" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("Renamed", UUID.randomUUID(), organizationId, departmentId)))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/caretakers/" + UUID.randomUUID()))
                .andExpect(status().isNotFound());

        assertThat(caretakerCount()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT first_name FROM t_caretaker WHERE id = ?", String.class, id))
                .isEqualTo("Test");
    }

    @Test
    void foreignKeysRestrictDeletionAndRejectUnknownReferences() throws Exception {
        final UUID organizationId = createOrganization("Test Organization");
        final UUID departmentId = createDepartment("Test Department", organizationId);
        createCaretaker(organizationId, departmentId);

        assertThatThrownBy(() -> jdbc.update("DELETE FROM t_department WHERE id = ?", departmentId))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("DELETE FROM t_organization WHERE id = ?", organizationId))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO t_caretaker (id, id_organization) VALUES (?, ?)", UUID.randomUUID(), UUID.randomUUID()))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO t_caretaker (id, id_department) VALUES (?, ?)", UUID.randomUUID(), UUID.randomUUID()))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(caretakerCount()).isEqualTo(1);
    }
}
