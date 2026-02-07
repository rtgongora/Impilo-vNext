package zw.gov.mohcc.impilo.msika.persistence.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "msika_change_log")
public class ChangeLogEntity {

    @Id
    @Column(name = "id", length = 26)
    private String id;

    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(name = "actor_id", nullable = false, length = 128)
    private String actorId;

    @Column(name = "action", nullable = false, length = 30)
    private String action;

    @Column(name = "entity_type", nullable = false, length = 50)
    private String entityType;

    @Column(name = "entity_id", nullable = false, length = 26)
    private String entityId;

    @Column(name = "diff", columnDefinition = "jsonb")
    private String diff;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public String getActorId() { return actorId; }
    public void setActorId(String actorId) { this.actorId = actorId; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public String getEntityType() { return entityType; }
    public void setEntityType(String entityType) { this.entityType = entityType; }
    public String getEntityId() { return entityId; }
    public void setEntityId(String entityId) { this.entityId = entityId; }
    public String getDiff() { return diff; }
    public void setDiff(String diff) { this.diff = diff; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
