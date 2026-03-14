package zw.gov.mohcc.impilo.shared.security;

import zw.gov.mohcc.impilo.security.audit.AdminAuditEmitter;
import zw.gov.mohcc.impilo.security.audit.AdminAuditRecord;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Objects;

/**
 * {@link AdminAuditEmitter.AdminAuditSink} implementation that writes admin
 * audit events to the service's {@code event_outbox} table via JDBC.
 * <p>
 * This ensures admin audit events participate in the outbox pattern and are
 * reliably published to Kafka by the outbox poller.
 */
public class AdminAuditOutboxSink implements AdminAuditEmitter.AdminAuditSink {

    private static final String INSERT_SQL = """
            INSERT INTO event_outbox (aggregate_type, aggregate_id, event_type, payload, created_at)
            VALUES (?, ?, ?, ?::jsonb, ?)
            """;

    private final DataSource dataSource;
    private final String schema;

    /**
     * @param dataSource JDBC data source
     * @param schema     database schema (e.g. "tshepo", "vito")
     */
    public AdminAuditOutboxSink(DataSource dataSource, String schema) {
        this.dataSource = Objects.requireNonNull(dataSource);
        this.schema = Objects.requireNonNull(schema);
    }

    @Override
    public void persist(AdminAuditRecord record) {
        String sql = "INSERT INTO " + schema + ".event_outbox "
                + "(aggregate_type, aggregate_id, event_type, payload, created_at) "
                + "VALUES (?, ?, ?, ?::jsonb, ?)";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, record.aggregateType());
            ps.setString(2, record.aggregateId());
            ps.setString(3, record.eventType());
            ps.setString(4, record.payload());
            ps.setTimestamp(5, Timestamp.from(record.createdAt()));
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to persist admin audit event to outbox", e);
        }
    }
}
