package zw.gov.mohcc.impilo.rito.persistence.entity;

import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "rit_sla_policy", schema = "rito")
public class SlaPolicyEntity {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "policy_key", nullable = false, length = 128)
    private String policyKey;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "applies_to_case_type", length = 64)
    private String appliesToCaseType;

    @Column(name = "applies_to_severity", length = 32)
    private String appliesToSeverity;

    @Column(name = "acknowledge_within_minutes")
    private Integer acknowledgeWithinMinutes;

    @Column(name = "resolve_within_minutes")
    private Integer resolveWithinMinutes;

    @Column(name = "escalate_after_minutes")
    private Integer escalateAfterMinutes;

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
    public String getPolicyKey() { return policyKey; }
    public void setPolicyKey(String policyKey) { this.policyKey = policyKey; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getAppliesToCaseType() { return appliesToCaseType; }
    public void setAppliesToCaseType(String appliesToCaseType) { this.appliesToCaseType = appliesToCaseType; }
    public String getAppliesToSeverity() { return appliesToSeverity; }
    public void setAppliesToSeverity(String appliesToSeverity) { this.appliesToSeverity = appliesToSeverity; }
    public Integer getAcknowledgeWithinMinutes() { return acknowledgeWithinMinutes; }
    public void setAcknowledgeWithinMinutes(Integer acknowledgeWithinMinutes) { this.acknowledgeWithinMinutes = acknowledgeWithinMinutes; }
    public Integer getResolveWithinMinutes() { return resolveWithinMinutes; }
    public void setResolveWithinMinutes(Integer resolveWithinMinutes) { this.resolveWithinMinutes = resolveWithinMinutes; }
    public Integer getEscalateAfterMinutes() { return escalateAfterMinutes; }
    public void setEscalateAfterMinutes(Integer escalateAfterMinutes) { this.escalateAfterMinutes = escalateAfterMinutes; }
    public Boolean getActive() { return active; }
    public void setActive(Boolean active) { this.active = active; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
