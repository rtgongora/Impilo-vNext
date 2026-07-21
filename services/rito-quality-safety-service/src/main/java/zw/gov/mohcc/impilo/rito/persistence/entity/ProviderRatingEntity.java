package zw.gov.mohcc.impilo.rito.persistence.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Contextual provider rating (RW1). A Record derived from a (mostly PCT-verified)
 * Transaction — see docs/doctrine/provider-reputation-doctrine.md. Free-text narrative
 * lives in a linked {@code rit_case}; this row carries no inline PII. Per-domain scores
 * are stored separately in {@link RatingDomainScoreEntity} and never blended.
 */
@Entity
@Table(name = "rit_provider_rating", schema = "rito")
public class ProviderRatingEntity {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "provider_public_id", nullable = false, length = 64)
    private String providerPublicId;

    @Column(name = "facility_id")
    private UUID facilityId;

    @Column(name = "service_point_id")
    private UUID servicePointId;

    @Column(name = "encounter_ref", length = 128)
    private String encounterRef;

    @Column(name = "provider_role", length = 64)
    private String providerRole;

    @Column(name = "modality", length = 24)
    private String modality;

    @Column(name = "specialty", length = 128)
    private String specialty;

    @Column(name = "verified_interaction", nullable = false)
    private Boolean verifiedInteraction = false;

    @Column(name = "verification_source", nullable = false, length = 24)
    private String verificationSource = "NONE";

    @Column(name = "respondent_class", nullable = false, length = 24)
    private String respondentClass = "CLIENT";

    @Column(name = "respondent_identity_protected", nullable = false)
    private Boolean respondentIdentityProtected = true;

    @Column(name = "respondent_actor_ref", length = 128)
    private String respondentActorRef;

    @Column(name = "reporting_period", length = 16)
    private String reportingPeriod;

    @Column(name = "overall_score")
    private BigDecimal overallScore;

    @Column(name = "narrative_case_id")
    private UUID narrativeCaseId;

    @Column(name = "moderation_state", nullable = false, length = 24)
    private String moderationState = "SUBMITTED";

    @Column(name = "provider_status_at_time", length = 48)
    private String providerStatusAtTime;

    @Column(name = "assignment_ref_at_time", length = 128)
    private String assignmentRefAtTime;

    @Column(name = "facility_id_at_time")
    private UUID facilityIdAtTime;

    @Column(name = "provider_role_at_time", length = 64)
    private String providerRoleAtTime;

    @Column(name = "submitted_at", nullable = false)
    private OffsetDateTime submittedAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (submittedAt == null) {
            submittedAt = now;
        }
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    // ── getters / setters ────────────────────────────────────────────────────
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public String getProviderPublicId() { return providerPublicId; }
    public void setProviderPublicId(String providerPublicId) { this.providerPublicId = providerPublicId; }
    public UUID getFacilityId() { return facilityId; }
    public void setFacilityId(UUID facilityId) { this.facilityId = facilityId; }
    public UUID getServicePointId() { return servicePointId; }
    public void setServicePointId(UUID servicePointId) { this.servicePointId = servicePointId; }
    public String getEncounterRef() { return encounterRef; }
    public void setEncounterRef(String encounterRef) { this.encounterRef = encounterRef; }
    public String getProviderRole() { return providerRole; }
    public void setProviderRole(String providerRole) { this.providerRole = providerRole; }
    public String getModality() { return modality; }
    public void setModality(String modality) { this.modality = modality; }
    public String getSpecialty() { return specialty; }
    public void setSpecialty(String specialty) { this.specialty = specialty; }
    public Boolean getVerifiedInteraction() { return verifiedInteraction; }
    public void setVerifiedInteraction(Boolean verifiedInteraction) { this.verifiedInteraction = verifiedInteraction; }
    public String getVerificationSource() { return verificationSource; }
    public void setVerificationSource(String verificationSource) { this.verificationSource = verificationSource; }
    public String getRespondentClass() { return respondentClass; }
    public void setRespondentClass(String respondentClass) { this.respondentClass = respondentClass; }
    public Boolean getRespondentIdentityProtected() { return respondentIdentityProtected; }
    public void setRespondentIdentityProtected(Boolean v) { this.respondentIdentityProtected = v; }
    public String getRespondentActorRef() { return respondentActorRef; }
    public void setRespondentActorRef(String respondentActorRef) { this.respondentActorRef = respondentActorRef; }
    public String getReportingPeriod() { return reportingPeriod; }
    public void setReportingPeriod(String reportingPeriod) { this.reportingPeriod = reportingPeriod; }
    public BigDecimal getOverallScore() { return overallScore; }
    public void setOverallScore(BigDecimal overallScore) { this.overallScore = overallScore; }
    public UUID getNarrativeCaseId() { return narrativeCaseId; }
    public void setNarrativeCaseId(UUID narrativeCaseId) { this.narrativeCaseId = narrativeCaseId; }
    public String getModerationState() { return moderationState; }
    public void setModerationState(String moderationState) { this.moderationState = moderationState; }
    public String getProviderStatusAtTime() { return providerStatusAtTime; }
    public void setProviderStatusAtTime(String v) { this.providerStatusAtTime = v; }
    public String getAssignmentRefAtTime() { return assignmentRefAtTime; }
    public void setAssignmentRefAtTime(String v) { this.assignmentRefAtTime = v; }
    public UUID getFacilityIdAtTime() { return facilityIdAtTime; }
    public void setFacilityIdAtTime(UUID v) { this.facilityIdAtTime = v; }
    public String getProviderRoleAtTime() { return providerRoleAtTime; }
    public void setProviderRoleAtTime(String v) { this.providerRoleAtTime = v; }
    public OffsetDateTime getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(OffsetDateTime submittedAt) { this.submittedAt = submittedAt; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
