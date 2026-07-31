package zw.gov.mohcc.impilo.pct.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "adult_journey_positions")
public class AdultJourneyPositionEntity {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "subject_cpid", nullable = false)
    private String subjectCpid;

    @Column(name = "journey_id")
    private String journeyId;

    @Column(name = "encounter_id")
    private String encounterId;

    @Column(name = "step_key", nullable = false)
    private String stepKey;

    @Column(name = "step_index", nullable = false)
    private int stepIndex;

    @Column(name = "recorded_by")
    private String recordedBy;

    @Column(name = "recorded_at", nullable = false)
    private OffsetDateTime recordedAt;

    @Column(name = "notes")
    private String notes;

    @PrePersist
    protected void onCreate() {
        if (id == null) id = UUID.randomUUID();
        if (recordedAt == null) recordedAt = OffsetDateTime.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID v) { id = v; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID v) { tenantId = v; }
    public String getSubjectCpid() { return subjectCpid; }
    public void setSubjectCpid(String v) { subjectCpid = v; }
    public String getJourneyId() { return journeyId; }
    public void setJourneyId(String v) { journeyId = v; }
    public String getEncounterId() { return encounterId; }
    public void setEncounterId(String v) { encounterId = v; }
    public String getStepKey() { return stepKey; }
    public void setStepKey(String v) { stepKey = v; }
    public int getStepIndex() { return stepIndex; }
    public void setStepIndex(int v) { stepIndex = v; }
    public String getRecordedBy() { return recordedBy; }
    public void setRecordedBy(String v) { recordedBy = v; }
    public OffsetDateTime getRecordedAt() { return recordedAt; }
    public void setRecordedAt(OffsetDateTime v) { recordedAt = v; }
    public String getNotes() { return notes; }
    public void setNotes(String v) { notes = v; }
}
