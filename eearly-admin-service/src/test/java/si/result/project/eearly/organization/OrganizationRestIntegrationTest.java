package si.result.project.eearly.organization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import si.result.project.eearly.dao.organization.OrganizationDAO;
import si.result.project.eearly.facade.organization.OrganizationServiceFacade;
import si.result.project.eearly.mapper.organization.OrganizationMapperImpl;
import si.result.project.eearly.dto.organization.OrganizationUpsertDTO;
import si.result.project.eearly.dto.department.DepartmentLiteDTO;
import si.result.project.eearly.mapper.organization.OrganizationMapper;
import si.result.project.eearly.model.organization.Organization;
import si.result.project.eearly.port.organization.OrganizationClient;
import si.result.project.eearly.port.organization.OrganizationService;
import si.result.project.eearly.resource.organization.OrganizationController;
import si.result.rest.filter.config.RestFilterArgumentResolver;
import si.result.spring.boot.bricks.advice.ErrorResponseHandler;

/**
 * Organizations through the REST controller, facade, domain service and JPA repository against a migrated
 * PostgreSQL database (Hibernate validates the mapping against the schema).
 */
@DataJpaTest
@ContextConfiguration(classes = OrganizationRestIntegrationTest.Cfg.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@TestPropertySource(properties = {
        "spring.config.name=organization-rest-test",
        // The first test context of a JVM initialises logging for all later ones (the log format
        // characterization test reads service.name), so name the application as the service does.
        "spring.application.name=eearly-admin-service",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.hibernate.naming.physical-strategy=org.hibernate.boot.model.naming.PhysicalNamingStrategyStandardImpl"
})
class OrganizationRestIntegrationTest {

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
        OrganizationService organizationService(OrganizationDAO organizationDAO, OrganizationClient organizationClient) {
            return new OrganizationService(organizationDAO, organizationClient);
        }

        // A Spring-managed facade, so that its transactional methods keep the session open while the
        // lazily loaded departments are mapped (as in the running service).
        @Bean
        OrganizationServiceFacade organizationServiceFacade(OrganizationService organizationService,
                OrganizationMapper organizationMapper) {
            return new OrganizationServiceFacade(organizationService, organizationMapper);
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
    private OrganizationServiceFacade facade;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private OrganizationClient organizationClient;

    @Autowired
    private OrganizationService organizationService;

    @Autowired
    private OrganizationMapper organizationMapper;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM t_department");
        jdbc.update("DELETE FROM t_organization");
        reset(organizationClient);
        mvc = MockMvcBuilders.standaloneSetup(new OrganizationController(facade))
                .setCustomArgumentResolvers(new RestFilterArgumentResolver(), new PageableHandlerMethodArgumentResolver())
                .setControllerAdvice(new ErrorResponseHandler())
                .build();
    }

    private UUID createOrganization(final String name) throws Exception {
        final String response = mvc.perform(post("/api/v1/organizations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.writeValueAsString(Map.of("name", name))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.payload.name").value(name))
                .andReturn().getResponse().getContentAsString();
        final JsonNode payload = JSON.readTree(response).get("payload");
        return UUID.fromString(payload.get("id").asText());
    }

    @Test
    void createdOrganizationIsStoredReadableAndSyncedAsFacility() throws Exception {
        final UUID id = createOrganization("Test Organization");

        assertThat(jdbc.queryForObject("SELECT name FROM t_organization WHERE id = ?", String.class, id))
                .isEqualTo("Test Organization");
        assertThat(jdbc.queryForObject("SELECT is_deleted FROM t_organization WHERE id = ?", Boolean.class, id))
                .isFalse();
        verify(organizationClient).upsertFacility(any(Organization.class));

        mvc.perform(get("/api/v1/organizations/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payload.id").value(id.toString()))
                .andExpect(jsonPath("$.payload.name").value("Test Organization"));
    }

    private UUID insertDepartment(final UUID organizationId, final String name) {
        final UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO t_department (id, name, id_organization) VALUES (?, ?, ?)", id, name, organizationId);
        return id;
    }

    @Test
    void createAcceptsDepartmentListButDoesNotCreateDepartmentsFromIt() throws Exception {
        final String payload = JSON.writeValueAsString(Map.of(
                "name", "Test Organization",
                "departmentList", List.of(
                        Map.of("name", "Test Department"),
                        Map.of("id", UUID.randomUUID().toString(), "name", "Other Test Department"))));

        final String response = mvc.perform(post("/api/v1/organizations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.payload.name").value("Test Organization"))
                .andExpect(jsonPath("$.payload.departmentList").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        final UUID id = UUID.fromString(JSON.readTree(response).get("payload").get("id").asText());

        assertThat(jdbc.queryForObject("SELECT name FROM t_organization WHERE id = ?", String.class, id))
                .isEqualTo("Test Organization");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM t_department", Integer.class)).isZero();
        verify(organizationClient).upsertFacility(any(Organization.class));
    }

    @Test
    void updateWithDepartmentListRenamesOnlyAndLeavesDepartmentsUntouched() {
        final UUID id = organizationService.create(organizationMapper.toCreateCommand(
                new OrganizationUpsertDTO(null, "Test Organization", null))).getId();
        final UUID departmentId = insertDepartment(id, "Test Department");
        final long versionBefore = jdbc.queryForObject("SELECT version FROM t_department WHERE id = ?", Long.class, departmentId);

        final OrganizationUpsertDTO dto = new OrganizationUpsertDTO(null, "Renamed Organization", List.of(
                new DepartmentLiteDTO(departmentId, "Changed Department"),
                new DepartmentLiteDTO(null, "New Test Department")));
        final OrganizationUpsertDTO result = new TransactionTemplate(transactionManager).execute(status ->
                organizationMapper.toUpsertDTO(organizationService.update(organizationMapper.toUpdateCommand(id, dto))));

        assertThat(result.name()).isEqualTo("Renamed Organization");
        assertThat(result.departmentList()).isNull();
        assertThat(jdbc.queryForObject("SELECT name FROM t_organization WHERE id = ?", String.class, id))
                .isEqualTo("Renamed Organization");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM t_department", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT name FROM t_department WHERE id = ?", String.class, departmentId))
                .isEqualTo("Test Department");
        assertThat(jdbc.queryForObject("SELECT id_organization FROM t_department WHERE id = ?", UUID.class, departmentId))
                .isEqualTo(id);
        assertThat(jdbc.queryForObject("SELECT version FROM t_department WHERE id = ?", Long.class, departmentId))
                .isEqualTo(versionBefore);
        verify(organizationClient, org.mockito.Mockito.times(2)).upsertFacility(any(Organization.class));
    }

    @Test
    void unknownOrganizationIsNotFound() throws Exception {
        mvc.perform(get("/api/v1/organizations/" + UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    @Test
    void organizationWithoutNameIsRejectedAndNotStored() throws Exception {
        mvc.perform(post("/api/v1/organizations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        assertThat(jdbc.queryForObject("SELECT count(*) FROM t_organization", Integer.class)).isZero();
        verify(organizationClient, never()).upsertFacility(any());
    }

    @Test
    void listIsPagedSortedAndFilteredByName() throws Exception {
        createOrganization("Alpha Clinic");
        createOrganization("Beta Hospital");
        createOrganization("Gamma Clinic");

        mvc.perform(get("/api/v1/organizations").param("sort", "name,asc").param("size", "2").param("page", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payload.content.length()").value(2))
                .andExpect(jsonPath("$.payload.content[0].name").value("Alpha Clinic"))
                .andExpect(jsonPath("$.payload.content[1].name").value("Beta Hospital"))
                .andExpect(jsonPath("$.payload.page.totalElements").value(3))
                .andExpect(jsonPath("$.payload.page.totalPages").value(2));

        mvc.perform(get("/api/v1/organizations").param("sort", "name,asc").param("size", "2").param("page", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payload.content.length()").value(1))
                .andExpect(jsonPath("$.payload.content[0].name").value("Gamma Clinic"));

        mvc.perform(get("/api/v1/organizations").param("filter", "name:LIKE:Clinic").param("sort", "name,desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payload.content.length()").value(2))
                .andExpect(jsonPath("$.payload.content[0].name").value("Gamma Clinic"))
                .andExpect(jsonPath("$.payload.content[1].name").value("Alpha Clinic"))
                .andExpect(jsonPath("$.payload.page.totalElements").value(2));

        mvc.perform(get("/api/v1/organizations").param("filter", "name:EQ:Beta Hospital"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payload.content.length()").value(1))
                .andExpect(jsonPath("$.payload.content[0].name").value("Beta Hospital"));
    }

    @Test
    void migrationCreatesTheOrganizationTableWithAuditColumns() {
        final var columns = jdbc.queryForList(
                "SELECT column_name, data_type, character_maximum_length, is_nullable FROM information_schema.columns "
                        + "WHERE table_name = 't_organization' ORDER BY column_name");

        assertThat(columns).extracting(c -> c.get("column_name")).containsExactlyInAnyOrder(
                "id", "name", "created_by", "created_at", "updated_by", "updated_at", "is_deleted", "version");
        assertThat(columns).filteredOn(c -> "name".equals(c.get("column_name"))).singleElement().satisfies(c -> {
            assertThat(c.get("data_type")).isEqualTo("character varying");
            assertThat(c.get("character_maximum_length")).isEqualTo(255);
            assertThat(c.get("is_nullable")).isEqualTo("NO");
        });
    }
}
