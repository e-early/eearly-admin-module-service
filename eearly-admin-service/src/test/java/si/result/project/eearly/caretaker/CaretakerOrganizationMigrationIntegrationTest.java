package si.result.project.eearly.caretaker;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Runs the Flyway migrations against a PostgreSQL container, up to the version before the caretaker
 * organization and department migration, then to the latest version.
 */
@Testcontainers
class CaretakerOrganizationMigrationIntegrationTest {

    private static final String MIGRATION = "db/migration/V1.13.0__add_caretaker_organization_and_department.sql";
    private static final String CARETAKER_ID = "11111111-1111-1111-1111-111111111111";
    private static final String DELETED_CARETAKER_ID = "22222222-2222-2222-2222-222222222222";
    private static final String ORGANIZATION_ID = "33333333-3333-3333-3333-333333333333";
    private static final String DEPARTMENT_ID = "44444444-4444-4444-4444-444444444444";

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    private Flyway flyway(final String target) {
        return Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .cleanDisabled(false)
                .target(target)
                .load();
    }

    private Connection connection() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    @Test
    void upgradeFrom1_12_0_assignsExistingCaretakersToOneDefaultOrganizationAndDepartment() throws Exception {
        flyway("latest").clean();
        flyway("1.12.0").migrate();
        try (Connection c = connection(); Statement s = c.createStatement()) {
            assertThat(columnExists(c, "id_organization")).isFalse();
            s.execute("INSERT INTO t_organization (id, name) VALUES ('" + ORGANIZATION_ID + "', 'Existing Organization')");
            s.execute("INSERT INTO t_department (id, name, id_organization) VALUES ('" + DEPARTMENT_ID
                    + "', 'Existing Department', '" + ORGANIZATION_ID + "')");
            s.execute("INSERT INTO t_caretaker (id, first_name, last_name, email, language) VALUES ('"
                    + CARETAKER_ID + "', 'Test', 'Caretaker', 'test@example.org', 'sl')");
            s.execute("INSERT INTO t_caretaker (id, first_name, is_deleted) VALUES ('" + DELETED_CARETAKER_ID
                    + "', 'Deleted', true)");
        }

        flyway("latest").migrate();

        try (Connection c = connection(); Statement s = c.createStatement()) {
            assertThat(single(s, "SELECT count(*) FROM t_organization WHERE name = 'Default organization'"))
                    .isEqualTo("1");
            assertThat(single(s, "SELECT count(*) FROM t_department WHERE name = 'Default department'"))
                    .isEqualTo("1");
            // Existing rows are left alone and nothing else is created.
            assertThat(single(s, "SELECT count(*) FROM t_organization")).isEqualTo("2");
            assertThat(single(s, "SELECT count(*) FROM t_department")).isEqualTo("2");
            assertThat(single(s, "SELECT count(*) FROM t_department d JOIN t_organization o ON o.id = d.id_organization "
                    + "WHERE d.name = 'Default department' AND o.name = 'Default organization'")).isEqualTo("1");

            for (final String id : List.of(CARETAKER_ID, DELETED_CARETAKER_ID)) {
                assertThat(single(s, "SELECT count(*) FROM t_caretaker c "
                        + "JOIN t_organization o ON o.id = c.id_organization AND o.name = 'Default organization' "
                        + "JOIN t_department d ON d.id = c.id_department AND d.name = 'Default department' "
                        + "WHERE c.id = '" + id + "'")).as("caretaker " + id).isEqualTo("1");
            }
            assertThat(single(s, "SELECT first_name FROM t_caretaker WHERE id = '" + CARETAKER_ID + "'"))
                    .isEqualTo("Test");
            assertConstraintsPresent(s);
        }
    }

    @Test
    void emptyDatabase_createsNoDefaultRowsAndHasTheColumnsAndConstraints() throws Exception {
        flyway("latest").clean();
        flyway("latest").migrate();

        try (Connection c = connection(); Statement s = c.createStatement()) {
            assertThat(single(s, "SELECT count(*) FROM t_organization")).isEqualTo("0");
            assertThat(single(s, "SELECT count(*) FROM t_department")).isEqualTo("0");
            assertThat(single(s, "SELECT count(*) FROM t_caretaker")).isEqualTo("0");
            assertConstraintsPresent(s);
        }
    }

    @Test
    void organizationsWithoutCaretakers_getNoDefaultRows() throws Exception {
        flyway("latest").clean();
        flyway("1.12.0").migrate();
        try (Connection c = connection(); Statement s = c.createStatement()) {
            s.execute("INSERT INTO t_organization (id, name) VALUES ('" + ORGANIZATION_ID + "', 'Existing Organization')");
        }

        flyway("latest").migrate();

        try (Connection c = connection(); Statement s = c.createStatement()) {
            assertThat(single(s, "SELECT count(*) FROM t_organization")).isEqualTo("1");
            assertThat(single(s, "SELECT count(*) FROM t_department")).isEqualTo("0");
        }
    }

    @Test
    void runningTheMigrationAgainChangesNothingAndReusesTheDefaultRows() throws Exception {
        flyway("latest").clean();
        flyway("1.12.0").migrate();
        try (Connection c = connection(); Statement s = c.createStatement()) {
            s.execute("INSERT INTO t_caretaker (id, first_name) VALUES ('" + CARETAKER_ID + "', 'Test')");
        }
        flyway("latest").migrate();

        try (Connection c = connection(); Statement s = c.createStatement()) {
            // A caretaker without them (the columns are nullable) is assigned the same default rows.
            s.execute("INSERT INTO t_caretaker (id, first_name) VALUES ('" + DELETED_CARETAKER_ID + "', 'Other')");
            s.execute(migrationScript());
            s.execute(migrationScript());

            assertThat(single(s, "SELECT count(*) FROM t_organization")).isEqualTo("1");
            assertThat(single(s, "SELECT count(*) FROM t_department")).isEqualTo("1");
            assertThat(single(s, "SELECT count(DISTINCT id_organization) FROM t_caretaker")).isEqualTo("1");
            assertThat(single(s, "SELECT count(*) FROM t_caretaker WHERE id_organization IS NOT NULL "
                    + "AND id_department IS NOT NULL")).isEqualTo("2");
            assertConstraintsPresent(s);
        }
    }

    private static void assertConstraintsPresent(final Statement s) throws SQLException {
        final List<String> foreignKeys = new ArrayList<>();
        try (ResultSet rs = s.executeQuery(
                "SELECT kcu.column_name || '>' || ccu.table_name || ':' || rc.delete_rule "
                        + "FROM information_schema.referential_constraints rc "
                        + "JOIN information_schema.key_column_usage kcu ON kcu.constraint_name = rc.constraint_name "
                        + "JOIN information_schema.constraint_column_usage ccu ON ccu.constraint_name = rc.constraint_name "
                        + "WHERE kcu.table_name = 't_caretaker'")) {
            while (rs.next()) {
                foreignKeys.add(rs.getString(1));
            }
        }
        assertThat(foreignKeys).containsExactlyInAnyOrder(
                "id_organization>t_organization:RESTRICT", "id_department>t_department:RESTRICT");

        // Nullable uuid columns; "required" is enforced by the domain.
        for (final String column : List.of("id_organization", "id_department")) {
            assertThat(single(s, "SELECT udt_name || '/' || is_nullable FROM information_schema.columns "
                    + "WHERE table_name = 't_caretaker' AND column_name = '" + column + "'")).isEqualTo("uuid/YES");
        }
    }

    private boolean columnExists(final Connection c, final String column) throws SQLException {
        try (Statement s = c.createStatement(); ResultSet rs = s.executeQuery(
                "SELECT 1 FROM information_schema.columns WHERE table_name = 't_caretaker' AND column_name = '"
                        + column + "'")) {
            return rs.next();
        }
    }

    private static String migrationScript() throws Exception {
        try (InputStream in = CaretakerOrganizationMigrationIntegrationTest.class.getClassLoader()
                .getResourceAsStream(MIGRATION)) {
            assertThat(in).as(MIGRATION).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static String single(final Statement s, final String sql) throws SQLException {
        try (ResultSet rs = s.executeQuery(sql)) {
            assertThat(rs.next()).isTrue();
            return rs.getString(1);
        }
    }
}
