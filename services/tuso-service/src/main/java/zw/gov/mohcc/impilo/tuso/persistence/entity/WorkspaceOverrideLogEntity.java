package zw.gov.mohcc.impilo.tuso.persistence.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "workspace_override_log", schema = "tuso")
public class WorkspaceOverrideLogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workspace_id", nullable = false)
    private WorkspaceEntity workspace;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "actor_id", nullable = false, length = 255)
    private String actorId;

    @Column(name = "override_type", nullable = false, length = 50)
    private String overrideType;

    @Column(name = "reason", nullable = false, columnDefinition = "TEXT")
    private String reason;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "old_value", columnDefinition = "jsonb")
    private Map<String, Object> oldValue;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "new_value", columnDefinition = "jsonb")
    private Map<String, Object> newValue;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }

    // Getters and setters

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public WorkspaceEntity getWorkspace() { return workspace; }
    public void setWorkspace(WorkspaceEntity workspace) { this.workspace = workspace; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public String getActorId() { return actorId; }
    public void setActorId(String actorId) { this.actorId = actorId; }

    public String getOverrideType() { return overrideType; }
    public void setOverrideType(String overrideType) { this.overrideType = overrideType; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public Map<String, Object> getOldValue() { return oldValue; }
    public void setOldValue(Map<String, Object> oldValue) { this.oldValue = oldValue; }

    public Map<String, Object> getNewValue() { return newValue; }
    public void setNewValue(Map<String, Object> newValue) { this.newValue = newValue; }

    public Instant getCreatedAt() { return createdAt; }
}
