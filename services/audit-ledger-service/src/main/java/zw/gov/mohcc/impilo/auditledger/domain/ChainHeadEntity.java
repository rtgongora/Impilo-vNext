package zw.gov.mohcc.impilo.auditledger.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "ald_chain_heads")
public class ChainHeadEntity {
    @Id @Column(name = "tenant_id") private UUID tenantId;
    @Column(name = "current_hash", nullable = false) private String currentHash;
    @Column(name = "current_seq", nullable = false) private long currentSeq;
    @Column(name = "updated_at", nullable = false) private OffsetDateTime updatedAt = OffsetDateTime.now();

    protected ChainHeadEntity() {}
    public ChainHeadEntity(UUID tenantId, String currentHash, long currentSeq) {
        this.tenantId = tenantId; this.currentHash = currentHash; this.currentSeq = currentSeq;
    }

    public UUID getTenantId() { return tenantId; }
    public String getCurrentHash() { return currentHash; }
    public void setCurrentHash(String v) { this.currentHash = v; }
    public long getCurrentSeq() { return currentSeq; }
    public void setCurrentSeq(long v) { this.currentSeq = v; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime v) { this.updatedAt = v; }
}
