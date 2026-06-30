package zw.gov.mohcc.impilo.guidance.persistence.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * A person's dismissal of a guidance item (display state only — no SoR truth). Once dismissed, the
 * card is not re-shown to that person on subsequent context resolutions.
 */
@Entity
@Table(name = "guidance_dismissal", schema = "guidance")
public class GuidanceDismissalEntity {

    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false) private String tenantId;
    @Column(name = "actor_id", nullable = false) private String actorId;
    @Column(name = "guidance_key", nullable = false, length = 128) private String guidanceKey;
    @Column(name = "dismissed_at", nullable = false) private OffsetDateTime dismissedAt;

    @PrePersist
    void onCreate() {
        if (dismissedAt == null) dismissedAt = OffsetDateTime.now();
    }

    public UUID getId() { return id; }
    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
    public String getActorId() { return actorId; }
    public void setActorId(String actorId) { this.actorId = actorId; }
    public String getGuidanceKey() { return guidanceKey; }
    public void setGuidanceKey(String guidanceKey) { this.guidanceKey = guidanceKey; }
    public OffsetDateTime getDismissedAt() { return dismissedAt; }
}
