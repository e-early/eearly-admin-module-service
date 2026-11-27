package si.result.project.eearly.characterization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.postgresql.util.PSQLException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.dao.InvalidDataAccessResourceUsageException;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import si.result.project.eearly.dao.patient.PatientDAO;
import si.result.project.eearly.model.patient.Patient;
import si.result.rest.filter.Filter;
import si.result.rest.filter.annotation.AttributeMapping;
import si.result.rest.filter.annotation.PathFilter;
import si.result.rest.filter.config.RestFilterArgumentResolver;
import si.result.rest.filter.config.RestFilterGenerator;
import si.result.spring.boot.bricks.advice.ErrorResponseHandler;

/**
 * Characterization of {@code si.result.lib:rest-filter} 1.4.0.
 * Part (a): how {@code @PathFilter Filter} arguments are resolved from the query string.
 * Part (b): filter semantics against PostgreSQL for the real {@link Patient} entity through
 * {@link PatientDAO} and {@code Filter.toSpecification()}.
 *
 * Every assertion records observed behaviour; methods named {@code currentDefect*} record behaviour that is
 * clearly wrong/surprising today but is pinned so that a replacement library has to match it (or consciously change it).
 */
@DataJpaTest
@ContextConfiguration(classes = RestFilterCharacterizationTest.Cfg.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@TestPropertySource(properties = {
        // do not load the service's application.yaml (it needs a real environment)
        "spring.config.name=characterization-filter-test",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.hibernate.naming.physical-strategy=org.hibernate.boot.model.naming.PhysicalNamingStrategyStandardImpl"
})
class RestFilterCharacterizationTest {

    @SpringBootConfiguration
    @EntityScan(basePackages = "si.result.project.eearly.model")
    @EnableJpaRepositories(basePackageClasses = PatientDAO.class)
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

    // =========================================================================================
    // Part (a): argument resolution through MockMvc
    // =========================================================================================

    @RestController
    static class FilterController {

        private static String describe(Filter filter) {
            if (filter == null) {
                return "filter=null";
            }
            return "predicate=" + (filter.getPredicate() == null ? "null" : "set")
                    + " spec=" + (filter.toSpecification() == null ? "null" : "set")
                    + " mappings=" + filter.getMappings();
        }

        @GetMapping("/filter")
        String filter(@PathFilter Filter filter) {
            return describe(filter);
        }

        @GetMapping("/renamed")
        String renamed(@PathFilter Filter myFilter) {
            return describe(myFilter);
        }

        @GetMapping("/mapped")
        String mapped(@PathFilter(mappings = @AttributeMapping(source = "name", target = "firstName")) Filter filter) {
            return describe(filter);
        }
    }

    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new FilterController())
            .setCustomArgumentResolvers(new RestFilterArgumentResolver())
            .setControllerAdvice(new ErrorResponseHandler())
            .build();

    private String resolved(String path, String param, String value) throws Exception {
        var request = get(path);
        if (param != null) {
            request = request.param(param, value);
        }
        return mvc.perform(request).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    @Test
    void resolverWithoutFilterParameterGivesEmptyFilterWithNullSpecification() throws Exception {
        assertThat(resolved("/filter", null, null)).isEqualTo("predicate=null spec=null mappings={}");
    }

    @Test
    void resolverWithEmptyFilterParameterGivesEmptyFilter() throws Exception {
        assertThat(resolved("/filter", "filter", "")).isEqualTo("predicate=null spec=null mappings={}");
    }

    @Test
    void resolverWithBlankFilterParameterGivesEmptyFilter() throws Exception {
        assertThat(resolved("/filter", "filter", "   ")).isEqualTo("predicate=null spec=null mappings={}");
    }

    @Test
    void resolverWithNormalFilterGivesPredicateAndSpecification() throws Exception {
        assertThat(resolved("/filter", "filter", "firstName:EQ:Anna")).isEqualTo("predicate=set spec=set mappings={}");
    }

    @Test
    void resolverReadsTheRequestParameterNamedAfterTheJavaParameterNotAfterTheAnnotationValue() throws Exception {
        assertThat(resolved("/renamed", "filter", "firstName:EQ:Anna")).isEqualTo("predicate=null spec=null mappings={}");
        assertThat(resolved("/renamed", "myFilter", "firstName:EQ:Anna")).isEqualTo("predicate=set spec=set mappings={}");
    }

    @Test
    void resolverPutsAnnotationAttributeMappingsOntoTheFilter() throws Exception {
        assertThat(resolved("/mapped", "filter", "name:EQ:Anna")).isEqualTo("predicate=set spec=set mappings={name=firstName}");
    }

    @Test
    void currentDefectMalformedFilterBecomesNullPointerAnd500UnexpectedError() throws Exception {
        // Defect: client input error surfaces as NullPointerException inside the parser -> 500 instead of 400.
        mvc.perform(get("/filter").param("filter", "garbage"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().json("{\"code\":\"UNKNOWN_EXCEPTION\",\"message\":\"Unexpected error\"}", JsonCompareMode.STRICT));
        mvc.perform(get("/filter").param("filter", "firstName:FOO:Anna"))
                .andExpect(status().isInternalServerError());
    }

    @Test
    void currentDefectMalformedFilterThrowsNullPointerExceptionFromParser() {
        assertThatThrownBy(() -> new RestFilterGenerator(Map.of()).parse("garbage"))
                .isInstanceOf(NullPointerException.class);
    }

    // =========================================================================================
    // Part (b): semantics against PostgreSQL
    // =========================================================================================

    static final UUID ANNA = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    static final UUID BOJAN = UUID.fromString("00000000-0000-0000-0000-0000000000a2");
    static final UUID CENE = UUID.fromString("00000000-0000-0000-0000-0000000000a3");
    static final UUID DANA = UUID.fromString("00000000-0000-0000-0000-0000000000a4");
    static final UUID ANNA_LOWER = UUID.fromString("00000000-0000-0000-0000-0000000000a5");
    static final UUID FRAN = UUID.fromString("00000000-0000-0000-0000-0000000000a6");
    static final UUID KC_BOJAN = UUID.fromString("00000000-0000-0000-0000-0000000000b2");

    static final String A = "Anna Novak";
    static final String B = "Bojan Kranjc";
    static final String C = "Cene Horvat";
    static final String D = "Dana O'Brien";
    static final String A2 = "anna Zupan";
    static final String F = "Fran Kos";

    @Autowired
    PatientDAO patients;

    @Autowired
    JdbcTemplate jdbc;

    /**
     * Seed: Anna Novak (Ljubljana, 1980-05-10, F, zip 1000), Bojan Kranjc (Maribor, 1975-03-02, M, zip 2000,
     * keycloak id set, caretakers Carla+Dario), Cene Horvat (Ljubljana, 1990-12-24, M, zip null),
     * Dana O'Brien (Novo Mesto, 1969-07-01, F, zip 8000), anna Zupan (Koper, 2000-01-01, OTHER, zip 6000),
     * Fran Kos (Maribor, 1985-09-09, OTHER, zip 2000, is_deleted = true). Anna is also linked to caretaker Carla.
     */
    @BeforeEach
    void seed() {
        jdbc.update("delete from t_patient_caretaker");
        jdbc.update("delete from t_caretaker");
        jdbc.update("delete from t_patient");
        insert(ANNA, "Anna", "Novak", "1980-05-10", "FEMALE", "HI-001", "Ljubljana", 1000, null, false);
        insert(BOJAN, "Bojan", "Kranjc", "1975-03-02", "MALE", "HI-002", "Maribor", 2000, KC_BOJAN, false);
        insert(CENE, "Cene", "Horvat", "1990-12-24", "MALE", "HI-003", "Ljubljana", null, null, false);
        insert(DANA, "Dana", "O'Brien", "1969-07-01", "FEMALE", "HI-004", "Novo Mesto", 8000, null, false);
        insert(ANNA_LOWER, "anna", "Zupan", "2000-01-01", "OTHER", "HI-005", "Koper", 6000, null, false);
        insert(FRAN, "Fran", "Kos", "1985-09-09", "OTHER", "HI-006", "Maribor", 2000, null, true);

        UUID carla = UUID.fromString("00000000-0000-0000-0000-0000000000c1");
        UUID dario = UUID.fromString("00000000-0000-0000-0000-0000000000c2");
        jdbc.update("insert into t_caretaker (id, first_name, last_name, email, language) values (?, 'Carla', 'Care', 'carla@example.test', 'sl')", carla);
        jdbc.update("insert into t_caretaker (id, first_name, last_name, email, language) values (?, 'Dario', 'Doc', 'dario@example.test', 'en')", dario);
        jdbc.update("insert into t_patient_caretaker (patient_id, caretaker_id) values (?, ?)", ANNA, carla);
        jdbc.update("insert into t_patient_caretaker (patient_id, caretaker_id) values (?, ?)", BOJAN, carla);
        jdbc.update("insert into t_patient_caretaker (patient_id, caretaker_id) values (?, ?)", BOJAN, dario);
    }

    private void insert(UUID id, String first, String last, String dob, String gender, String insurance,
                        String city, Integer zip, UUID keycloakId, boolean deleted) {
        jdbc.update("insert into t_patient (id, first_name, last_name, date_of_birth, gender, health_insurance_id, street, city, state, zip, country, email, phone_number, keycloak_id, is_deleted)"
                        + " values (?, ?, ?, cast(? as date), cast(? as e_gender_type), ?, 'Test street 1', ?, 'Test state', ?, 'Testland', ?, '+38600000000', ?, ?)",
                id, first, last, dob, gender, insurance, city, zip, first.toLowerCase() + "@example.test", keycloakId, deleted);
    }

    /** Same path as production: parse like the argument resolver, then findAll(spec, sort). */
    private List<String> run(String filterText) {
        Filter filter = new RestFilterGenerator(Map.of()).parse(filterText);
        Specification<Patient> spec = filter.toSpecification();
        return patients.findAll(spec, Sort.by("firstName", "lastName")).stream()
                .map(p -> p.getFirstName() + " " + p.getLastName())
                .collect(Collectors.toList());
    }

    private void assertMatches(String filterText, String... expected) {
        assertThat(run(filterText)).as(filterText).containsExactlyInAnyOrder(expected);
    }

    private static final String[] ALL = {A, B, C, D, A2, F};

    @Test
    void noSpecificationReturnsAllRows() {
        Specification<Patient> spec = new Filter().toSpecification();
        assertThat(spec).isNull();
        assertThat(patients.findAll(spec, Sort.by("firstName"))).hasSize(6);
    }

    // ---- comparison / text operators ----

    @Test
    void eqIsCaseSensitive() {
        assertMatches("firstName:EQ:Anna", A);
        assertMatches("firstName:EQ:anna", A2);
    }

    @Test
    void eqIgnoreCase() {
        assertMatches("firstName:EQ_IC:ANNA", A, A2);
    }

    @Test
    void neqAndNeqIgnoreCase() {
        assertMatches("firstName:NEQ:Anna", B, C, D, F, A2);
        assertMatches("firstName:NEQ_IC:anna", B, C, D, F);
    }

    @Test
    void likeIsContainsAndCaseSensitiveAndAddsItsOwnWildcards() {
        assertMatches("lastName:LIKE:ov", A);
        assertMatches("lastName:LIKE:NOV");
        // user-supplied % is passed through as a SQL wildcard, in addition to the implicit % on both sides
        assertMatches("lastName:LIKE:%ov%", A);
        assertMatches("lastName:LIKE:%o", A, C, F);
    }

    @Test
    void likeIgnoreCase() {
        assertMatches("lastName:LIKE_IC:NOV", A);
    }

    @Test
    void eqIsLiteralAndDoesNotTreatPercentAsWildcard() {
        assertMatches("firstName:EQ:Ann%");
    }

    // ---- IN / NIN ----

    @Test
    void inWithBracketedCommaList() {
        assertMatches("firstName:IN:[Anna,Bojan]", A, B);
        assertMatches("firstName:IN:[Anna, Bojan]", A, B);
    }

    @Test
    void inWithBareCommaListParenthesesAndQuotedElements() {
        assertMatches("firstName:IN:Anna,Bojan", A, B);
        assertMatches("firstName:IN:(Anna,Bojan)", A, B);
        assertMatches("firstName:IN:['Anna','Bojan']", A, B);
        assertMatches("city:IN:['Novo Mesto','Koper']", D, A2);
    }

    @Test
    void inWithSingleBareValue() {
        assertMatches("firstName:IN:Anna", A);
    }

    @Test
    void currentDefectInWithSingleElementBracketListMatchesNothing() {
        // Defect: "[Anna]" is taken as the literal value "[Anna]" -> no rows (should be Anna Novak).
        assertMatches("firstName:IN:[Anna]");
    }

    @Test
    void ninWithCommaListAndWithSingleBareValue() {
        assertMatches("firstName:NIN:[Anna,Bojan]", C, D, F, A2);
        assertMatches("firstName:NIN:Anna", B, C, D, F, A2);
    }

    @Test
    void currentDefectNinWithSingleElementBracketListExcludesNothing() {
        // Defect: "[Anna]" is taken as the literal value "[Anna]" -> nothing excluded (should exclude Anna Novak).
        assertMatches("firstName:NIN:[Anna]", ALL);
    }

    @Test
    void currentDefectCaseInsensitiveListOperatorsAreNotParsable() {
        // Defect: IN_IC / NIN_IC exist in the Operator enum but the grammar rejects them -> NullPointerException.
        assertThatThrownBy(() -> run("firstName:IN_IC:[ANNA,BOJAN]")).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> run("firstName:NIN_IC:[ANNA,BOJAN]")).isInstanceOf(NullPointerException.class);
    }

    // ---- dates, numbers, null checks, booleans ----

    @Test
    void dateComparisons() {
        assertMatches("dateOfBirth:GT:1980-01-01", A, C, F, A2);
        assertMatches("dateOfBirth:GTE:1980-05-10", A, C, F, A2);
        assertMatches("dateOfBirth:LT:1980-05-10", B, D);
        assertMatches("dateOfBirth:LTE:1980-05-10", A, B, D);
        assertMatches("dateOfBirth:EQ:1980-05-10", A);
        assertMatches("dateOfBirth:GT:1980-01-01T00:00:00", A, C, F, A2);
    }

    @Test
    void invalidDateValueFailsAtQueryTimeAsInvalidDataAccessApiUsage() {
        assertThatThrownBy(() -> run("dateOfBirth:GT:notadate"))
                .isInstanceOf(InvalidDataAccessApiUsageException.class)
                .hasRootCauseInstanceOf(DateTimeParseException.class);
    }

    @Test
    void numberComparisonsOnIntegerField() {
        assertMatches("zip:GT:1500", B, D, F, A2);
        assertMatches("zip:LT:2000", A);
        assertMatches("zip:GTE:2000", B, D, F, A2);
        assertMatches("zip:EQ:1000", A);
        assertMatches("zip:IN:[1000,2000]", A, B, F);
    }

    @Test
    void invalidNumberValueFailsAtQueryTime() {
        assertThatThrownBy(() -> run("zip:EQ:abc")).isInstanceOf(InvalidDataAccessApiUsageException.class);
    }

    @Test
    void isNullAndIsNotNull() {
        assertMatches("zip:ISNULL", C);
        assertMatches("zip:ISNOTNULL", A, B, D, F, A2);
        assertMatches("keycloakId:ISNOTNULL", B);
    }

    @Test
    void isTrueAndIsFalseOnBooleanField() {
        assertMatches("isDeleted:ISTRUE", F);
        assertMatches("isDeleted:ISFALSE", A, B, C, D, A2);
    }

    // ---- logical operators ----

    @Test
    void andOrNot() {
        assertMatches("firstName:EQ:Anna AND city:EQ:Ljubljana", A);
        assertMatches("firstName:EQ:Anna OR firstName:EQ:Bojan", A, B);
        assertMatches("NOT firstName:EQ:Anna", B, C, D, F, A2);
        assertMatches("firstName:EQ:Anna AND NOT city:EQ:Koper", A);
    }

    @Test
    void parenthesesGroupAndNestedNot() {
        assertMatches("(firstName:EQ:Anna OR firstName:EQ:Bojan) AND city:EQ:Ljubljana", A);
        assertMatches("NOT (firstName:EQ:Anna OR firstName:EQ:Bojan)", C, D, F, A2);
        assertMatches("((firstName:EQ:Anna))", A);
    }

    @Test
    void mixedAndOrWithoutParenthesesDoesNotFollowSqlPrecedence() {
        // Observed: "A OR B AND C" -> [Bojan] (i.e. (A OR B) AND C), unlike SQL where AND binds tighter.
        assertMatches("firstName:EQ:Anna OR firstName:EQ:Bojan AND city:EQ:Maribor", B);
        // Observed: "B AND C OR A" -> [Anna, Bojan]
        assertMatches("firstName:EQ:Bojan AND city:EQ:Maribor OR firstName:EQ:Anna", A, B);
    }

    @Test
    void currentDefectLowercaseLogicalAndOperatorsAreNotRecognised() {
        // Defect: "and" is swallowed into the value ("Anna and city:EQ:Ljubljana") -> silently no rows.
        assertMatches("firstName:EQ:Anna and city:EQ:Ljubljana");
        // Defect: lowercase "not" / operator names are a parser NullPointerException.
        assertThatThrownBy(() -> run("not firstName:EQ:Anna")).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> run("firstName:eq:Anna")).isInstanceOf(NullPointerException.class);
    }

    // ---- quoting and spaces ----

    @Test
    void quotedValuesAndEscapedQuote() {
        assertMatches("lastName:EQ:'Novak'", A);
        assertMatches("lastName:EQ:'O\\'Brien'", D);
        assertMatches("city:LIKE:'Novo M'", D);
    }

    @Test
    void valueWithSpacesWorksQuotedUnquotedAndDoubleQuoted() {
        assertMatches("city:EQ:'Novo Mesto'", D);
        assertMatches("city:EQ:Novo Mesto", D);
        assertMatches("city:EQ:\"Novo Mesto\"", D);
        assertMatches("city:LIKE:Novo M", D);
    }

    @Test
    void unquotedApostropheInValueMatchesNothing() {
        assertMatches("lastName:EQ:O'Brien");
    }

    // ---- enum, uuid ----

    @Test
    void enumFieldEqNeqInAreCaseInsensitiveInTheValue() {
        assertMatches("gender:EQ:MALE", B, C);
        assertMatches("gender:EQ:male", B, C);
        assertMatches("gender:NEQ:MALE", A, D, F, A2);
        assertMatches("gender:IN:[MALE,OTHER]", B, C, F, A2);
    }

    @Test
    void unknownEnumValueFailsAtQueryTime() {
        assertThatThrownBy(() -> run("gender:EQ:BOGUS"))
                .isInstanceOf(InvalidDataAccessApiUsageException.class)
                .hasRootCauseInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void likeOnEnumFieldFailsInDatabase() {
        assertThatThrownBy(() -> run("gender:LIKE:MAL"))
                .isInstanceOf(InvalidDataAccessResourceUsageException.class)
                .hasRootCauseInstanceOf(PSQLException.class);
    }

    @Test
    void uuidFieldsWorkForEqNeqInUnquotedAndQuoted() {
        assertMatches("id:EQ:" + ANNA, A);
        assertMatches("id:EQ:'" + ANNA + "'", A);
        assertMatches("id:IN:[" + ANNA + "," + BOJAN + "]", A, B);
        assertMatches("id:NEQ:" + ANNA, B, C, D, F, A2);
        assertMatches("keycloakId:EQ:" + KC_BOJAN, B);
    }

    @Test
    void invalidUuidFailsAtQueryTime() {
        assertThatThrownBy(() -> run("id:EQ:not-a-uuid"))
                .isInstanceOf(InvalidDataAccessApiUsageException.class)
                .hasRootCauseInstanceOf(IllegalArgumentException.class);
    }

    // ---- relation paths ----

    @Test
    void dottedPathToCollectionRelationUsesLeftJoin() {
        assertMatches("caretakers.firstName:EQ:Carla", A, B);
        assertMatches("caretakers.lastName:LIKE:Do", B);
        assertMatches("caretakers.firstName:ISNOTNULL", A, B);
        assertMatches("caretakers.firstName:ISNULL");
    }

    @Test
    void dottedConditionsOnTheSameRelationShareOneJoinRow() {
        // Both conditions are evaluated on the same joined caretaker row, so no patient satisfies both.
        assertMatches("caretakers.firstName:EQ:Carla AND caretakers.firstName:EQ:Dario");
    }

    @Test
    void currentDefectIsNullOnRelationNeverMatchesPatientsWithoutRelation() {
        // Defect-like: LEFT join is created, but patients without caretakers (Cene, Dana, anna, Fran) are not
        // returned by caretakers.firstName:ISNULL; observed result is empty.
        assertMatches("caretakers.firstName:ISNULL");
    }

    // ---- unknown fields ----

    @Test
    void unknownFieldFailsAtQueryTimeAsInvalidDataAccessApiUsage() {
        assertThatThrownBy(() -> run("bogus:EQ:x")).isInstanceOf(InvalidDataAccessApiUsageException.class);
        assertThatThrownBy(() -> run("bogus.name:EQ:x")).isInstanceOf(InvalidDataAccessApiUsageException.class);
        assertThatThrownBy(() -> run("caretakers.bogus:EQ:x")).isInstanceOf(InvalidDataAccessApiUsageException.class);
    }

    @Test
    void fieldNamesAreCaseSensitive() {
        assertThatThrownBy(() -> run("firstname:EQ:Anna")).isInstanceOf(InvalidDataAccessApiUsageException.class);
    }

    @Test
    void attributeMappingRenamesFieldBeforeQuerying() {
        Filter filter = new RestFilterGenerator(Map.of("name", "firstName")).parse("name:EQ:Anna");
        Specification<Patient> spec = filter.toSpecification();
        assertThat(patients.findAll(spec, Sort.by("firstName"))).extracting(Patient::getFirstName).containsExactly("Anna");
    }

    // ---- malformed / odd input ----

    @Test
    void emptyValueMatchesNothing() {
        assertMatches("firstName:EQ:");
    }

    @Test
    void semicolonSeparatedConditionsSilentlyMatchNothing() {
        assertMatches("firstName:EQ:Anna;lastName:EQ:Novak");
        assertMatches("firstName:EQ:Anna;");
    }

    @Test
    void twoConditionsWithoutOperatorAreOneValueWithSpacesAndMatchNothing() {
        assertMatches("firstName:EQ:Anna lastName:EQ:Novak");
    }

    @Test
    void contradictoryAndMatchesNothing() {
        assertMatches("firstName:EQ:Anna AND firstName:EQ:Bojan");
    }

    @Test
    void currentDefectMalformedInputThrowsNullPointerException() {
        assertThatThrownBy(() -> run("garbage")).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> run("firstName")).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> run("firstName:FOO:Anna")).isInstanceOf(NullPointerException.class);
    }

    @Test
    void currentDefectBrokenInputIsPartiallyOrCompletelyIgnoredInsteadOfRejected() {
        // Defect: syntax errors are recovered silently by the parser and parts of the filter disappear.
        assertMatches("firstName:EQ:Anna AND", A);          // dangling AND dropped
        assertMatches("(firstName:EQ:Anna", A);              // missing ')' tolerated
        assertMatches("firstName:EQ:Anna:Bojan", A);         // trailing ':Bojan' dropped
        assertMatches(":EQ:x", ALL);                         // condition without key ignored -> no filtering
        assertMatches("AND", ALL);
        assertMatches("", ALL);                              // direct parse of empty text (resolver never does this)
        assertMatches("   ", ALL);
    }

    @Test
    void isNullWithTrailingValueStillParsesButMatchesNothing() {
        assertMatches("firstName:ISNULL:x");
    }
}
