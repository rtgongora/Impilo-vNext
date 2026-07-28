package zw.gov.mohcc.impilo.surgery.persistence.entity;

import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Discharge/follow-up items with no other home (SB-2, surgical domain pack §18). Surveillance is
 * PCT's ({@code pct_care_plans}) — {@code surveillanceplanRef} is a reference only.
 */
@Entity
@Table(name = "surgical_followup", schema = "surgery")
public class SurgicalFollowupEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "surgical_episode_id", nullable = false)
    private UUID surgicalEpisodeId;

    @Column(columnDefinition = "text")
    private String restrictions;

    @Column(name = "fit_note_notes", columnDefinition = "text")
    private String fitNoteNotes;

    @Column(name = "fit_note_issued_at")
    private OffsetDateTime fitNoteIssuedAt;

    @Column(name = "fit_note_issued_by")
    private String fitNoteIssuedBy;

    @Column(name = "transport_arranged", nullable = false)
    private boolean transportArranged;

    @Column(name = "transport_notes", columnDefinition = "text")
    private String transportNotes;

    @Column(name = "future_surgery_intent", columnDefinition = "text")
    private String futureSurgeryIntent;

    @Column(name = "surveillance_plan_ref")
    private UUID surveillancePlanRef;

    @Column(name = "recorded_by")
    private String recordedBy;

    @Column(name = "recorded_at")
    private OffsetDateTime recordedAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    void prePersist() {
        OffsetDateTime now = OffsetDateTime.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID v) { this.id = v; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID v) { this.tenantId = v; }
    public UUID getSurgicalEpisodeId() { return surgicalEpisodeId; }
    public void setSurgicalEpisodeId(UUID v) { this.surgicalEpisodeId = v; }
    public String getRestrictions() { return restrictions; }
    public void setRestrictions(String v) { this.restrictions = v; }
    public String getFitNoteNotes() { return fitNoteNotes; }
    public void setFitNoteNotes(String v) { this.fitNoteNotes = v; }
    public OffsetDateTime getFitNoteIssuedAt() { return fitNoteIssuedAt; }
    public void setFitNoteIssuedAt(OffsetDateTime v) { this.fitNoteIssuedAt = v; }
    public String getFitNoteIssuedBy() { return fitNoteIssuedBy; }
    public void setFitNoteIssuedBy(String v) { this.fitNoteIssuedBy = v; }
    public boolean isTransportArranged() { return transportArranged; }
    public void setTransportArranged(boolean v) { this.transportArranged = v; }
    public String getTransportNotes() { return transportNotes; }
    public void setTransportNotes(String v) { this.transportNotes = v; }
    public String getFutureSurgeryIntent() { return futureSurgeryIntent; }
    public void setFutureSurgeryIntent(String v) { this.futureSurgeryIntent = v; }
    public UUID getSurveillancePlanRef() { return surveillancePlanRef; }
    public void setSurveillancePlanRef(UUID v) { this.surveillancePlanRef = v; }
    public String getRecordedBy() { return recordedBy; }
    public void setRecordedBy(String v) { this.recordedBy = v; }
    public OffsetDateTime getRecordedAt() { return recordedAt; }
    public void setRecordedAt(OffsetDateTime v) { this.recordedAt = v; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
