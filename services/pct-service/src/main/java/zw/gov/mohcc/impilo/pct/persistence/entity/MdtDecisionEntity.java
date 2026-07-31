package zw.gov.mohcc.impilo.pct.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * A multidisciplinary decision with its author, chair, participants and date (brief.md §14, §23.7).
 *
 * <p>A decision that sets treatment intent and has no author, no chair and no participants is not a
 * governed decision; it is a rumour with clinical consequences. Those three are NOT NULL for that
 * reason. See {@code V114__consultation_and_mdt.sql}.</p>
 */
@Entity
@Table(name = "pct_mdt_decisions")
public class MdtDecisionEntity {

    @Id
    @Column(name = "decision_id")
    private UUID decisionId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "subject_cpid", nullable = false)
    private String subjectCpid;

    @Column(name = "journey_id")
    private UUID journeyId;

    @Column(name = "encounter_id")
    private UUID encounterId;

    @Column(name = "episode_id")
    private UUID episodeId;

    @Column(name = "meeting_type", nullable = false)
    private String meetingType;

    @Column(name = "met_on", nullable = false)
    private LocalDate metOn;

    @Column(name = "participants", nullable = false)
    private String participants;

    @Column(name = "chaired_by", nullable = false)
    private String chairedBy;

    @Column(name = "decision", nullable = false)
    private String decision;

    /** NULL means the meeting did not set an intent — never defaulted. */
    @Column(name = "treatment_intent")
    private String treatmentIntent;

    @Column(name = "rationale")
    private String rationale;

    @Column(name = "next_action")
    private String nextAction;

    @Column(name = "responsible_service")
    private String responsibleService;

    /** Nullable link to pct_mdt_case_items when a telemedicine board case produced this decision. */
    @Column(name = "case_item_id")
    private UUID caseItemId;

    @Column(name = "recorded_by", nullable = false)
    private String recordedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (decisionId == null) decisionId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() { updatedAt = OffsetDateTime.now(); }

    public UUID getDecisionId() { return decisionId; }
    public void setDecisionId(UUID v) { this.decisionId = v; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID v) { this.tenantId = v; }
    public String getSubjectCpid() { return subjectCpid; }
    public void setSubjectCpid(String v) { this.subjectCpid = v; }
    public UUID getJourneyId() { return journeyId; }
    public void setJourneyId(UUID v) { this.journeyId = v; }
    public UUID getEncounterId() { return encounterId; }
    public void setEncounterId(UUID v) { this.encounterId = v; }
    public UUID getEpisodeId() { return episodeId; }
    public void setEpisodeId(UUID v) { this.episodeId = v; }
    public String getMeetingType() { return meetingType; }
    public void setMeetingType(String v) { this.meetingType = v; }
    public LocalDate getMetOn() { return metOn; }
    public void setMetOn(LocalDate v) { this.metOn = v; }
    public String getParticipants() { return participants; }
    public void setParticipants(String v) { this.participants = v; }
    public String getChairedBy() { return chairedBy; }
    public void setChairedBy(String v) { this.chairedBy = v; }
    public String getDecision() { return decision; }
    public void setDecision(String v) { this.decision = v; }
    public String getTreatmentIntent() { return treatmentIntent; }
    public void setTreatmentIntent(String v) { this.treatmentIntent = v; }
    public String getRationale() { return rationale; }
    public void setRationale(String v) { this.rationale = v; }
    public String getNextAction() { return nextAction; }
    public void setNextAction(String v) { this.nextAction = v; }
    public String getResponsibleService() { return responsibleService; }
    public void setResponsibleService(String v) { this.responsibleService = v; }
    public UUID getCaseItemId() { return caseItemId; }
    public void setCaseItemId(UUID v) { this.caseItemId = v; }
    public String getRecordedBy() { return recordedBy; }
    public void setRecordedBy(String v) { this.recordedBy = v; }
}
