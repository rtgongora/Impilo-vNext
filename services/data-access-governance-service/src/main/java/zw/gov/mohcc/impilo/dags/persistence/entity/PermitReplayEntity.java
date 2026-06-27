package zw.gov.mohcc.impilo.dags.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Records a consumed permit-token nonce. The enforcement (verify) path inserts a row on
 * first successful use; the unique {@code nonce} constraint makes a second presentation of
 * the same token a replay, which is rejected. Single-use enforcement for permit grants.
 */
@Entity
@Table(name = "permit_replay", schema = "dags")
public class PermitReplayEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "nonce", nullable = false, unique = true)
    private String nonce;

    @Column(name = "request_id")
    private Long requestId;

    @Column(name = "consumed_at", nullable = false, updatable = false)
    private OffsetDateTime consumedAt;

    @PrePersist
    protected void onCreate() {
        consumedAt = OffsetDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public String getNonce() { return nonce; }
    public void setNonce(String nonce) { this.nonce = nonce; }
    public Long getRequestId() { return requestId; }
    public void setRequestId(Long requestId) { this.requestId = requestId; }
    public OffsetDateTime getConsumedAt() { return consumedAt; }
    public void setConsumedAt(OffsetDateTime consumedAt) { this.consumedAt = consumedAt; }
}
