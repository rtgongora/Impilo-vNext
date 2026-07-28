package zw.gov.mohcc.impilo.surgery.config;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Reports whether this service's schema was actually migrated, not merely whether a
 * connection pool is alive.
 *
 * <p>Contributes to {@code /actuator/health} rather than a bespoke route because
 * {@code /internal/v1/**} is guarded by the v1.1 trust-header filter, and a Kubernetes probe
 * carries no trust headers. Reporting UP because the datasource merely responded — while the
 * schema was never migrated — is the failure mode this whole line of services (procedures,
 * surgery) is written against, because both sit on paths where a clinician needs an honest
 * answer rather than a green check that means nothing.</p>
 */
@Component("schema")
public class SchemaHealthIndicator implements HealthIndicator {

    private final JdbcTemplate jdbc;

    public SchemaHealthIndicator(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Health health() {
        try {
            Integer applied = jdbc.queryForObject(
                    "SELECT count(*) FROM surgery.flyway_schema_history WHERE success = true",
                    Integer.class);
            return Health.up()
                    .withDetail("service", "surgery-service")
                    .withDetail("migrationsApplied", applied == null ? 0 : applied)
                    .build();
        } catch (RuntimeException e) {
            return Health.down()
                    .withDetail("service", "surgery-service")
                    .withDetail("schemaError", e.getClass().getSimpleName())
                    .build();
        }
    }
}
