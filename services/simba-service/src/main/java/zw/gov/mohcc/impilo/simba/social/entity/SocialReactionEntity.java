package zw.gov.mohcc.impilo.simba.social.entity;

import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.UUID;

/** A reaction (ENCOURAGE/LIKE/CELEBRATE/HELPFUL/SUPPORT/THANK_YOU) on a post/status/comment/reel. */
@Entity
@Table(name = "simba_social_reaction", schema = "simba")
public class SocialReactionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "reaction_id", nullable = false, unique = true)
    private UUID reactionId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "subject_type", nullable = false, length = 16)
    private String subjectType;

    @Column(name = "subject_id", nullable = false)
    private UUID subjectId;

    @Column(name = "actor_cpid", nullable = false, length = 128)
    private String actorCpid;

    @Column(name = "reaction", nullable = false, length = 16)
    private String reaction;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (reactionId == null) {
            reactionId = UUID.randomUUID();
        }
        createdAt = OffsetDateTime.now();
    }

    public Long getId() { return id; }
    public UUID getReactionId() { return reactionId; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public String getSubjectType() { return subjectType; }
    public void setSubjectType(String subjectType) { this.subjectType = subjectType; }
    public UUID getSubjectId() { return subjectId; }
    public void setSubjectId(UUID subjectId) { this.subjectId = subjectId; }
    public String getActorCpid() { return actorCpid; }
    public void setActorCpid(String actorCpid) { this.actorCpid = actorCpid; }
    public String getReaction() { return reaction; }
    public void setReaction(String reaction) { this.reaction = reaction; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
