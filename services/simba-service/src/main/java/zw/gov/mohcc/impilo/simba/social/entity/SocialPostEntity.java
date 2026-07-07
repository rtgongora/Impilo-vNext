package zw.gov.mohcc.impilo.simba.social.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

/** A wellness-social post (sovereign layer, /wellness/social/**). Carries an actor block + visibility. */
@Entity
@Table(name = "simba_social_post", schema = "simba")
public class SocialPostEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "post_id", nullable = false, unique = true)
    private UUID postId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "actor_cpid", nullable = false, length = 128)
    private String actorCpid;

    @Column(name = "actor_type", nullable = false, length = 24)
    private String actorType = "CLIENT";

    @Column(name = "provider_id", length = 128)
    private String providerId;

    @Column(name = "programme_id")
    private UUID programmeId;

    @Column(name = "facility_id", length = 128)
    private String facilityId;

    @Column(name = "group_id")
    private UUID groupId;

    @Column(name = "community_id")
    private UUID communityId;

    @Column(name = "body", nullable = false, columnDefinition = "TEXT")
    private String body;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "media_object_ids", columnDefinition = "jsonb")
    private String mediaObjectIds;

    @Column(name = "sensitive_category", length = 24)
    private String sensitiveCategory;

    @Column(name = "milestone_ref", length = 128)
    private String milestoneRef;

    @Column(name = "visibility", nullable = false, length = 32)
    private String visibility = "PRIVATE";

    @Column(name = "moderation_status", nullable = false, length = 16)
    private String moderationStatus = "VISIBLE";

    @Column(name = "pinned", nullable = false)
    private boolean pinned = false;

    @Column(name = "official", nullable = false)
    private boolean official = false;

    @Column(name = "reaction_count", nullable = false)
    private int reactionCount = 0;

    @Column(name = "comment_count", nullable = false)
    private int commentCount = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    void onCreate() {
        if (postId == null) {
            postId = UUID.randomUUID();
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
    public UUID getPostId() { return postId; }
    public void setPostId(UUID postId) { this.postId = postId; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public String getActorCpid() { return actorCpid; }
    public void setActorCpid(String actorCpid) { this.actorCpid = actorCpid; }
    public String getActorType() { return actorType; }
    public void setActorType(String actorType) { this.actorType = actorType; }
    public String getProviderId() { return providerId; }
    public void setProviderId(String providerId) { this.providerId = providerId; }
    public UUID getProgrammeId() { return programmeId; }
    public void setProgrammeId(UUID programmeId) { this.programmeId = programmeId; }
    public String getFacilityId() { return facilityId; }
    public void setFacilityId(String facilityId) { this.facilityId = facilityId; }
    public UUID getGroupId() { return groupId; }
    public void setGroupId(UUID groupId) { this.groupId = groupId; }
    public UUID getCommunityId() { return communityId; }
    public void setCommunityId(UUID communityId) { this.communityId = communityId; }
    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }
    public String getMediaObjectIds() { return mediaObjectIds; }
    public void setMediaObjectIds(String mediaObjectIds) { this.mediaObjectIds = mediaObjectIds; }
    public String getSensitiveCategory() { return sensitiveCategory; }
    public void setSensitiveCategory(String sensitiveCategory) { this.sensitiveCategory = sensitiveCategory; }
    public String getMilestoneRef() { return milestoneRef; }
    public void setMilestoneRef(String milestoneRef) { this.milestoneRef = milestoneRef; }
    public String getVisibility() { return visibility; }
    public void setVisibility(String visibility) { this.visibility = visibility; }
    public String getModerationStatus() { return moderationStatus; }
    public void setModerationStatus(String moderationStatus) { this.moderationStatus = moderationStatus; }
    public boolean isPinned() { return pinned; }
    public void setPinned(boolean pinned) { this.pinned = pinned; }
    public boolean isOfficial() { return official; }
    public void setOfficial(boolean official) { this.official = official; }
    public int getReactionCount() { return reactionCount; }
    public void setReactionCount(int reactionCount) { this.reactionCount = reactionCount; }
    public int getCommentCount() { return commentCount; }
    public void setCommentCount(int commentCount) { this.commentCount = commentCount; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
