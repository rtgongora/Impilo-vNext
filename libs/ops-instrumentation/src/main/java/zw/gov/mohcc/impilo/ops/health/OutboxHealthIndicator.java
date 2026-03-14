package zw.gov.mohcc.impilo.ops.health;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Health indicator that checks the outbox table for excessive lag.
 *
 * <p>Reports UP when unpublished event count is below threshold (default 1000).
 * Reports DOWN when count exceeds threshold or query fails.
 */
public class OutboxHealthIndicator implements HealthIndicator {

    private final JdbcTemplate jdbc;
    private final String outboxTable;
    private final int threshold;

    public OutboxHealthIndicator(JdbcTemplate jdbc, String outboxTable, int threshold) {
        this.jdbc = jdbc;
        this.outboxTable = outboxTable;
        this.threshold = threshold;
    }

    @Override
    public Health health() {
        try {
            Integer count = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM " + outboxTable + " WHERE published_at IS NULL",
                    Integer.class);
            int unpublished = count != null ? count : 0;

            if (unpublished > threshold) {
                return Health.down()
                        .withDetail("outbox_table", outboxTable)
                        .withDetail("unpublished_count", unpublished)
                        .withDetail("threshold", threshold)
                        .build();
            }

            return Health.up()
                    .withDetail("outbox_table", outboxTable)
                    .withDetail("unpublished_count", unpublished)
                    .build();
        } catch (Exception e) {
            return Health.down()
                    .withDetail("outbox_table", outboxTable)
                    .withDetail("error", e.getMessage())
                    .build();
        }
    }
}
