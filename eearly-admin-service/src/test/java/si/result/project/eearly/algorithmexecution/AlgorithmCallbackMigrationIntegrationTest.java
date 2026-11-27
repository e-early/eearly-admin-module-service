package si.result.project.eearly.algorithmexecution;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Migrations V1.19.0 (callback token columns of an execution) and V1.20.0 (credentials of an algorithm), run against
 * a PostgreSQL container on a database that already holds rows from before them.
 */
@Testcontainers
class AlgorithmCallbackMigrationIntegrationTest {

    private static final String PATIENT_ID = "11111111-1111-1111-1111-111111111111";
    private static final String ALGORITHM_ID = "22222222-2222-2222-2222-222222222222";
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

    private static String single(final Statement s, final String sql) throws SQLException {
        try (ResultSet rs = s.executeQuery(sql)) {
            assertThat(rs.next()).as(sql).isTrue();
            return rs.getString(1);
        }
    }

    private static void assertColumn(final Connection c, final String table, final String column,
            final String type, final String nullable) throws SQLException {
        final String sql = "SELECT data_type, is_nullable FROM information_schema.columns WHERE table_name = '"
                + table + "' AND column_name = '" + column + "'";
        try (Statement s = c.createStatement(); ResultSet rs = s.executeQuery(sql)) {
            assertThat(rs.next()).as(table + "." + column + " exists").isTrue();
            assertThat(rs.getString("data_type")).as(table + "." + column).isEqualTo(type);
            assertThat(rs.getString("is_nullable")).as(table + "." + column).isEqualTo(nullable);
        }
    }

    private static boolean columnExists(final Connection c, final String table, final String column)
            throws SQLException {
        try (Statement s = c.createStatement(); ResultSet rs = s.executeQuery(
                "SELECT 1 FROM information_schema.columns WHERE table_name = '" + table
                        + "' AND column_name = '" + column + "'")) {
            return rs.next();
        }
    }

    @Test
    void emptyDatabase_hasTheNewColumns() throws Exception {
        flyway("latest").clean();
        flyway("latest").migrate();

        try (Connection c = connection()) {
            assertColumn(c, "t_algorithm_execution", "callback_token_id", "uuid", "YES");
            assertColumn(c, "t_algorithm_execution", "callback_token_expires_at", "timestamp with time zone", "YES");
            assertColumn(c, "t_algorithm_execution", "callback_token_consumed_at", "timestamp with time zone", "YES");
            assertColumn(c, "t_algorithm", "auth_username", "character varying", "YES");
            // ciphertext: no length limit that would cut it
            assertColumn(c, "t_algorithm", "auth_password", "text", "YES");
        }
    }

    @Test
    void existingRows_keepTheirDataAndGetNullColumns() throws Exception {
        flyway("latest").clean();
        flyway("1.18.0").migrate();

        try (Connection c = connection(); Statement s = c.createStatement()) {
            s.execute("INSERT INTO t_patient (id, first_name, last_name, date_of_birth, gender, health_insurance_id, "
                    + "street, city, state, country, email, phone_number) VALUES ('" + PATIENT_ID
                    + "', 'A', 'B', DATE '1990-01-01', 'OTHER', 'X1', 's', 'c', 'st', 'co', 'a@b.c', '1')");
            s.execute("INSERT INTO t_algorithm (id, name) VALUES ('" + ALGORITHM_ID + "', 'algo')");
            s.execute("INSERT INTO t_algorithm_execution (id, id_algorithm, id_patient, trigger_type, execution_status) "
                    + "VALUES ('" + EXECUTION_ID + "', '" + ALGORITHM_ID + "', '" + PATIENT_ID
                    + "', 'MANUAL', 'RUNNING')");
            assertThat(columnExists(c, "t_algorithm_execution", "callback_token_id")).isFalse();
            assertThat(columnExists(c, "t_algorithm", "auth_username")).isFalse();
        }

        flyway("latest").migrate();

        try (Connection c = connection(); Statement s = c.createStatement()) {
            assertThat(single(s, "SELECT execution_status::text FROM t_algorithm_execution WHERE id = '"
                    + EXECUTION_ID + "'")).isEqualTo("RUNNING");
            assertThat(single(s, "SELECT callback_token_id FROM t_algorithm_execution WHERE id = '"
                    + EXECUTION_ID + "'")).isNull();
            assertThat(single(s, "SELECT callback_token_expires_at FROM t_algorithm_execution WHERE id = '"
                    + EXECUTION_ID + "'")).isNull();
            assertThat(single(s, "SELECT callback_token_consumed_at FROM t_algorithm_execution WHERE id = '"
                    + EXECUTION_ID + "'")).isNull();
            assertThat(single(s, "SELECT name FROM t_algorithm WHERE id = '" + ALGORITHM_ID + "'")).isEqualTo("algo");
            assertThat(single(s, "SELECT auth_username FROM t_algorithm WHERE id = '" + ALGORITHM_ID + "'")).isNull();
            assertThat(single(s, "SELECT auth_password FROM t_algorithm WHERE id = '" + ALGORITHM_ID + "'")).isNull();

            // the columns take what the service writes: a token id and times, and a long ciphertext
            final String ciphertext = "v1:" + "A".repeat(2000);
            final UUID jti = UUID.randomUUID();
            s.execute("UPDATE t_algorithm_execution SET callback_token_id = '" + jti
                    + "', callback_token_expires_at = now(), callback_token_consumed_at = now() WHERE id = '"
                    + EXECUTION_ID + "'");
            s.execute("UPDATE t_algorithm SET auth_username = 'svc', auth_password = '" + ciphertext
                    + "' WHERE id = '" + ALGORITHM_ID + "'");
            assertThat(single(s, "SELECT callback_token_id FROM t_algorithm_execution WHERE id = '" + EXECUTION_ID
                    + "'")).isEqualTo(jti.toString());
            assertThat(single(s, "SELECT auth_password FROM t_algorithm WHERE id = '" + ALGORITHM_ID + "'"))
                    .isEqualTo(ciphertext);
        }
    }

    @Test
    void migrationsAreTheLatestOnesInOrder() throws Exception {
        flyway("latest").clean();
        flyway("latest").migrate();

        try (Connection c = connection(); Statement s = c.createStatement()) {
            assertThat(single(s, "SELECT script FROM flyway_schema_history WHERE version = '1.19.0'"))
                    .isEqualTo("V1.19.0__add_algorithm_callback_token.sql");
            assertThat(single(s, "SELECT script FROM flyway_schema_history WHERE version = '1.20.0'"))
                    .isEqualTo("V1.20.0__add_algorithm_credentials.sql");
            assertThat(single(s, "SELECT success::text FROM flyway_schema_history WHERE version = '1.20.0'"))
                    .isEqualTo("true");
        }
    }
}
