package zw.gov.mohcc.impilo.simba.persistence.entity;

import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "coaching_nudges", schema = "simba")
public class CoachingNudgeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "nudge_id", nullable = false, unique = true)
    private UUID nudgeId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "person_cpid", length = 128)
    private String personCpid;

    @Column(name = "nudge_type", nullable = false, length = 32)
    private String nudgeType;

    @Column(name = "message", nullable = false, columnDefinition = "TEXT")
    private String message;

    @Column(name = "channel", nullable = false, length = 16)
    private String channel = "IN_APP";

    @Column(name = "status", nullable = false, length = 16)
    private String status = "ACTIVE";

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (nudgeId == null) {
            nudgeId = UUID.randomUUID();
        }
        createdAt = OffsetDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public UUID getNudgeId() {
        return nudgeId;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public String getPersonCpid() {
        return personCpid;
    }

    public String getNudgeType() {
        return nudgeType;
    }

    public String getMessage() {
        return message;
    }

    public String getChannel() {
        return channel;
    }

    public String getStatus() {
        return status;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
