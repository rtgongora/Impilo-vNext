package zw.gov.mohcc.impilo.participation.persistence.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * The owning record for a citizen contribution (table {@code participation.contribution}).
 */
@Entity
@Table(name = "contribution", schema = "participation")
public class ContributionEntity {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "reference", nullable = false, length = 48)
    private String reference;

    /**
     * SHA-256 hex of the one-time claim code issued on anonymous public intake. Null for
     * contributions opened through the authenticated lane. The plaintext code is returned
     * once at creation and never stored.
     */
    @Column(name = "claim_code_hash", length = 64)
    private String claimCodeHash;

    @Column(name = "type", nullable = false, length = 32)
    private String type;

    @Column(name = "need_area", length = 128)
    private String needArea;

    @Column(name = "title", length = 512)
    private String title;

    @Column(name = "problem_or_opportunity", columnDefinition = "TEXT")
    private String problemOrOpportunity;

    @Column(name = "better_experience", columnDefinition = "TEXT")
    private String betterExperience;

    @Column(name = "beneficiary", length = 255)
    private String beneficiary;

    @Column(name = "willing_to_help", nullable = false)
    private Boolean willingToHelp = false;

    @Column(name = "submitter_mode", nullable = false, length = 24)
    private String submitterMode;

    @Column(name = "submitter_ref", length = 128)
    private String submitterRef;

    @Column(name = "status", nullable = false, length = 48)
    private String status;

    @Column(name = "merged_into_id")
    private UUID mergedIntoId;

    @Column(name = "moderation_state", nullable = false, length = 24)
    private String moderationState;

    @Column(name = "support_count", nullable = false)
    private Integer supportCount = 0;

    @Column(name = "roadmap_ref", length = 128)
    private String roadmapRef;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private String metadataJson;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "created_by", length = 128)
    private String createdBy;

    @PrePersist
    protected void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
        if (willingToHelp == null) {
            willingToHelp = false;
        }
        if (supportCount == null) {
            supportCount = 0;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public String getReference() { return reference; }
    public void setReference(String reference) { this.reference = reference; }
    public String getClaimCodeHash() { return claimCodeHash; }
    public void setClaimCodeHash(String claimCodeHash) { this.claimCodeHash = claimCodeHash; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getNeedArea() { return needArea; }
    public void setNeedArea(String needArea) { this.needArea = needArea; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getProblemOrOpportunity() { return problemOrOpportunity; }
    public void setProblemOrOpportunity(String problemOrOpportunity) { this.problemOrOpportunity = problemOrOpportunity; }
    public String getBetterExperience() { return betterExperience; }
    public void setBetterExperience(String betterExperience) { this.betterExperience = betterExperience; }
    public String getBeneficiary() { return beneficiary; }
    public void setBeneficiary(String beneficiary) { this.beneficiary = beneficiary; }
    public Boolean getWillingToHelp() { return willingToHelp; }
    public void setWillingToHelp(Boolean willingToHelp) { this.willingToHelp = willingToHelp; }
    public String getSubmitterMode() { return submitterMode; }
    public void setSubmitterMode(String submitterMode) { this.submitterMode = submitterMode; }
    public String getSubmitterRef() { return submitterRef; }
    public void setSubmitterRef(String submitterRef) { this.submitterRef = submitterRef; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public UUID getMergedIntoId() { return mergedIntoId; }
    public void setMergedIntoId(UUID mergedIntoId) { this.mergedIntoId = mergedIntoId; }
    public String getModerationState() { return moderationState; }
    public void setModerationState(String moderationState) { this.moderationState = moderationState; }
    public Integer getSupportCount() { return supportCount; }
    public void setSupportCount(Integer supportCount) { this.supportCount = supportCount; }
    public String getRoadmapRef() { return roadmapRef; }
    public void setRoadmapRef(String roadmapRef) { this.roadmapRef = roadmapRef; }
    public String getMetadataJson() { return metadataJson; }
    public void setMetadataJson(String metadataJson) { this.metadataJson = metadataJson; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
}
