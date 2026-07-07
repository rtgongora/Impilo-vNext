package zw.gov.mohcc.impilo.simba.social.entity;

import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.UUID;

/** A comment on a social post. */
@Entity
@Table(name = "simba_social_comment", schema = "simba")
public class SocialCommentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "comment_id", nullable = false, unique = true)
    private UUID commentId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "post_id", nullable = false)
    private UUID postId;

    @Column(name = "actor_cpid", nullable = false, length = 128)
    private String actorCpid;

    @Column(name = "actor_type", nullable = false, length = 24)
    private String actorType = "CLIENT";

    @Column(name = "body", nullable = false, columnDefinition = "TEXT")
    private String body;

    @Column(name = "moderation_status", nullable = false, length = 16)
    private String moderationStatus = "VISIBLE";

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (commentId == null) {
            commentId = UUID.randomUUID();
        }
        createdAt = OffsetDateTime.now();
    }

    public Long getId() { return id; }
    public UUID getCommentId() { return commentId; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public UUID getPostId() { return postId; }
    public void setPostId(UUID postId) { this.postId = postId; }
    public String getActorCpid() { return actorCpid; }
    public void setActorCpid(String actorCpid) { this.actorCpid = actorCpid; }
    public String getActorType() { return actorType; }
    public void setActorType(String actorType) { this.actorType = actorType; }
    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }
    public String getModerationStatus() { return moderationStatus; }
    public void setModerationStatus(String moderationStatus) { this.moderationStatus = moderationStatus; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
