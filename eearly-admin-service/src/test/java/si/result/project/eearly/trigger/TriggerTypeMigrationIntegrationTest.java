package si.result.project.eearly.trigger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
 * Runs the Flyway migrations against a PostgreSQL container, up to and including the version before the
 * trigger type migration, then to the latest version, and checks the trigger type columns.
 */
@Testcontainers
class TriggerTypeMigrationIntegrationTest {

    private static final String PATIENT_ID = "11111111-1111-1111-1111-111111111111";
    private static final String ALGORITHM_ID = "22222222-2222-2222-2222-222222222222";
    private static final String ANALYSIS_ID = "33333333-3333-3333-3333-333333333333";
    private static final String EXECUTION_ID = "44444444-4444-4444-4444-444444444444";

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

    private void cleanDatabase() {
        flyway("latest").clean();
    }

    @Test
    void emptyDatabase_hasTriggerTypeColumnsWithManualDefault() throws Exception {
        cleanDatabase();
        flyway("latest").migrate();

        try (Connection c = connection()) {
            for (final String table : List.of("t_analysis", "t_algorithm_execution")) {
                final String sql = "SELECT udt_name, is_nullable FROM information_schema.columns "
                        + "WHERE table_name = '" + table + "' AND column_name = 'trigger_type'";
                try (Statement s = c.createStatement(); ResultSet rs = s.executeQuery(sql)) {
                    assertThat(rs.next()).as(table + ".trigger_type exists").isTrue();
                    assertThat(rs.getString("udt_name")).isEqualTo("e_trigger_type");
                    assertThat(rs.getString("is_nullable")).isEqualTo("NO");
                }
            }
            assertThat(enumLabels(c)).containsExactly("MANUAL", "SCHEDULED");

            final String analysisDefault = "SELECT column_default FROM information_schema.columns "
                    + "WHERE table_name = 't_analysis' AND column_name = 'trigger_type'";
            try (Statement s = c.createStatement(); ResultSet rs = s.executeQuery(analysisDefault)) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getString(1)).contains("MANUAL");
            }
        }
    }

    @Test
    void existingRows_getManualAndExecutionValueIsKept() throws Exception {
        cleanDatabase();
        flyway("1.8.0").migrate();

        try (Connection c = connection(); Statement s = c.createStatement()) {
            s.execute("INSERT INTO t_patient (id, first_name, last_name, date_of_birth, gender, health_insurance_id, "
                    + "street, city, state, country, email, phone_number) VALUES ('" + PATIENT_ID
                    + "', 'A', 'B', DATE '1990-01-01', 'OTHER', 'X1', 's', 'c', 'st', 'co', 'a@b.c', '1')");
            s.execute("INSERT INTO t_algorithm (id, name) VALUES ('" + ALGORITHM_ID + "', 'algo')");
            s.execute("INSERT INTO t_analysis (id, name, state, feedback, id_patient, id_algorithm) VALUES ('"
                    + ANALYSIS_ID + "', 'old analysis', 'COMPLETED', 'NONE', '" + PATIENT_ID + "', '"
                    + ALGORITHM_ID + "')");
            s.execute("INSERT INTO t_algorithm_execution (id, id_algorithm, id_patient, id_analysis, trigger_type) "
                    + "VALUES ('" + EXECUTION_ID + "', '" + ALGORITHM_ID + "', '" + PATIENT_ID + "', '"
                    + ANALYSIS_ID + "', 'SCHEDULED')");
            assertThat(columnExists(c, "t_analysis", "trigger_type")).isFalse();
        }

        flyway("latest").migrate();

        try (Connection c = connection(); Statement s = c.createStatement()) {
            assertThat(single(s, "SELECT trigger_type::text FROM t_analysis WHERE id = '" + ANALYSIS_ID + "'"))
                    .isEqualTo("MANUAL");
            assertThat(single(s, "SELECT trigger_type::text FROM t_algorithm_execution WHERE id = '"
                    + EXECUTION_ID + "'")).isEqualTo("SCHEDULED");

            // A row inserted without the column falls back to MANUAL; invalid values are rejected.
            s.execute("INSERT INTO t_analysis (id, name, state, feedback, id_patient) VALUES "
                    + "('55555555-5555-5555-5555-555555555555', 'new analysis', 'IN_PROGRESS', 'NONE', '"
                    + PATIENT_ID + "')");
            assertThat(single(s, "SELECT trigger_type::text FROM t_analysis WHERE id = "
                    + "'55555555-5555-5555-5555-555555555555'")).isEqualTo("MANUAL");
            assertThatThrownBy(() -> s.execute("INSERT INTO t_analysis (id, name, state, feedback, id_patient, "
                    + "trigger_type) VALUES ('66666666-6666-6666-6666-666666666666', 'x', 'IN_PROGRESS', 'NONE', '"
                    + PATIENT_ID + "', 'OTHER')")).isInstanceOf(SQLException.class);
        }
    }

    private static List<String> enumLabels(final Connection c) throws SQLException {
        final List<String> labels = new ArrayList<>();
        final String sql = "SELECT e.enumlabel FROM pg_enum e JOIN pg_type t ON t.oid = e.enumtypid "
                + "WHERE t.typname = 'e_trigger_type' ORDER BY e.enumsortorder";
        try (Statement s = c.createStatement(); ResultSet rs = s.executeQuery(sql)) {
            while (rs.next()) {
                labels.add(rs.getString(1));
            }
        }
        return labels;
    }

    private static boolean columnExists(final Connection c, final String table, final String column)
            throws SQLException {
        try (Statement s = c.createStatement(); ResultSet rs = s.executeQuery(
                "SELECT 1 FROM information_schema.columns WHERE table_name = '" + table
                        + "' AND column_name = '" + column + "'")) {
            return rs.next();
        }
    }

    private static String single(final Statement s, final String sql) throws SQLException {
        try (ResultSet rs = s.executeQuery(sql)) {
            assertThat(rs.next()).isTrue();
            return rs.getString(1);
        }
    }
}
