package zw.gov.mohcc.impilo.simba.social.entity;

import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.UUID;

/** A saved post/reel bookmark. */
@Entity
@Table(name = "simba_social_bookmark", schema = "simba")
public class SocialBookmarkEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "bookmark_id", nullable = false, unique = true)
    private UUID bookmarkId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "actor_cpid", nullable = false, length = 128)
    private String actorCpid;

    @Column(name = "subject_type", nullable = false, length = 16)
    private String subjectType;

    @Column(name = "subject_id", nullable = false)
    private UUID subjectId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (bookmarkId == null) {
            bookmarkId = UUID.randomUUID();
        }
        createdAt = OffsetDateTime.now();
    }

    public Long getId() { return id; }
    public UUID getBookmarkId() { return bookmarkId; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public String getActorCpid() { return actorCpid; }
    public void setActorCpid(String actorCpid) { this.actorCpid = actorCpid; }
    public String getSubjectType() { return subjectType; }
    public void setSubjectType(String subjectType) { this.subjectType = subjectType; }
    public UUID getSubjectId() { return subjectId; }
    public void setSubjectId(UUID subjectId) { this.subjectId = subjectId; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
