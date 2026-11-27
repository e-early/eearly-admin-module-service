package si.result.project.eearly.qrcode;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** V1.18.0: the QR code table, its constraints, indexes and enum, and that it holds no seed data. */
@Testcontainers
class QRCodeMigrationIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @BeforeAll
    static void migrate() {
        Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration").load().migrate();
    }

    private static List<String> column(final String sql) throws Exception {
        final List<String> out = new ArrayList<>();
        try (Connection c = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(),
                POSTGRES.getPassword()); Statement s = c.createStatement(); ResultSet rs = s.executeQuery(sql)) {
            while (rs.next()) {
                out.add(rs.getString(1));
            }
        }
        return out;
    }

    @Test
    @DisplayName("the migration is applied and the table holds no rows")
    void tableIsEmpty() throws Exception {
        assertThat(column("select version from flyway_schema_history where version = '1.18.0' and success"))
                .containsExactly("1.18.0");
        assertThat(column("select count(*) from t_qr_code")).containsExactly("0");
    }

    @Test
    @DisplayName("the status enum has the four labels")
    void enumLabels() throws Exception {
        assertThat(column("select e.enumlabel from pg_enum e join pg_type t on t.oid = e.enumtypid "
                + "where t.typname = 'e_qr_code_status' order by e.enumsortorder"))
                .containsExactly("ACTIVE", "USED", "EXPIRED", "REVOKED");
    }

    @Test
    @DisplayName("the columns have the expected nullability")
    void columns() throws Exception {
        assertThat(column("select column_name || ':' || is_nullable from information_schema.columns "
                + "where table_name = 't_qr_code' order by column_name")).contains(
                "id:NO", "token:NO", "expires_at:NO", "image_url:NO", "id_patient:NO", "id_caretaker:YES",
                "onboarding_url:YES", "is_deleted:NO", "version:NO");
    }

    @Test
    @DisplayName("the token is unique and the five indexes exist")
    void indexes() throws Exception {
        assertThat(column("select indexname from pg_indexes where tablename = 't_qr_code'")).contains(
                "idx_qr_code_token", "idx_qr_code_patient", "idx_qr_code_status", "idx_qr_code_expires_at",
                "idx_qr_code_caretaker_id");
        assertThat(column("select constraint_type from information_schema.table_constraints "
                + "where table_name = 't_qr_code' and constraint_type = 'UNIQUE'")).isNotEmpty();
    }

    @Test
    @DisplayName("deleting the patient cascades, deleting the caretaker clears the reference")
    void foreignKeyRules() throws Exception {
        assertThat(column("select constraint_name || ':' || delete_rule from information_schema.referential_constraints "
                + "where constraint_name in ('fk_qr_code_patient', 'fk_qr_code_caretaker') order by 1"))
                .containsExactly("fk_qr_code_caretaker:SET NULL", "fk_qr_code_patient:CASCADE");
    }
}
