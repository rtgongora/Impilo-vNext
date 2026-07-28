package zw.gov.mohcc.impilo.surgery;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import zw.gov.mohcc.impilo.surgery.config.SchemaHealthIndicator;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * S0 scaffold proof.
 *
 * <p>Named {@code *Test} deliberately, matching the estate-wide law recorded in
 * {@code procedures-service}'s own scaffold test: {@code maven-failsafe-plugin} is configured
 * nowhere in this repository and Surefire's defaults exclude {@code *IT.java}, so a test named
 * {@code SurgeryApplicationIT} would look like coverage and be none.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
class SurgeryApplicationTest {

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
        assertThat(health.getDetails()).containsEntry("service", "surgery-service");
        assertThat(health.getDetails()).containsKey("schemaError");
    }

    /**
     * And the positive, so the negative above is not passing for the wrong reason: given a
     * migration history with a successful row, the indicator reports UP and counts it.
     */
    @Test
    void schemaHealthReportsUpAndCountsAppliedMigrations() {
        jdbc.execute("CREATE TABLE IF NOT EXISTS surgery.flyway_schema_history "
                + "(installed_rank INT, success BOOLEAN)");
        jdbc.update("INSERT INTO surgery.flyway_schema_history (installed_rank, success) VALUES (1, true)");
        jdbc.update("INSERT INTO surgery.flyway_schema_history (installed_rank, success) VALUES (2, false)");
        try {
            Health health = schemaHealth.health();

            assertThat(health.getStatus()).isEqualTo(Status.UP);
            // Two rows, one failed: a failed migration is not an applied one.
            assertThat(health.getDetails()).containsEntry("migrationsApplied", 1);
        } finally {
            jdbc.execute("DROP TABLE surgery.flyway_schema_history");
        }
    }
}
