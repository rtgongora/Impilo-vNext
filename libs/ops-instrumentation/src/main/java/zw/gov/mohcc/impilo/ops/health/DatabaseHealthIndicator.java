package zw.gov.mohcc.impilo.ops.health;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Lightweight database connectivity health check.
 *
 * <p>Executes {@code SELECT 1} to verify the database connection is alive.
 * This is safe and imposes negligible load.
 */
public class DatabaseHealthIndicator implements HealthIndicator {

    private final JdbcTemplate jdbc;

    public DatabaseHealthIndicator(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Health health() {
        try {
            jdbc.queryForObject("SELECT 1", Integer.class);
            return Health.up().withDetail("database", "reachable").build();
        } catch (Exception e) {
            return Health.down()
                    .withDetail("database", "unreachable")
                    .withDetail("error", e.getMessage())
                    .build();
        }
    }
}
