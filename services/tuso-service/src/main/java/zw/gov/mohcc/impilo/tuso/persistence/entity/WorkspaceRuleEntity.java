package zw.gov.mohcc.impilo.tuso.persistence.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "workspace_rule", schema = "tuso")
public class WorkspaceRuleEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workspace_id", nullable = false)
    private WorkspaceEntity workspace;

    @Column(name = "rule_type", nullable = false, length = 30)
    private String ruleType;

    @Column(name = "actor_type", length = 50)
    private String actorType;

    @Column(name = "role", length = 100)
    private String role;

    @Column(name = "privilege", length = 100)
    private String privilege;

    @Column(name = "required", nullable = false)
    private Boolean required = true;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private Map<String, Object> metadata;

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

    public String getRuleType() { return ruleType; }
    public void setRuleType(String ruleType) { this.ruleType = ruleType; }

    public String getActorType() { return actorType; }
    public void setActorType(String actorType) { this.actorType = actorType; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public String getPrivilege() { return privilege; }
    public void setPrivilege(String privilege) { this.privilege = privilege; }

    public Boolean getRequired() { return required; }
    public void setRequired(Boolean required) { this.required = required; }

    public Map<String, Object> getMetadata() { return metadata; }
    public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }

    public Instant getCreatedAt() { return createdAt; }

    public boolean isRequired() { return required != null && required; }
}
