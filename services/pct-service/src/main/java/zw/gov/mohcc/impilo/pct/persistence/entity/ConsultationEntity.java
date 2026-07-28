package zw.gov.mohcc.impilo.pct.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * A question asked of another team about a patient this team keeps (brief.md §14, §23.10).
 *
 * <p>{@link #owningService} is the reason this table exists. Loss of ownership is rarely a decision
 * anybody makes; it is what happens when nobody records who still holds the patient and both teams
 * assume the other does. See {@code V114__consultation_and_mdt.sql}.</p>
 */
@Entity
@Table(name = "pct_consultations")
public class ConsultationEntity {

    @Id
    @Column(name = "consultation_id")
    private UUID consultationId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "subject_cpid", nullable = false)
    private String subjectCpid;

    @Column(name = "journey_id")
    private UUID journeyId;

    @Column(name = "encounter_id")
    private UUID encounterId;

    @Column(name = "requesting_service", nullable = false)
    private String requestingService;

    @Column(name = "requesting_actor", nullable = false)
    private String requestingActor;

    @Column(name = "asked_of_service", nullable = false)
    private String askedOfService;

    @Column(name = "question", nullable = false)
    private String question;

    @Column(name = "clinical_summary")
    private String clinicalSummary;

    @Column(name = "urgency", nullable = false)
    private String urgency = "ROUTINE";

    @Column(name = "status", nullable = false)
    private String status = "REQUESTED";

    /** Never changed by answering. A takeover is a separate, explicit act. */
    @Column(name = "owning_service", nullable = false)
    private String owningService;

    @Column(name = "advice")
    private String advice;

    @Column(name = "responded_by")
    private String respondedBy;

    @Column(name = "responded_at")
    private OffsetDateTime respondedAt;

    @Column(name = "takeover_recommended", nullable = false)
    private boolean takeoverRecommended;

    @Column(name = "decline_reason")
    private String declineReason;

    @Column(name = "requested_at", nullable = false)
    private OffsetDateTime requestedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (consultationId == null) consultationId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        if (createdAt == null) createdAt = now;
        if (requestedAt == null) requestedAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() { updatedAt = OffsetDateTime.now(); }

    public UUID getConsultationId() { return consultationId; }
    public void setConsultationId(UUID v) { this.consultationId = v; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID v) { this.tenantId = v; }
    public String getSubjectCpid() { return subjectCpid; }
    public void setSubjectCpid(String v) { this.subjectCpid = v; }
    public UUID getJourneyId() { return journeyId; }
    public void setJourneyId(UUID v) { this.journeyId = v; }
    public UUID getEncounterId() { return encounterId; }
    public void setEncounterId(UUID v) { this.encounterId = v; }
    public String getRequestingService() { return requestingService; }
    public void setRequestingService(String v) { this.requestingService = v; }
    public String getRequestingActor() { return requestingActor; }
    public void setRequestingActor(String v) { this.requestingActor = v; }
    public String getAskedOfService() { return askedOfService; }
    public void setAskedOfService(String v) { this.askedOfService = v; }
    public String getQuestion() { return question; }
    public void setQuestion(String v) { this.question = v; }
    public String getClinicalSummary() { return clinicalSummary; }
    public void setClinicalSummary(String v) { this.clinicalSummary = v; }
    public String getUrgency() { return urgency; }
    public void setUrgency(String v) { this.urgency = v; }
    public String getStatus() { return status; }
    public void setStatus(String v) { this.status = v; }
    public String getOwningService() { return owningService; }
    public void setOwningService(String v) { this.owningService = v; }
    public String getAdvice() { return advice; }
    public void setAdvice(String v) { this.advice = v; }
    public String getRespondedBy() { return respondedBy; }
    public void setRespondedBy(String v) { this.respondedBy = v; }
    public OffsetDateTime getRespondedAt() { return respondedAt; }
    public void setRespondedAt(OffsetDateTime v) { this.respondedAt = v; }
    public boolean isTakeoverRecommended() { return takeoverRecommended; }
    public void setTakeoverRecommended(boolean v) { this.takeoverRecommended = v; }
    public String getDeclineReason() { return declineReason; }
    public void setDeclineReason(String v) { this.declineReason = v; }
    public OffsetDateTime getRequestedAt() { return requestedAt; }
    public void setRequestedAt(OffsetDateTime v) { this.requestedAt = v; }
}
