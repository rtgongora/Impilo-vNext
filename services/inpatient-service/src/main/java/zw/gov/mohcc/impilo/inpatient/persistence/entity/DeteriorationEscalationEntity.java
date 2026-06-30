package zw.gov.mohcc.impilo.inpatient.persistence.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * A deterioration escalation raised when an Early Warning Score crosses the configured threshold.
 * Carries a response-due deadline; if not responded to in time it becomes OVERDUE and a Rito safety
 * case is linked. Inpatient owns the escalation workflow; Rito owns the safety case (link only).
 */
@Entity
@Table(name = "deterioration_escalation", schema = "inpatient")
public class DeteriorationEscalationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "escalation_id")
    private UUID escalationId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "facility_id")
    private UUID facilityId;

    @Column(name = "ward_id")
    private UUID wardId;

    @Column(name = "subject_cpid", nullable = false)
    private String subjectCpid;

    @Column(name = "admission_ref")
    private UUID admissionRef;

    @Column(name = "encounter_id")
    private UUID encounterId;

    @Column(name = "score_id")
    private UUID scoreId;

    @Column(name = "total_score", nullable = false)
    private Integer totalScore;

    @Column(name = "risk_level", nullable = false)
    private String riskLevel;

    @Column(name = "status", nullable = false)
    private String status = "RAISED";

    @Column(name = "raised_by")
    private String raisedBy;

    @Column(name = "raised_at", nullable = false)
    private OffsetDateTime raisedAt;

    @Column(name = "response_due_at", nullable = false)
    private OffsetDateTime responseDueAt;

    @Column(name = "acknowledged_by")
    private String acknowledgedBy;

    @Column(name = "acknowledged_at")
    private OffsetDateTime acknowledgedAt;

    @Column(name = "responded_by")
    private String respondedBy;

    @Column(name = "responded_at")
    private OffsetDateTime respondedAt;

    @Column(name = "response_note")
    private String responseNote;

    @Column(name = "rito_case_ref")
    private String ritoCaseRef;

    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @PrePersist
    void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        if (raisedAt == null) raisedAt = now;
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    public UUID getEscalationId() { return escalationId; }
    public void setEscalationId(UUID v) { this.escalationId = v; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID v) { this.tenantId = v; }
    public UUID getFacilityId() { return facilityId; }
    public void setFacilityId(UUID v) { this.facilityId = v; }
    public UUID getWardId() { return wardId; }
    public void setWardId(UUID v) { this.wardId = v; }
    public String getSubjectCpid() { return subjectCpid; }
    public void setSubjectCpid(String v) { this.subjectCpid = v; }
    public UUID getAdmissionRef() { return admissionRef; }
    public void setAdmissionRef(UUID v) { this.admissionRef = v; }
    public UUID getEncounterId() { return encounterId; }
    public void setEncounterId(UUID v) { this.encounterId = v; }
    public UUID getScoreId() { return scoreId; }
    public void setScoreId(UUID v) { this.scoreId = v; }
    public Integer getTotalScore() { return totalScore; }
    public void setTotalScore(Integer v) { this.totalScore = v; }
    public String getRiskLevel() { return riskLevel; }
    public void setRiskLevel(String v) { this.riskLevel = v; }
    public String getStatus() { return status; }
    public void setStatus(String v) { this.status = v; }
    public String getRaisedBy() { return raisedBy; }
    public void setRaisedBy(String v) { this.raisedBy = v; }
    public OffsetDateTime getRaisedAt() { return raisedAt; }
    public void setRaisedAt(OffsetDateTime v) { this.raisedAt = v; }
    public OffsetDateTime getResponseDueAt() { return responseDueAt; }
    public void setResponseDueAt(OffsetDateTime v) { this.responseDueAt = v; }
    public String getAcknowledgedBy() { return acknowledgedBy; }
    public void setAcknowledgedBy(String v) { this.acknowledgedBy = v; }
    public OffsetDateTime getAcknowledgedAt() { return acknowledgedAt; }
    public void setAcknowledgedAt(OffsetDateTime v) { this.acknowledgedAt = v; }
    public String getRespondedBy() { return respondedBy; }
    public void setRespondedBy(String v) { this.respondedBy = v; }
    public OffsetDateTime getRespondedAt() { return respondedAt; }
    public void setRespondedAt(OffsetDateTime v) { this.respondedAt = v; }
    public String getResponseNote() { return responseNote; }
    public void setResponseNote(String v) { this.responseNote = v; }
    public String getRitoCaseRef() { return ritoCaseRef; }
    public void setRitoCaseRef(String v) { this.ritoCaseRef = v; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime v) { this.createdAt = v; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime v) { this.updatedAt = v; }
}
