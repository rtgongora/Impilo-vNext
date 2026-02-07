package zw.gov.mohcc.impilo.tshepo.persistence;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "audit_chain_head", schema = "tshepo")
public class AuditChainHeadEntity {

    @Id
    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(name = "current_hash", nullable = false)
    private String currentHash;

    @Column(name = "current_seq", nullable = false)
    private Long currentSeq;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // --- Getters and Setters ---
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public String getCurrentHash() { return currentHash; }
    public void setCurrentHash(String currentHash) { this.currentHash = currentHash; }
    public Long getCurrentSeq() { return currentSeq; }
    public void setCurrentSeq(Long currentSeq) { this.currentSeq = currentSeq; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
