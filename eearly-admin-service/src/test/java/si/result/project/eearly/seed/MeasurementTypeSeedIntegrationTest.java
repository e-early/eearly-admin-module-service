package si.result.project.eearly.seed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
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
import si.result.project.eearly.dao.measurement.MeasurementDAO;
import si.result.project.eearly.facade.measurement.MeasurementServiceFacade;
import si.result.project.eearly.mapper.measurement.MeasurementMapperImpl;
import si.result.project.eearly.model.measurement.Measurement;
import si.result.project.eearly.model.measurement.MeasurementType;
import si.result.project.eearly.port.measurement.MeasurementService;
import si.result.project.eearly.resource.measurement.MeasurementController;
import si.result.rest.filter.config.RestFilterArgumentResolver;

/**
 * Migrates an empty PostgreSQL database with all Flyway migrations and checks that every measurement type
 * that is meant to be selectable has a catalogue row, and that the service and REST listing return them.
 */
@DataJpaTest
@ContextConfiguration(classes = MeasurementTypeSeedIntegrationTest.Cfg.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@TestPropertySource(properties = {
        "spring.config.name=measurement-type-seed-test",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.hibernate.naming.physical-strategy=org.hibernate.boot.model.naming.PhysicalNamingStrategyStandardImpl"
})
class MeasurementTypeSeedIntegrationTest {

    @SpringBootConfiguration
    @EntityScan(basePackages = "si.result.project.eearly.model")
    @EnableJpaRepositories(basePackageClasses = MeasurementDAO.class)
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

    /** Enum values that are intentionally selectable, i.e. have exactly one catalogue row. */
    private static final Set<MeasurementType> SELECTABLE = EnumSet.of(
            MeasurementType.BLOOD_PRESSURE,
            MeasurementType.BLOOD_PRESSURE_SYSTOLIC,
            MeasurementType.BLOOD_PRESSURE_DIASTOLIC,
            MeasurementType.HEART_RATE,
            MeasurementType.WEIGHT,
            MeasurementType.HEIGHT,
            MeasurementType.BODY_TEMPERATURE,
            MeasurementType.OXYGEN_SATURATION,
            MeasurementType.BLOOD_GLUCOSE,
            MeasurementType.HEART_RATE_VARIABILITY,
            MeasurementType.STRESS_LEVEL,
            MeasurementType.GLUCOSE_VARIABILITY,
            MeasurementType.BODY_FAT_PERCENTAGE,
            MeasurementType.BODY_WATER_PERCENTAGE,
            MeasurementType.BODY_MASS_INDEX,
            MeasurementType.FAT_FREE_MASS,
            MeasurementType.FAT_MASS,
            MeasurementType.SKELETAL_MUSCLE_MASS,
            MeasurementType.BASAL_METABOLIC_RATE,
            MeasurementType.FAT_FREE_MASS_PERCENTAGE,
            MeasurementType.SKELETAL_MUSCLE_MASS_PERCENTAGE,
            MeasurementType.BODY_WATER_MASS);

    /** Enum values that exist for forward compatibility only and have no catalogue row. */
    private static final Set<MeasurementType> NOT_SELECTABLE = EnumSet.of(
            MeasurementType.DAILY_STEPS,
            MeasurementType.MUSCLE_MASS,
            MeasurementType.SLEEP_DURATION,
            MeasurementType.SLEEP_QUALITY,
            MeasurementType.ECG,
            MeasurementType.BLOOD_PRESSURE_VARIABILITY,
            MeasurementType.PEAK_FLOW,
            MeasurementType.SKIN_TEMPERATURE,
            MeasurementType.RESPIRATORY_RATE,
            MeasurementType.BASIC_VISION_SCREENING);

    @Autowired
    private MeasurementDAO measurementDAO;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void everyEnumValueIsClassifiedAsSelectableOrNot() {
        // A new enum value forces a decision here: add it to SELECTABLE (and seed a row) or to NOT_SELECTABLE.
        assertThat(SELECTABLE).doesNotContainAnyElementsOf(NOT_SELECTABLE);
        assertThat(EnumSet.copyOf(Arrays.asList(MeasurementType.values())))
                .isEqualTo(union(SELECTABLE, NOT_SELECTABLE));
    }

    @Test
    void everySelectableTypeHasExactlyOneCatalogueRow() {
        List<Measurement> all = measurementDAO.findAll();
        Map<MeasurementType, Long> perType = all.stream()
                .collect(Collectors.groupingBy(Measurement::getType, Collectors.counting()));

        assertThat(perType.keySet()).containsExactlyInAnyOrderElementsOf(SELECTABLE);
        assertThat(perType.values()).allMatch(count -> count == 1L);
        assertThat(all).allSatisfy(m -> {
            assertThat(m.getName()).isNotBlank();
            assertThat(m.getEhrObservationId()).isNotBlank();
            assertThat(m.getChartType()).isNotNull();
            assertThat(m.isDeleted()).isFalse();
        });
    }

    @Test
    void seededRowsKeepTheirFixedIds() {
        assertThat(idOf("HEART_RATE_VARIABILITY")).isEqualTo("fedcba98-7654-3210-fedc-ba9876543217");
        assertThat(idOf("STRESS_LEVEL")).isEqualTo("fedcba98-7654-3210-fedc-ba9876543218");
        assertThat(idOf("GLUCOSE_VARIABILITY")).isEqualTo("fedcba98-7654-3210-fedc-ba9876543219");
        assertThat(idOf("BLOOD_PRESSURE_SYSTOLIC")).isEqualTo("fedcba98-7654-3210-fedc-ba9876543220");
        assertThat(idOf("BLOOD_PRESSURE_DIASTOLIC")).isEqualTo("fedcba98-7654-3210-fedc-ba9876543221");
        assertThat(idOf("BODY_WATER_MASS")).isEqualTo("a5d100d6-0edd-4ac3-8f63-d3c523e4cb3b");
    }

    @Test
    void serviceListingReturnsAllSelectableTypes() {
        var service = new MeasurementService(measurementDAO);
        var page = service.findAll(null, PageRequest.of(0, 100));

        assertThat(page.getTotalElements()).isEqualTo(SELECTABLE.size());
        assertThat(page.getContent().stream().map(Measurement::getType).collect(Collectors.toSet()))
                .isEqualTo(SELECTABLE);
    }

    @Test
    void restListingReturnsAllSelectableTypes() throws Exception {
        MockMvc mvc = mvc();

        mvc.perform(get("/api/v1/measurements").param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payload.page.totalElements").value(SELECTABLE.size()))
                .andExpect(jsonPath("$.payload.content[?(@.type=='HEART_RATE_VARIABILITY')]").isNotEmpty())
                .andExpect(jsonPath("$.payload.content[?(@.type=='STRESS_LEVEL')]").isNotEmpty())
                .andExpect(jsonPath("$.payload.content[?(@.type=='GLUCOSE_VARIABILITY')]").isNotEmpty())
                .andExpect(jsonPath("$.payload.content[?(@.type=='BLOOD_PRESSURE_SYSTOLIC')]").isNotEmpty())
                .andExpect(jsonPath("$.payload.content[?(@.type=='BLOOD_PRESSURE_DIASTOLIC')]").isNotEmpty())
                .andExpect(jsonPath("$.payload.content[?(@.type=='BODY_MASS_INDEX')]").isNotEmpty())
                .andExpect(jsonPath("$.payload.content[?(@.type=='BODY_WATER_MASS')]").isNotEmpty());
    }

    @Test
    void restLookupByIdReturnsSeededType() throws Exception {
        mvc().perform(get("/api/v1/measurements/fedcba98-7654-3210-fedc-ba9876543219"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payload.type").value("GLUCOSE_VARIABILITY"))
                .andExpect(jsonPath("$.payload.chartType").value("LINE"));
    }

    private MockMvc mvc() {
        var facade = new MeasurementServiceFacade(new MeasurementService(measurementDAO), new MeasurementMapperImpl());
        return MockMvcBuilders.standaloneSetup(new MeasurementController(facade))
                .setCustomArgumentResolvers(new RestFilterArgumentResolver(), new PageableHandlerMethodArgumentResolver())
                .build();
    }

    private String idOf(String type) {
        return jdbc.queryForObject("SELECT id::text FROM t_measurement WHERE type = ?::e_measurement_type", String.class, type);
    }

    private static Set<MeasurementType> union(Set<MeasurementType> a, Set<MeasurementType> b) {
        EnumSet<MeasurementType> result = EnumSet.copyOf(a);
        result.addAll(b);
        return result;
    }
}
