package zw.gov.mohcc.impilo.simba.social.entity;

import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.UUID;

/** A wellness reel — short-form video referencing a MediaAsset (document-service object). */
@Entity
@Table(name = "simba_social_reel", schema = "simba")
public class ReelEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "reel_id", nullable = false, unique = true)
    private UUID reelId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "creator_cpid", nullable = false, length = 128)
    private String creatorCpid;

    @Column(name = "creator_type", nullable = false, length = 24)
    private String creatorType = "CLIENT";

    @Column(name = "provider_id", length = 128)
    private String providerId;

    @Column(name = "programme_id")
    private UUID programmeId;

    @Column(name = "media_asset_id", nullable = false)
    private UUID mediaAssetId;

    @Column(name = "caption", length = 500)
    private String caption;

    @Column(name = "fundo_content_ref", length = 128)
    private String fundoContentRef;

    @Column(name = "verified_badge", length = 24)
    private String verifiedBadge;

    @Column(name = "visibility", nullable = false, length = 32)
    private String visibility = "PUBLIC_WITHIN_IMPILO";

    @Column(name = "approval_status", nullable = false, length = 16)
    private String approvalStatus = "APPROVED";

    @Column(name = "moderation_status", nullable = false, length = 16)
    private String moderationStatus = "VISIBLE";

    @Column(name = "view_count", nullable = false)
    private long viewCount = 0;

    @Column(name = "reaction_count", nullable = false)
    private int reactionCount = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    void onCreate() {
        if (reelId == null) {
            reelId = UUID.randomUUID();
        }
        OffsetDateTime now = OffsetDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    public Long getId() { return id; }
    public UUID getReelId() { return reelId; }
    public void setReelId(UUID reelId) { this.reelId = reelId; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public String getCreatorCpid() { return creatorCpid; }
    public void setCreatorCpid(String creatorCpid) { this.creatorCpid = creatorCpid; }
    public String getCreatorType() { return creatorType; }
    public void setCreatorType(String creatorType) { this.creatorType = creatorType; }
    public String getProviderId() { return providerId; }
    public void setProviderId(String providerId) { this.providerId = providerId; }
    public UUID getProgrammeId() { return programmeId; }
    public void setProgrammeId(UUID programmeId) { this.programmeId = programmeId; }
    public UUID getMediaAssetId() { return mediaAssetId; }
    public void setMediaAssetId(UUID mediaAssetId) { this.mediaAssetId = mediaAssetId; }
    public String getCaption() { return caption; }
    public void setCaption(String caption) { this.caption = caption; }
    public String getFundoContentRef() { return fundoContentRef; }
    public void setFundoContentRef(String fundoContentRef) { this.fundoContentRef = fundoContentRef; }
    public String getVerifiedBadge() { return verifiedBadge; }
    public void setVerifiedBadge(String verifiedBadge) { this.verifiedBadge = verifiedBadge; }
    public String getVisibility() { return visibility; }
    public void setVisibility(String visibility) { this.visibility = visibility; }
    public String getApprovalStatus() { return approvalStatus; }
    public void setApprovalStatus(String approvalStatus) { this.approvalStatus = approvalStatus; }
    public String getModerationStatus() { return moderationStatus; }
    public void setModerationStatus(String moderationStatus) { this.moderationStatus = moderationStatus; }
    public long getViewCount() { return viewCount; }
    public void setViewCount(long viewCount) { this.viewCount = viewCount; }
    public int getReactionCount() { return reactionCount; }
    public void setReactionCount(int reactionCount) { this.reactionCount = reactionCount; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
