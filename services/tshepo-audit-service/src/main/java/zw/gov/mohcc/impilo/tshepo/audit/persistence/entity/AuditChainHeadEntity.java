package zw.gov.mohcc.impilo.tshepo.audit.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Tracks the current hash chain head per tenant.
 * Uses PESSIMISTIC_WRITE locking for gapless sequencing.
 */
@Entity
@Table(name = "audit_chain_head", schema = "tshepo_audit")
public class AuditChainHeadEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false, unique = true)
    private UUID tenantId;

    @Column(name = "current_hash", nullable = false, length = 64)
    private String currentHash;

    @Column(name = "sequence_number", nullable = false)
    private Long sequenceNumber;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public AuditChainHeadEntity() {
        // JPA requires default constructor
    }

    // --- Getters and Setters ---

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }

    public String getCurrentHash() {
        return currentHash;
    }

    public void setCurrentHash(String currentHash) {
        this.currentHash = currentHash;
    }

    public Long getSequenceNumber() {
        return sequenceNumber;
    }

    public void setSequenceNumber(Long sequenceNumber) {
        this.sequenceNumber = sequenceNumber;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
