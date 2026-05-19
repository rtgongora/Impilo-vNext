package zw.gov.mohcc.impilo.community.social.persistence.entity;

import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "social_community_memberships", schema = "community")
public class SocialCommunityMembershipEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "community_id", nullable = false)
    private UUID communityId;

    @Column(name = "actor_id", nullable = false, length = 128)
    private String actorId;

    @Column(name = "role", nullable = false, length = 16)
    private String role = "member";

    @Column(name = "status", nullable = false, length = 16)
    private String status = "active";

    @Column(name = "joined_at", nullable = false)
    private OffsetDateTime joinedAt;

    @PrePersist
    void onCreate() {
        if (joinedAt == null) joinedAt = OffsetDateTime.now();
    }

    public Long getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public UUID getCommunityId() { return communityId; }
    public void setCommunityId(UUID communityId) { this.communityId = communityId; }
    public String getActorId() { return actorId; }
    public void setActorId(String actorId) { this.actorId = actorId; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public OffsetDateTime getJoinedAt() { return joinedAt; }
    public void setJoinedAt(OffsetDateTime joinedAt) { this.joinedAt = joinedAt; }
}
