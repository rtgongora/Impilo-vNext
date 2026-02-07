package zw.gov.mohcc.impilo.tshepo.authz.persistence.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * ABAC/RBAC policy rule definition.
 *
 * <p>Rules define what actor types/roles may perform what actions on what
 * resource types, for what purpose, with optional facility/workspace scope
 * constraints and JSONB condition predicates.</p>
 *
 * <p>Effect is either ALLOW or DENY. DENY rules are evaluated first
 * (by priority). A first-matching DENY wins. If no ALLOW rule matches,
 * the default decision is DENY.</p>
 */
@Entity
@Table(name = "policy_rule", schema = "tshepo_authz")
public class PolicyRuleEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "actor_type", length = 32)
    private String actorType;

    @Column(length = 64)
    private String role;

    @Column(name = "resource_type", length = 64)
    private String resourceType;

    @Column(length = 32)
    private String action;

    @Column(length = 32)
    private String purpose;

    @Column(name = "facility_scope", nullable = false)
    private boolean facilityScope = false;

    @Column(name = "workspace_scope", nullable = false)
    private boolean workspaceScope = false;

    @Column(length = 8, nullable = false)
    private String effect = "ALLOW";

    @Column(nullable = false)
    private int priority = 100;

    @Column(columnDefinition = "jsonb")
    private String conditions;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }

    // ── Getters and Setters ────────────────────────────────────────────

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getActorType() { return actorType; }
    public void setActorType(String actorType) { this.actorType = actorType; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public String getResourceType() { return resourceType; }
    public void setResourceType(String resourceType) { this.resourceType = resourceType; }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public String getPurpose() { return purpose; }
    public void setPurpose(String purpose) { this.purpose = purpose; }

    public boolean isFacilityScope() { return facilityScope; }
    public void setFacilityScope(boolean facilityScope) { this.facilityScope = facilityScope; }

    public boolean isWorkspaceScope() { return workspaceScope; }
    public void setWorkspaceScope(boolean workspaceScope) { this.workspaceScope = workspaceScope; }

    public String getEffect() { return effect; }
    public void setEffect(String effect) { this.effect = effect; }

    public int getPriority() { return priority; }
    public void setPriority(int priority) { this.priority = priority; }

    public String getConditions() { return conditions; }
    public void setConditions(String conditions) { this.conditions = conditions; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
