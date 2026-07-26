package zw.gov.mohcc.impilo.procedures;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import zw.gov.mohcc.impilo.procedures.config.SchemaHealthIndicator;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P0 scaffold proof.
 *
 * <p>Named {@code *Test} deliberately. {@code maven-failsafe-plugin} is configured nowhere in
 * this repository and Surefire's defaults exclude {@code *IT.java}, so all 118 {@code *IT.java}
 * files under {@code services/} never execute. A test named {@code ProceduresApplicationIT}
 * would look like coverage and be none.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
class ProceduresApplicationTest {

    @Autowired
    private ApplicationContext context;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private SchemaHealthIndicator schemaHealth;

    @Test
    void contextLoads() {
        assertThat(context).isNotNull();
    }

    /**
     * Under the test profile Flyway is disabled and the schema comes from ddl-auto, so there is
     * no {@code flyway_schema_history} and DOWN is the correct answer. This asserts the honest
     * negative: the indicator must not report UP just because the datasource answered.
     */
    @Test
    void schemaHealthReportsDownWhenTheSchemaWasNeverMigrated() {
        Health health = schemaHealth.health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsEntry("service", "procedures-service");
        assertThat(health.getDetails()).containsKey("schemaError");
    }

    /**
     * And the positive, so the negative above is not passing for the wrong reason: given a
     * migration history with a successful row, the indicator reports UP and counts it.
     */
    @Test
    void schemaHealthReportsUpAndCountsAppliedMigrations() {
        jdbc.execute("CREATE TABLE IF NOT EXISTS procedures.flyway_schema_history "
                + "(installed_rank INT, success BOOLEAN)");
        jdbc.update("INSERT INTO procedures.flyway_schema_history (installed_rank, success) VALUES (1, true)");
        jdbc.update("INSERT INTO procedures.flyway_schema_history (installed_rank, success) VALUES (2, false)");
        try {
            Health health = schemaHealth.health();

            assertThat(health.getStatus()).isEqualTo(Status.UP);
            // Two rows, one failed: a failed migration is not an applied one.
            assertThat(health.getDetails()).containsEntry("migrationsApplied", 1);
        } finally {
            jdbc.execute("DROP TABLE procedures.flyway_schema_history");
        }
    }
}
