package zw.gov.mohcc.impilo.rito.persistence.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "rit_escalation_rule", schema = "rito")
public class EscalationRuleEntity {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "rule_key", nullable = false, length = 128)
    private String ruleKey;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "trigger_type", nullable = false, length = 48)
    private String triggerType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "condition_json", nullable = false, columnDefinition = "jsonb")
    private String conditionJson;

    @Column(name = "escalate_to_role", length = 128)
    private String escalateToRole;

    @Column(name = "escalate_to_level", length = 32)
    private String escalateToLevel;

    @Column(name = "notify_template_key", length = 128)
    private String notifyTemplateKey;

    @Column(name = "active", nullable = false)
    private Boolean active;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = createdAt;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public String getRuleKey() { return ruleKey; }
    public void setRuleKey(String ruleKey) { this.ruleKey = ruleKey; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getTriggerType() { return triggerType; }
    public void setTriggerType(String triggerType) { this.triggerType = triggerType; }
    public String getConditionJson() { return conditionJson; }
    public void setConditionJson(String conditionJson) { this.conditionJson = conditionJson; }
    public String getEscalateToRole() { return escalateToRole; }
    public void setEscalateToRole(String escalateToRole) { this.escalateToRole = escalateToRole; }
    public String getEscalateToLevel() { return escalateToLevel; }
    public void setEscalateToLevel(String escalateToLevel) { this.escalateToLevel = escalateToLevel; }
    public String getNotifyTemplateKey() { return notifyTemplateKey; }
    public void setNotifyTemplateKey(String notifyTemplateKey) { this.notifyTemplateKey = notifyTemplateKey; }
    public Boolean getActive() { return active; }
    public void setActive(Boolean active) { this.active = active; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
