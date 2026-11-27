package si.result.project.eearly.department;

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
import si.result.project.eearly.dao.department.DepartmentDAO;
import si.result.project.eearly.dao.organization.OrganizationDAO;
import si.result.project.eearly.facade.department.DepartmentServiceFacade;
import si.result.project.eearly.facade.organization.OrganizationServiceFacade;
import si.result.project.eearly.mapper.department.DepartmentMapper;
import si.result.project.eearly.mapper.department.DepartmentMapperImpl;
import si.result.project.eearly.mapper.organization.OrganizationMapper;
import si.result.project.eearly.mapper.organization.OrganizationMapperImpl;
import si.result.project.eearly.model.organization.Organization;
import si.result.project.eearly.model.organization.command.CreateOrganizationCommand;
import si.result.project.eearly.port.department.DepartmentService;
import si.result.project.eearly.port.organization.OrganizationClient;
import si.result.project.eearly.port.organization.OrganizationRepository;
import si.result.project.eearly.port.organization.OrganizationService;
import si.result.project.eearly.resource.department.DepartmentController;
import si.result.project.eearly.resource.organization.OrganizationController;
import si.result.rest.filter.config.RestFilterArgumentResolver;
import si.result.spring.boot.bricks.advice.ErrorResponseHandler;

/**
 * Departments through the REST controller, facade, domain service and JPA repository against a migrated
 * PostgreSQL database (Hibernate validates the mapping against the schema), including the link to
 * organizations.
 */
@DataJpaTest
@ContextConfiguration(classes = DepartmentRestIntegrationTest.Cfg.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@TestPropertySource(properties = {
        "spring.config.name=department-rest-test",
        // The first test context of a JVM initialises logging for all later ones (the log format
        // characterization test reads service.name), so name the application as the service does.
        "spring.application.name=eearly-admin-service",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.hibernate.naming.physical-strategy=org.hibernate.boot.model.naming.PhysicalNamingStrategyStandardImpl"
})
class DepartmentRestIntegrationTest {

    @SpringBootConfiguration
    @EntityScan(basePackages = "si.result.project.eearly.model")
    @EnableJpaRepositories(basePackages = "si.result.project.eearly.dao")
    static class Cfg {

        @Bean
        OrganizationClient organizationClient() {
            return mock(OrganizationClient.class);
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
        OrganizationService organizationService(OrganizationDAO organizationDAO, OrganizationClient organizationClient) {
            return new OrganizationService(organizationDAO, organizationClient);
        }

        @Bean
        DepartmentService departmentService(DepartmentDAO departmentDAO) {
            return new DepartmentService(departmentDAO);
        }

        // Spring-managed facades, so that their transactional methods keep the session open while the
        // lazily loaded associations are mapped (as in the running service).
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
    private DepartmentServiceFacade departmentFacade;

    @Autowired
    private OrganizationServiceFacade organizationFacade;

    @Autowired
    private JdbcTemplate jdbc;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM t_department");
        jdbc.update("DELETE FROM t_organization");
        mvc = MockMvcBuilders
                .standaloneSetup(new DepartmentController(departmentFacade),
                        new OrganizationController(organizationFacade))
                .setCustomArgumentResolvers(new RestFilterArgumentResolver(), new PageableHandlerMethodArgumentResolver())
                .setControllerAdvice(new ErrorResponseHandler())
                .build();
    }

    private UUID createOrganization(final String name) {
        return organizationRepository.save(Organization.create(new CreateOrganizationCommand(name, null))).getId();
    }

    private String body(final String name, final UUID organizationId) throws Exception {
        final Map<String, Object> body = new HashMap<>();
        body.put("name", name);
        body.put("organizationId", organizationId == null ? null : organizationId.toString());
        return JSON.writeValueAsString(body);
    }

    private UUID createDepartment(final String name, final UUID organizationId) throws Exception {
        final String response = mvc.perform(post("/api/v1/departments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(name, organizationId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.payload.name").value(name))
                .andReturn().getResponse().getContentAsString();
        final JsonNode payload = JSON.readTree(response).get("payload");
        return UUID.fromString(payload.get("id").asText());
    }

    @Test
    void createdDepartmentIsStoredLinkedToItsOrganizationAndReadable() throws Exception {
        final UUID organizationId = createOrganization("Test Organization");

        final UUID id = createDepartment("Test Department", organizationId);

        assertThat(jdbc.queryForObject("SELECT name FROM t_department WHERE id = ?", String.class, id))
                .isEqualTo("Test Department");
        assertThat(jdbc.queryForObject("SELECT id_organization FROM t_department WHERE id = ?", UUID.class, id))
                .isEqualTo(organizationId);
        assertThat(jdbc.queryForObject("SELECT is_deleted FROM t_department WHERE id = ?", Boolean.class, id))
                .isFalse();

        mvc.perform(get("/api/v1/departments/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payload.id").value(id.toString()))
                .andExpect(jsonPath("$.payload.name").value("Test Department"))
                .andExpect(jsonPath("$.payload.organization.id").value(organizationId.toString()))
                .andExpect(jsonPath("$.payload.organization.name").value("Test Organization"));
    }

    @Test
    void organizationListsItsDepartments() throws Exception {
        final UUID organizationId = createOrganization("Test Organization");
        final UUID otherOrganizationId = createOrganization("Other Test Organization");
        final UUID first = createDepartment("First Department", organizationId);
        createDepartment("Second Department", organizationId);
        createDepartment("Unrelated Department", otherOrganizationId);

        mvc.perform(get("/api/v1/organizations/" + organizationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payload.name").value("Test Organization"))
                .andExpect(jsonPath("$.payload.departments.length()").value(2))
                .andExpect(jsonPath("$.payload.departments[?(@.id == '" + first + "')].name")
                        .value("First Department"));

        final UUID emptyOrganizationId = createOrganization("Empty Test Organization");
        mvc.perform(get("/api/v1/organizations/" + emptyOrganizationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payload.departments.length()").value(0));
    }

    @Test
    void departmentCanBeRenamedAndMovedToAnotherOrganization() throws Exception {
        final UUID organizationId = createOrganization("Test Organization");
        final UUID otherOrganizationId = createOrganization("Other Test Organization");
        final UUID id = createDepartment("Test Department", organizationId);

        final long versionBefore = jdbc.queryForObject("SELECT version FROM t_department WHERE id = ?", Long.class, id);

        mvc.perform(put("/api/v1/departments/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("Renamed Department", otherOrganizationId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payload.id").value(id.toString()))
                .andExpect(jsonPath("$.payload.name").value("Renamed Department"));

        assertThat(jdbc.queryForObject("SELECT name FROM t_department WHERE id = ?", String.class, id))
                .isEqualTo("Renamed Department");
        assertThat(jdbc.queryForObject("SELECT id_organization FROM t_department WHERE id = ?", UUID.class, id))
                .isEqualTo(otherOrganizationId);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM t_department", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT version FROM t_department WHERE id = ?", Long.class, id))
                .isGreaterThan(versionBefore);
    }

    @Test
    void unknownDepartmentIsNotFound() throws Exception {
        mvc.perform(get("/api/v1/departments/" + UUID.randomUUID()))
                .andExpect(status().isNotFound());

        mvc.perform(put("/api/v1/departments/" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("Test Department", createOrganization("Test Organization"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void departmentOfUnknownOrMissingOrganizationIsRejectedAndNotStored() throws Exception {
        mvc.perform(post("/api/v1/departments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("Test Department", UUID.randomUUID())))
                .andExpect(status().isNotFound());

        mvc.perform(post("/api/v1/departments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("Test Department", null)))
                .andExpect(status().isNotFound());

        assertThat(jdbc.queryForObject("SELECT count(*) FROM t_department", Integer.class)).isZero();
    }

    @Test
    void updateToUnknownOrganizationIsRejectedAndKeepsTheDepartment() throws Exception {
        final UUID organizationId = createOrganization("Test Organization");
        final UUID id = createDepartment("Test Department", organizationId);

        mvc.perform(put("/api/v1/departments/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("Renamed Department", UUID.randomUUID())))
                .andExpect(status().isNotFound());

        assertThat(jdbc.queryForObject("SELECT name FROM t_department WHERE id = ?", String.class, id))
                .isEqualTo("Test Department");
        assertThat(jdbc.queryForObject("SELECT id_organization FROM t_department WHERE id = ?", UUID.class, id))
                .isEqualTo(organizationId);
    }

    @Test
    void departmentWithoutNameIsRejectedAndNotStored() throws Exception {
        final UUID organizationId = createOrganization("Test Organization");

        mvc.perform(post("/api/v1/departments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(null, organizationId)))
                .andExpect(status().isBadRequest());

        final UUID id = createDepartment("Test Department", organizationId);
        mvc.perform(put("/api/v1/departments/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(null, organizationId)))
                .andExpect(status().isBadRequest());

        assertThat(jdbc.queryForObject("SELECT count(*) FROM t_department", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT name FROM t_department WHERE id = ?", String.class, id))
                .isEqualTo("Test Department");
    }

    @Test
    void listIsPagedSortedAndFiltered() throws Exception {
        final UUID organizationId = createOrganization("Test Organization");
        createDepartment("Alpha Ward", organizationId);
        createDepartment("Beta Unit", organizationId);
        createDepartment("Gamma Ward", organizationId);

        mvc.perform(get("/api/v1/departments").param("sort", "name,asc").param("size", "2").param("page", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payload.content.length()").value(2))
                .andExpect(jsonPath("$.payload.content[0].name").value("Alpha Ward"))
                .andExpect(jsonPath("$.payload.content[1].name").value("Beta Unit"))
                .andExpect(jsonPath("$.payload.page.totalElements").value(3))
                .andExpect(jsonPath("$.payload.page.totalPages").value(2));

        mvc.perform(get("/api/v1/departments").param("sort", "name,asc").param("size", "2").param("page", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payload.content.length()").value(1))
                .andExpect(jsonPath("$.payload.content[0].name").value("Gamma Ward"));

        mvc.perform(get("/api/v1/departments").param("filter", "name:LIKE:Ward").param("sort", "name,desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payload.content.length()").value(2))
                .andExpect(jsonPath("$.payload.content[0].name").value("Gamma Ward"))
                .andExpect(jsonPath("$.payload.content[1].name").value("Alpha Ward"))
                .andExpect(jsonPath("$.payload.page.totalElements").value(2));

        mvc.perform(get("/api/v1/departments").param("filter", "name:EQ:Beta Unit"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payload.content.length()").value(1))
                .andExpect(jsonPath("$.payload.content[0].name").value("Beta Unit"));
    }

    @Test
    void migrationCreatesTheDepartmentTableWithAuditColumns() {
        final var columns = jdbc.queryForList(
                "SELECT column_name, data_type, character_maximum_length, is_nullable FROM information_schema.columns "
                        + "WHERE table_name = 't_department' ORDER BY column_name");

        assertThat(columns).extracting(c -> c.get("column_name")).containsExactlyInAnyOrder(
                "id", "name", "id_organization", "created_by", "created_at", "updated_by", "updated_at",
                "is_deleted", "version");
        assertThat(columns).filteredOn(c -> "name".equals(c.get("column_name"))).singleElement().satisfies(c -> {
            assertThat(c.get("data_type")).isEqualTo("character varying");
            assertThat(c.get("character_maximum_length")).isEqualTo(255);
            assertThat(c.get("is_nullable")).isEqualTo("NO");
        });
        assertThat(columns).filteredOn(c -> "id_organization".equals(c.get("column_name"))).singleElement()
                .satisfies(c -> {
                    assertThat(c.get("data_type")).isEqualTo("uuid");
                    assertThat(c.get("is_nullable")).isEqualTo("NO");
                });
    }

    @Test
    void foreignKeyToOrganizationRestrictsDeletionAndRejectsUnknownOrganizations() throws Exception {
        final var foreignKeys = jdbc.queryForList(
                "SELECT rc.delete_rule, ccu.table_name AS referenced_table, kcu.column_name "
                        + "FROM information_schema.referential_constraints rc "
                        + "JOIN information_schema.key_column_usage kcu ON kcu.constraint_name = rc.constraint_name "
                        + "JOIN information_schema.constraint_column_usage ccu ON ccu.constraint_name = rc.constraint_name "
                        + "WHERE kcu.table_name = 't_department'");
        assertThat(foreignKeys).singleElement().satisfies(fk -> {
            assertThat(fk.get("column_name")).isEqualTo("id_organization");
            assertThat(fk.get("referenced_table")).isEqualTo("t_organization");
            assertThat(fk.get("delete_rule")).isEqualTo("RESTRICT");
        });

        final UUID organizationId = createOrganization("Test Organization");
        createDepartment("Test Department", organizationId);

        assertThatThrownBy(() -> jdbc.update("DELETE FROM t_organization WHERE id = ?", organizationId))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO t_department (id, name, id_organization) VALUES (?, 'Test Department', ?)",
                UUID.randomUUID(), UUID.randomUUID()))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM t_organization", Integer.class)).isEqualTo(1);
    }
}
