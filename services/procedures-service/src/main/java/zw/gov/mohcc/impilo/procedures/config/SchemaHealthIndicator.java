package zw.gov.mohcc.impilo.procedures.config;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Reports whether this service's schema was actually migrated, not merely whether a
 * connection pool is alive.
 *
 * <p>It contributes to {@code /actuator/health} rather than to a bespoke route because
 * {@code /internal/v1/**} is guarded by the v1.1 trust-header filter, and a Kubernetes probe
 * carries no trust headers. Putting liveness behind a route probes cannot reach is how a
 * service ends up with a health endpoint nobody checks.</p>
 *
 * <p>The distinction earns its keep because this service sits on the readiness path. A
 * caller that cannot clear a procedure must block it, so a green health check over a
 * missing schema would tell that caller the opposite of the truth. Reporting DOWN with the
 * failure named is the honest answer; reporting UP because the datasource responded is the
 * failure mode this whole programme is written against.</p>
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
                    "SELECT count(*) FROM procedures.flyway_schema_history WHERE success = true",
                    Integer.class);
            return Health.up()
                    .withDetail("service", "procedures-service")
                    .withDetail("migrationsApplied", applied == null ? 0 : applied)
                    .build();
        } catch (RuntimeException e) {
            return Health.down()
                    .withDetail("service", "procedures-service")
                    .withDetail("schemaError", e.getClass().getSimpleName())
                    .build();
        }
    }
}
