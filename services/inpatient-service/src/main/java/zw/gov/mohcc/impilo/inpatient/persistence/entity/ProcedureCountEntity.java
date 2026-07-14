package zw.gov.mohcc.impilo.inpatient.persistence.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Surgical count (swab/instrument/needle) baseline vs closing. An unreconciled discrepancy gates the
 * WHO Sign-Out completion and auto-raises a RITO RETAINED_ITEM sentinel (RITO owns the case; rito_case_ref).
 */
@Entity
@Table(name = "procedure_count", schema = "inpatient")
public class ProcedureCountEntity {

    @Id
    @Column(name = "count_id")
    private UUID countId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "episode_id", nullable = false)
    private UUID episodeId;

    @Column(name = "kind", nullable = false)
    private String kind;   // SWAB | INSTRUMENT | NEEDLE

    @Column(name = "seq", nullable = false)
    private int seq = 1;

    @Column(name = "baseline_count")
    private Integer baselineCount;

    @Column(name = "closing_count")
    private Integer closingCount;

    @Column(name = "discrepancy")
    private Integer discrepancy;

    @Column(name = "reconciled", nullable = false)
    private boolean reconciled = false;

    @Column(name = "reconciliation_note", columnDefinition = "TEXT")
    private String reconciliationNote;

    @Column(name = "override", nullable = false)
    private boolean override = false;

    @Column(name = "override_reason", columnDefinition = "TEXT")
    private String overrideReason;

    @Column(name = "rito_case_ref")
    private String ritoCaseRef;

    @Column(name = "recorded_by")
    private String recordedBy;

    @Column(name = "idempotency_key")
    private String idempotencyKey;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    void prePersist() {
        if (countId == null) countId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() { updatedAt = OffsetDateTime.now(); }

    public UUID getCountId() { return countId; }
    public void setCountId(UUID v) { this.countId = v; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID v) { this.tenantId = v; }
    public UUID getEpisodeId() { return episodeId; }
    public void setEpisodeId(UUID v) { this.episodeId = v; }
    public String getKind() { return kind; }
    public void setKind(String v) { this.kind = v; }
    public int getSeq() { return seq; }
    public void setSeq(int v) { this.seq = v; }
    public Integer getBaselineCount() { return baselineCount; }
    public void setBaselineCount(Integer v) { this.baselineCount = v; }
    public Integer getClosingCount() { return closingCount; }
    public void setClosingCount(Integer v) { this.closingCount = v; }
    public Integer getDiscrepancy() { return discrepancy; }
    public void setDiscrepancy(Integer v) { this.discrepancy = v; }
    public boolean isReconciled() { return reconciled; }
    public void setReconciled(boolean v) { this.reconciled = v; }
    public String getReconciliationNote() { return reconciliationNote; }
    public void setReconciliationNote(String v) { this.reconciliationNote = v; }
    public boolean isOverride() { return override; }
    public void setOverride(boolean v) { this.override = v; }
    public String getOverrideReason() { return overrideReason; }
    public void setOverrideReason(String v) { this.overrideReason = v; }
    public String getRitoCaseRef() { return ritoCaseRef; }
    public void setRitoCaseRef(String v) { this.ritoCaseRef = v; }
    public String getRecordedBy() { return recordedBy; }
    public void setRecordedBy(String v) { this.recordedBy = v; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String v) { this.idempotencyKey = v; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime v) { this.createdAt = v; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime v) { this.updatedAt = v; }
}
