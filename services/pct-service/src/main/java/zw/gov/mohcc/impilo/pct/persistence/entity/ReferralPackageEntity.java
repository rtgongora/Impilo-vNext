package zw.gov.mohcc.impilo.pct.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "pct_referral_packages")
public class ReferralPackageEntity {
    @Id
    @Column(name = "referral_id", nullable = false)
    private UUID referralId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "journey_id", nullable = false)
    private String journeyId;

    @Column(name = "encounter_id")
    private Long encounterId;

    @Column(name = "patient_cpid", nullable = false)
    private String patientCpid;

    @Column(name = "provider_id")
    private String providerId;

    @Column(name = "facility_id")
    private UUID facilityId;

    @Column(name = "workspace_id")
    private UUID workspaceId;

    @Column(name = "modality", nullable = false)
    private String modality = "virtual";

    @Column(name = "virtual_mode")
    private String virtualMode;

    @Column(name = "referral_package_status", nullable = false)
    private String referralPackageStatus = "DRAFT";

    @Column(name = "workflow_stage", nullable = false)
    private int workflowStage = 1;

    @Column(name = "urgency", nullable = false)
    private String urgency = "ROUTINE";

    @Column(name = "specialty")
    private String specialty;

    @Column(name = "clinical_question")
    private String clinicalQuestion;

    @Column(name = "referral_letter")
    private String referralLetter;

    @Column(name = "patient_summary")
    private String patientSummary;

    @Column(name = "visit_summary")
    private String visitSummary;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "attachment_document_ids", columnDefinition = "jsonb")
    private String attachmentDocumentIds = "[]";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "routing_target", columnDefinition = "jsonb")
    private String routingTarget = "{}";

    @Column(name = "consent_required", nullable = false)
    private boolean consentRequired;

    @Column(name = "consent_status", nullable = false)
    private String consentStatus = "NOT_REQUIRED";

    @Column(name = "consent_type")
    private String consentType;

    @Column(name = "consent_reference")
    private String consentReference;

    @Column(name = "mvumo_session_id")
    private String mvumoSessionId;

    @Column(name = "tshepo_decision_id")
    private String tshepoDecisionId;

    @Column(name = "consultation_response")
    private String consultationResponse;

    @Column(name = "submitted_at")
    private OffsetDateTime submittedAt;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
        updatedAt = OffsetDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    public UUID getReferralId() { return referralId; }
    public void setReferralId(UUID referralId) { this.referralId = referralId; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public String getJourneyId() { return journeyId; }
    public void setJourneyId(String journeyId) { this.journeyId = journeyId; }
    public Long getEncounterId() { return encounterId; }
    public void setEncounterId(Long encounterId) { this.encounterId = encounterId; }
    public String getPatientCpid() { return patientCpid; }
    public void setPatientCpid(String patientCpid) { this.patientCpid = patientCpid; }
    public String getProviderId() { return providerId; }
    public void setProviderId(String providerId) { this.providerId = providerId; }
    public UUID getFacilityId() { return facilityId; }
    public void setFacilityId(UUID facilityId) { this.facilityId = facilityId; }
    public UUID getWorkspaceId() { return workspaceId; }
    public void setWorkspaceId(UUID workspaceId) { this.workspaceId = workspaceId; }
    public String getModality() { return modality; }
    public void setModality(String modality) { this.modality = modality; }
    public String getVirtualMode() { return virtualMode; }
    public void setVirtualMode(String virtualMode) { this.virtualMode = virtualMode; }
    public String getReferralPackageStatus() { return referralPackageStatus; }
    public void setReferralPackageStatus(String referralPackageStatus) { this.referralPackageStatus = referralPackageStatus; }
    public int getWorkflowStage() { return workflowStage; }
    public void setWorkflowStage(int workflowStage) { this.workflowStage = workflowStage; }
    public String getUrgency() { return urgency; }
    public void setUrgency(String urgency) { this.urgency = urgency; }
    public String getSpecialty() { return specialty; }
    public void setSpecialty(String specialty) { this.specialty = specialty; }
    public String getClinicalQuestion() { return clinicalQuestion; }
    public void setClinicalQuestion(String clinicalQuestion) { this.clinicalQuestion = clinicalQuestion; }
    public String getReferralLetter() { return referralLetter; }
    public void setReferralLetter(String referralLetter) { this.referralLetter = referralLetter; }
    public String getPatientSummary() { return patientSummary; }
    public void setPatientSummary(String patientSummary) { this.patientSummary = patientSummary; }
    public String getVisitSummary() { return visitSummary; }
    public void setVisitSummary(String visitSummary) { this.visitSummary = visitSummary; }
    public String getAttachmentDocumentIds() { return attachmentDocumentIds; }
    public void setAttachmentDocumentIds(String attachmentDocumentIds) { this.attachmentDocumentIds = attachmentDocumentIds; }
    public String getRoutingTarget() { return routingTarget; }
    public void setRoutingTarget(String routingTarget) { this.routingTarget = routingTarget; }
    public boolean isConsentRequired() { return consentRequired; }
    public void setConsentRequired(boolean consentRequired) { this.consentRequired = consentRequired; }
    public String getConsentStatus() { return consentStatus; }
    public void setConsentStatus(String consentStatus) { this.consentStatus = consentStatus; }
    public String getConsentType() { return consentType; }
    public void setConsentType(String consentType) { this.consentType = consentType; }
    public String getConsentReference() { return consentReference; }
    public void setConsentReference(String consentReference) { this.consentReference = consentReference; }
    public String getMvumoSessionId() { return mvumoSessionId; }
    public void setMvumoSessionId(String mvumoSessionId) { this.mvumoSessionId = mvumoSessionId; }
    public String getTshepoDecisionId() { return tshepoDecisionId; }
    public void setTshepoDecisionId(String tshepoDecisionId) { this.tshepoDecisionId = tshepoDecisionId; }
    public String getConsultationResponse() { return consultationResponse; }
    public void setConsultationResponse(String consultationResponse) { this.consultationResponse = consultationResponse; }
    public OffsetDateTime getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(OffsetDateTime submittedAt) { this.submittedAt = submittedAt; }
    public OffsetDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(OffsetDateTime completedAt) { this.completedAt = completedAt; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
