package zw.gov.mohcc.impilo.vito.core.idempotency;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.util.Optional;

/**
 * JdbcTemplate-based repository for vito.idempotency_keys table.
 * Uses Spring JDBC (already available via spring-boot-starter-data-jpa).
 */
@Repository
public class IdempotencyRepository {

    private final JdbcTemplate jdbcTemplate;

    public IdempotencyRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Find an existing idempotency record by composite key.
     */
    public Optional<IdempotencyRecord> find(String tenantId, String podId, String idempotencyKey) {
        var results = jdbcTemplate.query(
                "SELECT tenant_id, pod_id, idempotency_key, request_hash, response_status, "
                        + "response_body, created_at, expires_at "
                        + "FROM vito.idempotency_keys "
                        + "WHERE tenant_id = ? AND pod_id = ? AND idempotency_key = ?",
                (rs, rowNum) -> mapRow(rs),
                tenantId, podId, idempotencyKey
        );
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    /**
     * Insert a new idempotency record. Caller must ensure the key does not already exist.
     */
    public void insert(IdempotencyRecord record) {
        jdbcTemplate.update(
                "INSERT INTO vito.idempotency_keys "
                        + "(tenant_id, pod_id, idempotency_key, request_hash, response_status, "
                        + "response_body, created_at, expires_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                record.tenantId(),
                record.podId(),
                record.idempotencyKey(),
                record.requestHash(),
                record.responseStatus(),
                record.responseBody(),
                Timestamp.from(record.createdAt().toInstant()),
                record.expiresAt() != null ? Timestamp.from(record.expiresAt().toInstant()) : null
        );
    }

    private IdempotencyRecord mapRow(ResultSet rs) throws SQLException {
        return new IdempotencyRecord(
                rs.getString("tenant_id"),
                rs.getString("pod_id"),
                rs.getString("idempotency_key"),
                rs.getString("request_hash"),
                rs.getInt("response_status"),
                rs.getString("response_body"),
                rs.getObject("created_at", OffsetDateTime.class),
                rs.getObject("expires_at", OffsetDateTime.class)
        );
    }
}
