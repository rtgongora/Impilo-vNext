package zw.gov.mohcc.impilo.ops.metrics;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * JDBC-based probe that tracks the count of unpublished outbox events.
 *
 * <p>Registers a gauge {@code impilo.ops.outbox.lag} that queries the service's
 * event_outbox table for rows WHERE published_at IS NULL.
 *
 * <p>The outbox table name is configurable per service (e.g. ih_event_outbox,
 * ns_event_outbox, fs_event_outbox, ss_event_outbox, rs_event_outbox).
 */
public class OutboxLagProbe {

    private static final Logger log = LoggerFactory.getLogger(OutboxLagProbe.class);

    private final JdbcTemplate jdbc;
    private final String outboxTable;

    public OutboxLagProbe(JdbcTemplate jdbc, MeterRegistry registry, String outboxTable) {
        this.jdbc = jdbc;
        this.outboxTable = outboxTable;

        Gauge.builder("impilo.ops.outbox.lag", this, OutboxLagProbe::queryUnpublishedCount)
                .description("Number of unpublished outbox events")
                .tag("table", outboxTable)
                .register(registry);
    }

    public double queryUnpublishedCount() {
        try {
            Integer count = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM " + outboxTable + " WHERE published_at IS NULL",
                    Integer.class);
            return count != null ? count : 0;
        } catch (Exception e) {
            log.warn("Failed to query outbox lag for table {}: {}", outboxTable, e.getMessage());
            return -1;
        }
    }
}
