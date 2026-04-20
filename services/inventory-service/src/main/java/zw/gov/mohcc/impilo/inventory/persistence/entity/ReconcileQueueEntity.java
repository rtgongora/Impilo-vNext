package zw.gov.mohcc.impilo.inventory.persistence.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;
import zw.gov.mohcc.impilo.inventory.domain.ReconcileStatus;

/**
 * Reconciliation queue entry comparing internal vs external stock quantities.
 * Supports confidence-scored matching and operator resolution workflow.
 */
@Entity
@Table(name = "inv_reconcile_queue")
public class ReconcileQueueEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "rec_id", nullable = false)
    private UUID recId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "facility_id", nullable = false)
    private UUID facilityId;

    @Column(name = "store_id", nullable = false)
    private UUID storeId;

    @Column(name = "item_code", nullable = false)
    private String itemCode;

    @Column(name = "batch")
    private String batch;

    @Column(name = "internal_qty")
    private Integer internalQty;

    @Column(name = "external_qty")
    private Integer externalQty;

    @Column(name = "confidence", precision = 5, scale = 4)
    private BigDecimal confidence;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ReconcileStatus status = ReconcileStatus.PENDING;

    @JdbcTypeCode(SqlTypes.JSON)


    @Column(name = "payload", columnDefinition = "jsonb")
    private String payload;

    @Column(name = "ops_notes", columnDefinition = "TEXT")
    private String opsNotes;

    @Column(name = "resolved_by")
    private String resolvedBy;

    @Column(name = "resolved_at")
    private OffsetDateTime resolvedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
    }

    // Getters and setters

    public UUID getRecId() { return recId; }
    public void setRecId(UUID recId) { this.recId = recId; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public UUID getFacilityId() { return facilityId; }
    public void setFacilityId(UUID facilityId) { this.facilityId = facilityId; }

    public UUID getStoreId() { return storeId; }
    public void setStoreId(UUID storeId) { this.storeId = storeId; }

    public String getItemCode() { return itemCode; }
    public void setItemCode(String itemCode) { this.itemCode = itemCode; }

    public String getBatch() { return batch; }
    public void setBatch(String batch) { this.batch = batch; }

    public Integer getInternalQty() { return internalQty; }
    public void setInternalQty(Integer internalQty) { this.internalQty = internalQty; }

    public Integer getExternalQty() { return externalQty; }
    public void setExternalQty(Integer externalQty) { this.externalQty = externalQty; }

    public BigDecimal getConfidence() { return confidence; }
    public void setConfidence(BigDecimal confidence) { this.confidence = confidence; }

    public ReconcileStatus getStatus() { return status; }
    public void setStatus(ReconcileStatus status) { this.status = status; }

    public String getPayload() { return payload; }
    public void setPayload(String payload) { this.payload = payload; }

    public String getOpsNotes() { return opsNotes; }
    public void setOpsNotes(String opsNotes) { this.opsNotes = opsNotes; }

    public String getResolvedBy() { return resolvedBy; }
    public void setResolvedBy(String resolvedBy) { this.resolvedBy = resolvedBy; }

    public OffsetDateTime getResolvedAt() { return resolvedAt; }
    public void setResolvedAt(OffsetDateTime resolvedAt) { this.resolvedAt = resolvedAt; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
