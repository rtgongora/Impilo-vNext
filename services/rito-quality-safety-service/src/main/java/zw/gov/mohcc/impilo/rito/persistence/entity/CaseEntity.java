package zw.gov.mohcc.impilo.rito.persistence.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "rit_case", schema = "rito")
public class CaseEntity {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "case_reference", nullable = false, length = 48)
    private String caseReference;

    @Column(name = "case_type", nullable = false, length = 48)
    private String caseType;

    @Column(name = "pillar", nullable = false, length = 48)
    private String pillar;

    @Column(name = "category", length = 64)
    private String category;

    @Column(name = "title", length = 512)
    private String title;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "severity", nullable = false, length = 32)
    private String severity;

    @Column(name = "status", nullable = false, length = 48)
    private String status;

    @Column(name = "source", nullable = false, length = 48)
    private String source;

    @Column(name = "anonymous", nullable = false)
    private Boolean anonymous;

    /**
     * SHA-256 hex of the one-time claim code issued on anonymous public intake
     * (gateway-public-lane ADR W4). Null for cases opened through authenticated lanes.
     * The plaintext code is returned once at creation and never stored.
     */
    @Column(name = "claim_code_hash", length = 64)
    private String claimCodeHash;

    @Column(name = "sensitive", nullable = false)
    private Boolean sensitive;

    /**
     * Citizen-facing feedback category from the public feedback loop (distinct from the clinical
     * case type). Null for cases opened outside the routed public lane. See
     * {@code PublicFeedbackRoutingPolicy}.
     */
    @Column(name = "feedback_category", length = 64)
    private String feedbackCategory;

    /** Operational owner routed from the feedback category — the team whose queue must act. */
    @Column(name = "operational_owner", length = 64)
    private String operationalOwner;

    /** Triage priority routed from the feedback category: CRITICAL / HIGH / ROUTINE. */
    @Column(name = "triage_priority", length = 16)
    private String triagePriority;

    @Column(name = "facility_id")
    private UUID facilityId;

    @Column(name = "district_id")
    private UUID districtId;

    @Column(name = "province_id")
    private UUID provinceId;

    @Column(name = "programme_id")
    private UUID programmeId;

    @Column(name = "reporter_actor_id", length = 128)
    private String reporterActorId;

    @Column(name = "reporter_actor_type", length = 48)
    private String reporterActorType;

    @Column(name = "subject_health_id", length = 128)
    private String subjectHealthId;

    @Column(name = "assigned_to_actor_id", length = 128)
    private String assignedToActorId;

    @Column(name = "assigned_to_actor_type", length = 48)
    private String assignedToActorType;

    @Column(name = "sla_policy_id")
    private UUID slaPolicyId;

    @Column(name = "due_at")
    private OffsetDateTime dueAt;

    @Column(name = "acknowledged_at")
    private OffsetDateTime acknowledgedAt;

    @Column(name = "triaged_at")
    private OffsetDateTime triagedAt;

    @Column(name = "resolved_at")
    private OffsetDateTime resolvedAt;

    @Column(name = "closed_at")
    private OffsetDateTime closedAt;

    @Column(name = "resolution_summary", columnDefinition = "TEXT")
    private String resolutionSummary;

    @Column(name = "closure_outcome", length = 48)
    private String closureOutcome;

    @Column(name = "satisfaction_score")
    private Integer satisfactionScore;

    @Column(name = "ai_suggested_severity", length = 32)
    private String aiSuggestedSeverity;

    @Column(name = "ai_suggested_routing", length = 128)
    private String aiSuggestedRouting;

    @Column(name = "ai_rationale", columnDefinition = "TEXT")
    private String aiRationale;

    @Column(name = "linked_patient_safety_case_id", length = 128)
    private String linkedPatientSafetyCaseId;

    @Column(name = "linked_haemovigilance_case_id", length = 128)
    private String linkedHaemovigilanceCaseId;

    @Column(name = "linked_regulatory_finding_id", length = 128)
    private String linkedRegulatoryFindingId;

    @Column(name = "linked_support_ticket_id", length = 128)
    private String linkedSupportTicketId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", nullable = false, columnDefinition = "jsonb")
    private String metadataJson;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "created_by", length = 128)
    private String createdBy;

    @PrePersist
    protected void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        createdAt = OffsetDateTime.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public String getClaimCodeHash() { return claimCodeHash; }
    public void setClaimCodeHash(String claimCodeHash) { this.claimCodeHash = claimCodeHash; }

    public String getCaseReference() { return caseReference; }
    public void setCaseReference(String caseReference) { this.caseReference = caseReference; }
    public String getCaseType() { return caseType; }
    public void setCaseType(String caseType) { this.caseType = caseType; }
    public String getPillar() { return pillar; }
    public void setPillar(String pillar) { this.pillar = pillar; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public Boolean getAnonymous() { return anonymous; }
    public void setAnonymous(Boolean anonymous) { this.anonymous = anonymous; }
    public Boolean getSensitive() { return sensitive; }
    public void setSensitive(Boolean sensitive) { this.sensitive = sensitive; }
    public String getFeedbackCategory() { return feedbackCategory; }
    public void setFeedbackCategory(String feedbackCategory) { this.feedbackCategory = feedbackCategory; }
    public String getOperationalOwner() { return operationalOwner; }
    public void setOperationalOwner(String operationalOwner) { this.operationalOwner = operationalOwner; }
    public String getTriagePriority() { return triagePriority; }
    public void setTriagePriority(String triagePriority) { this.triagePriority = triagePriority; }
    public UUID getFacilityId() { return facilityId; }
    public void setFacilityId(UUID facilityId) { this.facilityId = facilityId; }
    public UUID getDistrictId() { return districtId; }
    public void setDistrictId(UUID districtId) { this.districtId = districtId; }
    public UUID getProvinceId() { return provinceId; }
    public void setProvinceId(UUID provinceId) { this.provinceId = provinceId; }
    public UUID getProgrammeId() { return programmeId; }
    public void setProgrammeId(UUID programmeId) { this.programmeId = programmeId; }
    public String getReporterActorId() { return reporterActorId; }
    public void setReporterActorId(String reporterActorId) { this.reporterActorId = reporterActorId; }
    public String getReporterActorType() { return reporterActorType; }
    public void setReporterActorType(String reporterActorType) { this.reporterActorType = reporterActorType; }
    public String getSubjectHealthId() { return subjectHealthId; }
    public void setSubjectHealthId(String subjectHealthId) { this.subjectHealthId = subjectHealthId; }
    public String getAssignedToActorId() { return assignedToActorId; }
    public void setAssignedToActorId(String assignedToActorId) { this.assignedToActorId = assignedToActorId; }
    public String getAssignedToActorType() { return assignedToActorType; }
    public void setAssignedToActorType(String assignedToActorType) { this.assignedToActorType = assignedToActorType; }
    public UUID getSlaPolicyId() { return slaPolicyId; }
    public void setSlaPolicyId(UUID slaPolicyId) { this.slaPolicyId = slaPolicyId; }
    public OffsetDateTime getDueAt() { return dueAt; }
    public void setDueAt(OffsetDateTime dueAt) { this.dueAt = dueAt; }
    public OffsetDateTime getAcknowledgedAt() { return acknowledgedAt; }
    public void setAcknowledgedAt(OffsetDateTime acknowledgedAt) { this.acknowledgedAt = acknowledgedAt; }
    public OffsetDateTime getTriagedAt() { return triagedAt; }
    public void setTriagedAt(OffsetDateTime triagedAt) { this.triagedAt = triagedAt; }
    public OffsetDateTime getResolvedAt() { return resolvedAt; }
    public void setResolvedAt(OffsetDateTime resolvedAt) { this.resolvedAt = resolvedAt; }
    public OffsetDateTime getClosedAt() { return closedAt; }
    public void setClosedAt(OffsetDateTime closedAt) { this.closedAt = closedAt; }
    public String getResolutionSummary() { return resolutionSummary; }
    public void setResolutionSummary(String resolutionSummary) { this.resolutionSummary = resolutionSummary; }
    public String getClosureOutcome() { return closureOutcome; }
    public void setClosureOutcome(String closureOutcome) { this.closureOutcome = closureOutcome; }
    public Integer getSatisfactionScore() { return satisfactionScore; }
    public void setSatisfactionScore(Integer satisfactionScore) { this.satisfactionScore = satisfactionScore; }
    public String getAiSuggestedSeverity() { return aiSuggestedSeverity; }
    public void setAiSuggestedSeverity(String aiSuggestedSeverity) { this.aiSuggestedSeverity = aiSuggestedSeverity; }
    public String getAiSuggestedRouting() { return aiSuggestedRouting; }
    public void setAiSuggestedRouting(String aiSuggestedRouting) { this.aiSuggestedRouting = aiSuggestedRouting; }
    public String getAiRationale() { return aiRationale; }
    public void setAiRationale(String aiRationale) { this.aiRationale = aiRationale; }
    public String getLinkedPatientSafetyCaseId() { return linkedPatientSafetyCaseId; }
    public void setLinkedPatientSafetyCaseId(String linkedPatientSafetyCaseId) { this.linkedPatientSafetyCaseId = linkedPatientSafetyCaseId; }
    public String getLinkedHaemovigilanceCaseId() { return linkedHaemovigilanceCaseId; }
    public void setLinkedHaemovigilanceCaseId(String linkedHaemovigilanceCaseId) { this.linkedHaemovigilanceCaseId = linkedHaemovigilanceCaseId; }
    public String getLinkedRegulatoryFindingId() { return linkedRegulatoryFindingId; }
    public void setLinkedRegulatoryFindingId(String linkedRegulatoryFindingId) { this.linkedRegulatoryFindingId = linkedRegulatoryFindingId; }
    public String getLinkedSupportTicketId() { return linkedSupportTicketId; }
    public void setLinkedSupportTicketId(String linkedSupportTicketId) { this.linkedSupportTicketId = linkedSupportTicketId; }
    public String getMetadataJson() { return metadataJson; }
    public void setMetadataJson(String metadataJson) { this.metadataJson = metadataJson; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
}
