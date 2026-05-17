package zw.gov.mohcc.impilo.community.social.persistence.entity;

import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "social_page_followers", schema = "community")
public class SocialPageFollowerEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "page_id", nullable = false)
    private UUID pageId;

    @Column(name = "actor_id", nullable = false, length = 128)
    private String actorId;

    @Column(name = "followed_at", nullable = false)
    private OffsetDateTime followedAt;

    @PrePersist
    void onCreate() {
        if (followedAt == null) followedAt = OffsetDateTime.now();
    }

    public Long getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public UUID getPageId() { return pageId; }
    public void setPageId(UUID pageId) { this.pageId = pageId; }
    public String getActorId() { return actorId; }
    public void setActorId(String actorId) { this.actorId = actorId; }
    public OffsetDateTime getFollowedAt() { return followedAt; }
    public void setFollowedAt(OffsetDateTime followedAt) { this.followedAt = followedAt; }
}
