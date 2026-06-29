package zw.gov.mohcc.impilo.inventory.persistence.entity;

import jakarta.persistence.*;
import zw.gov.mohcc.impilo.inventory.domain.RecallSeverity;
import zw.gov.mohcc.impilo.inventory.domain.RecallSource;
import zw.gov.mohcc.impilo.inventory.domain.RecallStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * A product recall. Opening a recall freezes affected batches (transitions
 * them to RECALLED); closing records the response outcome.
 */
@Entity
@Table(name = "inv_recall_events")
public class RecallEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "recall_id", nullable = false)
    private UUID recallId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "recall_reference")
    private String recallReference;

    @Column(name = "item_code")
    private String itemCode;

    @Column(name = "batch_number")
    private String batchNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 20)
    private RecallSource source = RecallSource.INTERNAL;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false, length = 20)
    private RecallSeverity severity = RecallSeverity.MEDIUM;

    @Column(name = "reason", length = 1000)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private RecallStatus status = RecallStatus.OPEN;

    @Column(name = "affected_batches", nullable = false)
    private int affectedBatches = 0;

    @Column(name = "initiated_by")
    private String initiatedBy;

    @Column(name = "closure_note", length = 1000)
    private String closureNote;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @Column(name = "closed_at")
    private OffsetDateTime closedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
        updatedAt = OffsetDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    // Getters and setters

    public UUID getRecallId() { return recallId; }
    public void setRecallId(UUID recallId) { this.recallId = recallId; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public String getRecallReference() { return recallReference; }
    public void setRecallReference(String recallReference) { this.recallReference = recallReference; }

    public String getItemCode() { return itemCode; }
    public void setItemCode(String itemCode) { this.itemCode = itemCode; }

    public String getBatchNumber() { return batchNumber; }
    public void setBatchNumber(String batchNumber) { this.batchNumber = batchNumber; }

    public RecallSource getSource() { return source; }
    public void setSource(RecallSource source) { this.source = source; }

    public RecallSeverity getSeverity() { return severity; }
    public void setSeverity(RecallSeverity severity) { this.severity = severity; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public RecallStatus getStatus() { return status; }
    public void setStatus(RecallStatus status) { this.status = status; }

    public int getAffectedBatches() { return affectedBatches; }
    public void setAffectedBatches(int affectedBatches) { this.affectedBatches = affectedBatches; }

    public String getInitiatedBy() { return initiatedBy; }
    public void setInitiatedBy(String initiatedBy) { this.initiatedBy = initiatedBy; }

    public String getClosureNote() { return closureNote; }
    public void setClosureNote(String closureNote) { this.closureNote = closureNote; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }

    public OffsetDateTime getClosedAt() { return closedAt; }
    public void setClosedAt(OffsetDateTime closedAt) { this.closedAt = closedAt; }
}
