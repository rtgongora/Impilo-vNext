package zw.gov.mohcc.impilo.oros.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Immutable snapshot of an order (order fields + items) at a specific version (OF-B1).
 *
 * <p>Amendment creates a new row with {@code version = head + 1} and a
 * {@code supersedesVersionId} link — versions are never updated or deleted.
 * {@code contentHash} is the SHA-256 of the canonical serialisation; the
 * detached JWS prescriber signature (OF-B2) binds to it. The
 * {@code UNIQUE(order_id, version)} constraint is the concurrent-amend race guard.</p>
 */
@Entity
@Table(name = "oros_order_versions")
public class OrderVersionEntity {

    @Id
    @Column(name = "order_version_id", nullable = false, length = 26)
    private String orderVersionId;

    @Column(name = "order_id", nullable = false, length = 26)
    private String orderId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "version", nullable = false)
    private int version;

    @Column(name = "supersedes_version_id", length = 26)
    private String supersedesVersionId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "content_json", columnDefinition = "jsonb", nullable = false)
    private String contentJson;

    @Column(name = "content_hash", nullable = false, length = 64)
    private String contentHash;

    @Column(name = "reason", length = 512)
    private String reason;

    @Column(name = "created_by", length = 128)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    public String getOrderVersionId() { return orderVersionId; }
    public void setOrderVersionId(String orderVersionId) { this.orderVersionId = orderVersionId; }

    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public int getVersion() { return version; }
    public void setVersion(int version) { this.version = version; }

    public String getSupersedesVersionId() { return supersedesVersionId; }
    public void setSupersedesVersionId(String supersedesVersionId) { this.supersedesVersionId = supersedesVersionId; }

    public String getContentJson() { return contentJson; }
    public void setContentJson(String contentJson) { this.contentJson = contentJson; }

    public String getContentHash() { return contentHash; }
    public void setContentHash(String contentHash) { this.contentHash = contentHash; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
