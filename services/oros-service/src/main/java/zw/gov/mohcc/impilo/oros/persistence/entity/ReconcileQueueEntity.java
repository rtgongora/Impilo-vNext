package zw.gov.mohcc.impilo.oros.persistence.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;
import zw.gov.mohcc.impilo.oros.domain.ReconcileStatus;

/**
 * Represents a pending reconciliation entry in hybrid routing mode.
 * When an order is fulfilled both internally and via an external adapter,
 * the two results must be matched and reconciled before the order can
 * progress to completion.
 */
@Entity
@Table(name = "oros_reconcile_queue")
public class ReconcileQueueEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "rec_id", nullable = false)
    private UUID recId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "order_id", length = 26)
    private String orderId;

    @Column(name = "external_key", nullable = false, length = 255)
    private String externalKey;

    @Column(name = "external_system", nullable = false, length = 50)
    private String externalSystem;

    @Column(name = "patient_cpid", length = 64)
    private String patientCpid;

    @Column(name = "confidence", precision = 3, scale = 2)
    private BigDecimal confidence = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ReconcileStatus status = ReconcileStatus.PENDING;

    @Column(name = "ops_notes", columnDefinition = "TEXT")
    private String opsNotes;

    @Column(name = "matched_at")
    private OffsetDateTime matchedAt;

    @Column(name = "resolved_by", length = 128)
    private String resolvedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
    }

    public UUID getRecId() { return recId; }
    public void setRecId(UUID recId) { this.recId = recId; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }

    public String getExternalKey() { return externalKey; }
    public void setExternalKey(String externalKey) { this.externalKey = externalKey; }

    public String getExternalSystem() { return externalSystem; }
    public void setExternalSystem(String externalSystem) { this.externalSystem = externalSystem; }

    public String getPatientCpid() { return patientCpid; }
    public void setPatientCpid(String patientCpid) { this.patientCpid = patientCpid; }

    public BigDecimal getConfidence() { return confidence; }
    public void setConfidence(BigDecimal confidence) { this.confidence = confidence; }

    public ReconcileStatus getStatus() { return status; }
    public void setStatus(ReconcileStatus status) { this.status = status; }

    public String getOpsNotes() { return opsNotes; }
    public void setOpsNotes(String opsNotes) { this.opsNotes = opsNotes; }

    public OffsetDateTime getMatchedAt() { return matchedAt; }
    public void setMatchedAt(OffsetDateTime matchedAt) { this.matchedAt = matchedAt; }

    public String getResolvedBy() { return resolvedBy; }
    public void setResolvedBy(String resolvedBy) { this.resolvedBy = resolvedBy; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
}
