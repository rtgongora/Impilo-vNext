package zw.gov.mohcc.impilo.tuso.persistence.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "config_audit", schema = "tuso")
public class ConfigAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "entity_type", nullable = false, length = 30)
    private String entityType;

    @Column(name = "entity_id", nullable = false, length = 255)
    private String entityId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "action", nullable = false, length = 20)
    private String action;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "old_config", columnDefinition = "jsonb")
    private Map<String, Object> oldConfig;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "new_config", columnDefinition = "jsonb")
    private Map<String, Object> newConfig;

    @Column(name = "actor_id", nullable = false, length = 255)
    private String actorId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }

    // Getters and setters

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getEntityType() { return entityType; }
    public void setEntityType(String entityType) { this.entityType = entityType; }

    public String getEntityId() { return entityId; }
    public void setEntityId(String entityId) { this.entityId = entityId; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public Map<String, Object> getOldConfig() { return oldConfig; }
    public void setOldConfig(Map<String, Object> oldConfig) { this.oldConfig = oldConfig; }

    public Map<String, Object> getNewConfig() { return newConfig; }
    public void setNewConfig(Map<String, Object> newConfig) { this.newConfig = newConfig; }

    public String getActorId() { return actorId; }
    public void setActorId(String actorId) { this.actorId = actorId; }

    public Instant getCreatedAt() { return createdAt; }
}
