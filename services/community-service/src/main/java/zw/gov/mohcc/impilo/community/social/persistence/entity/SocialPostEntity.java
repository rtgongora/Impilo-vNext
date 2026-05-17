package zw.gov.mohcc.impilo.community.social.persistence.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "social_posts", schema = "community")
public class SocialPostEntity {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "author_actor_id", nullable = false, length = 128)
    private String authorActorId;

    @Column(name = "author_display", length = 200)
    private String authorDisplay;

    @Column(name = "author_role_badge", length = 64)
    private String authorRoleBadge;

    @Column(name = "author_facility", length = 200)
    private String authorFacility;

    @Column(name = "kind", nullable = false, length = 32)
    private String kind = "text";

    @Column(name = "status", nullable = false, length = 16)
    private String status = "published";

    @Column(name = "visibility", nullable = false, length = 32)
    private String visibility = "public";

    @Column(name = "title", length = 400)
    private String title;

    @Column(name = "body", nullable = false, columnDefinition = "TEXT")
    private String body;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "topics_json", nullable = false, columnDefinition = "jsonb")
    private String topicsJson = "[]";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "attachments_json", nullable = false, columnDefinition = "jsonb")
    private String attachmentsJson = "[]";

    @Column(name = "community_id")
    private UUID communityId;

    @Column(name = "group_id")
    private UUID groupId;

    @Column(name = "page_id")
    private UUID pageId;

    @Column(name = "facility_id", length = 128)
    private String facilityId;

    @Column(name = "pinned", nullable = false)
    private boolean pinned = false;

    @Column(name = "source", nullable = false, length = 32)
    private String source = "manual";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "nompilo_flags_json", nullable = false, columnDefinition = "jsonb")
    private String nompiloFlagsJson = "{}";

    @Column(name = "reaction_count", nullable = false)
    private int reactionCount = 0;

    @Column(name = "comment_count", nullable = false)
    private int commentCount = 0;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "reaction_summary_json", nullable = false, columnDefinition = "jsonb")
    private String reactionSummaryJson = "{}";

    @Column(name = "scheduled_for")
    private OffsetDateTime scheduledFor;

    @Column(name = "published_at")
    private OffsetDateTime publishedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
        if ("published".equals(status) && publishedAt == null) publishedAt = now;
    }

    @PreUpdate
    void onUpdate() { updatedAt = OffsetDateTime.now(); }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public String getAuthorActorId() { return authorActorId; }
    public void setAuthorActorId(String authorActorId) { this.authorActorId = authorActorId; }
    public String getAuthorDisplay() { return authorDisplay; }
    public void setAuthorDisplay(String authorDisplay) { this.authorDisplay = authorDisplay; }
    public String getAuthorRoleBadge() { return authorRoleBadge; }
    public void setAuthorRoleBadge(String authorRoleBadge) { this.authorRoleBadge = authorRoleBadge; }
    public String getAuthorFacility() { return authorFacility; }
    public void setAuthorFacility(String authorFacility) { this.authorFacility = authorFacility; }
    public String getKind() { return kind; }
    public void setKind(String kind) { this.kind = kind; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getVisibility() { return visibility; }
    public void setVisibility(String visibility) { this.visibility = visibility; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }
    public String getTopicsJson() { return topicsJson; }
    public void setTopicsJson(String topicsJson) { this.topicsJson = topicsJson; }
    public String getAttachmentsJson() { return attachmentsJson; }
    public void setAttachmentsJson(String attachmentsJson) { this.attachmentsJson = attachmentsJson; }
    public UUID getCommunityId() { return communityId; }
    public void setCommunityId(UUID communityId) { this.communityId = communityId; }
    public UUID getGroupId() { return groupId; }
    public void setGroupId(UUID groupId) { this.groupId = groupId; }
    public UUID getPageId() { return pageId; }
    public void setPageId(UUID pageId) { this.pageId = pageId; }
    public String getFacilityId() { return facilityId; }
    public void setFacilityId(String facilityId) { this.facilityId = facilityId; }
    public boolean isPinned() { return pinned; }
    public void setPinned(boolean pinned) { this.pinned = pinned; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public String getNompiloFlagsJson() { return nompiloFlagsJson; }
    public void setNompiloFlagsJson(String nompiloFlagsJson) { this.nompiloFlagsJson = nompiloFlagsJson; }
    public int getReactionCount() { return reactionCount; }
    public void setReactionCount(int reactionCount) { this.reactionCount = reactionCount; }
    public int getCommentCount() { return commentCount; }
    public void setCommentCount(int commentCount) { this.commentCount = commentCount; }
    public String getReactionSummaryJson() { return reactionSummaryJson; }
    public void setReactionSummaryJson(String reactionSummaryJson) { this.reactionSummaryJson = reactionSummaryJson; }
    public OffsetDateTime getScheduledFor() { return scheduledFor; }
    public void setScheduledFor(OffsetDateTime scheduledFor) { this.scheduledFor = scheduledFor; }
    public OffsetDateTime getPublishedAt() { return publishedAt; }
    public void setPublishedAt(OffsetDateTime publishedAt) { this.publishedAt = publishedAt; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
