package zw.gov.mohcc.impilo.pct.persistence.entity;

import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.UUID;

/** A reconciliation event. An unfinished one must never read as a clean one. See V107__medication_reconciliation.sql. */
@Entity
@Table(name = "pct_medication_reconciliations")
public class MedicationReconciliationEntity {

    @Id
    @Column(name = "reconciliation_id")
    private UUID reconciliationId;

    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(name = "subject_cpid")
    private String subjectCpid;

    @Column(name = "journey_id")
    private String journeyId;

    @Column(name = "encounter_id")
    private String encounterId;

    @Column(name = "episode_id")
    private UUID episodeId;

    @Column(name = "context")
    private String context;

    @Column(name = "status")
    private String status;

    @Column(name = "sources")
    private String sources;

    @Column(name = "completed_by")
    private String completedBy;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    @Column(name = "abandoned_reason")
    private String abandonedReason;

    @Column(name = "started_by")
    private String startedBy;

    @Column(name = "started_at")
    private OffsetDateTime startedAt;

    @Column(name = "notes")
    private String notes;

    @PrePersist
    protected void onCreate() {
        if (reconciliationId == null) reconciliationId = UUID.randomUUID();
        if (startedAt == null) startedAt = OffsetDateTime.now();
    }

    public UUID getReconciliationId() { return reconciliationId; }
    public void setReconciliationId(UUID v) { this.reconciliationId = v; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID v) { this.tenantId = v; }
    public String getSubjectCpid() { return subjectCpid; }
    public void setSubjectCpid(String v) { this.subjectCpid = v; }
    public String getJourneyId() { return journeyId; }
    public void setJourneyId(String v) { this.journeyId = v; }
    public String getEncounterId() { return encounterId; }
    public void setEncounterId(String v) { this.encounterId = v; }
    public UUID getEpisodeId() { return episodeId; }
    public void setEpisodeId(UUID v) { this.episodeId = v; }
    public String getContext() { return context; }
    public void setContext(String v) { this.context = v; }
    public String getStatus() { return status; }
    public void setStatus(String v) { this.status = v; }
    public String getSources() { return sources; }
    public void setSources(String v) { this.sources = v; }
    public String getCompletedBy() { return completedBy; }
    public void setCompletedBy(String v) { this.completedBy = v; }
    public OffsetDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(OffsetDateTime v) { this.completedAt = v; }
    public String getAbandonedReason() { return abandonedReason; }
    public void setAbandonedReason(String v) { this.abandonedReason = v; }
    public String getStartedBy() { return startedBy; }
    public void setStartedBy(String v) { this.startedBy = v; }
    public OffsetDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(OffsetDateTime v) { this.startedAt = v; }
    public String getNotes() { return notes; }
    public void setNotes(String v) { this.notes = v; }
}
