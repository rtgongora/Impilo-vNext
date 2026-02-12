package zw.gov.mohcc.impilo.companion.idempotency;

import org.springframework.jdbc.core.JdbcTemplate;

import java.time.OffsetDateTime;
import java.util.Optional;

/**
 * JDBC implementation of {@link IdempotencyRepository}.
 *
 * Expects a table with the reference schema (configurable table name).
 * Default table: {@code idempotency_keys}.
 * Default TTL: 24 hours.
 */
public class JdbcIdempotencyRepository implements IdempotencyRepository {

    private final JdbcTemplate jdbc;
    private final String tableName;
    private final long ttlHours;

    public JdbcIdempotencyRepository(JdbcTemplate jdbc) {
        this(jdbc, "idempotency_keys", 24);
    }

    public JdbcIdempotencyRepository(JdbcTemplate jdbc, String tableName, long ttlHours) {
        this.jdbc = jdbc;
        this.tableName = tableName;
        this.ttlHours = ttlHours;
    }

    @Override
    public Optional<IdempotencyRecord> find(String tenantId, String podId, String idempotencyKey) {
        String sql = "SELECT tenant_id, pod_id, idempotency_key, request_hash, " +
                "response_status, response_body, created_at, expires_at " +
                "FROM " + tableName +
                " WHERE tenant_id = ? AND pod_id = ? AND idempotency_key = ? " +
                "AND (expires_at IS NULL OR expires_at > now())";

        return jdbc.query(sql, (rs, rowNum) -> new IdempotencyRecord(
                rs.getString("tenant_id"),
                rs.getString("pod_id"),
                rs.getString("idempotency_key"),
                rs.getString("request_hash"),
                rs.getInt("response_status"),
                rs.getString("response_body"),
                rs.getObject("created_at", OffsetDateTime.class),
                rs.getObject("expires_at", OffsetDateTime.class)
        ), tenantId, podId, idempotencyKey).stream().findFirst();
    }

    @Override
    public void store(String tenantId, String podId, String idempotencyKey,
                      String requestHash, int responseStatus, String responseBody) {
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime expiresAt = now.plusHours(ttlHours);

        String sql = "INSERT INTO " + tableName +
                " (tenant_id, pod_id, idempotency_key, request_hash, " +
                "response_status, response_body, created_at, expires_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?) " +
                "ON CONFLICT (tenant_id, pod_id, idempotency_key) DO NOTHING";

        jdbc.update(sql, tenantId, podId, idempotencyKey,
                requestHash, responseStatus, responseBody, now, expiresAt);
    }
}
