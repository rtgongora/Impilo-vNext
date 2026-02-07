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
 * Signed audit export bundle record for legal defensibility.
 * Each export captures a range of audit events, their aggregate hash,
 * and an Ed25519 signature produced by tshepo-keys-service.
 */
@Entity
@Table(name = "audit_export", schema = "tshepo_audit")
public class AuditExportEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "requested_by", nullable = false, length = 255, updatable = false)
    private String requestedBy;

    @Column(name = "from_sequence", nullable = false, updatable = false)
    private Long fromSequence;

    @Column(name = "to_sequence", nullable = false, updatable = false)
    private Long toSequence;

    @Column(name = "entry_count", nullable = false, updatable = false)
    private Integer entryCount;

    @Column(name = "bundle_hash", nullable = false, length = 64, updatable = false)
    private String bundleHash;

    @Column(name = "signature", nullable = false, columnDefinition = "TEXT", updatable = false)
    private String signature;

    @Column(name = "status", nullable = false, length = 16)
    private String status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public AuditExportEntity() {
        // JPA requires default constructor
    }

    // --- Getters and Setters ---

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }

    public String getRequestedBy() {
        return requestedBy;
    }

    public void setRequestedBy(String requestedBy) {
        this.requestedBy = requestedBy;
    }

    public Long getFromSequence() {
        return fromSequence;
    }

    public void setFromSequence(Long fromSequence) {
        this.fromSequence = fromSequence;
    }

    public Long getToSequence() {
        return toSequence;
    }

    public void setToSequence(Long toSequence) {
        this.toSequence = toSequence;
    }

    public Integer getEntryCount() {
        return entryCount;
    }

    public void setEntryCount(Integer entryCount) {
        this.entryCount = entryCount;
    }

    public String getBundleHash() {
        return bundleHash;
    }

    public void setBundleHash(String bundleHash) {
        this.bundleHash = bundleHash;
    }

    public String getSignature() {
        return signature;
    }

    public void setSignature(String signature) {
        this.signature = signature;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
