package zw.gov.mohcc.impilo.zibo.persistence.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;
import zw.gov.mohcc.impilo.zibo.domain.PolicyMode;
import zw.gov.mohcc.impilo.zibo.domain.ScopeType;

/**
 * Represents the assignment of a terminology pack to a scope
 * (tenant, facility, or workspace) with an enforcement policy mode.
 */
@Entity
@Table(name = "zibo_assignments")
public class AssignmentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "assignment_id", nullable = false)
    private UUID assignmentId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Enumerated(EnumType.STRING)
    @Column(name = "scope_type", nullable = false)
    private ScopeType scopeType;

    @Column(name = "scope_id", nullable = false)
    private UUID scopeId;

    @Column(name = "pack_id", nullable = false)
    private UUID packId;

    @Column(name = "pack_version")
    private String packVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "policy_mode", nullable = false)
    private PolicyMode policyMode = PolicyMode.LENIENT;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "assigned_by")
    private String assignedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
    }

    // getId/setId aliases

    public UUID getId() { return assignmentId; }
    public void setId(UUID id) { this.assignmentId = id; }

    // Getters and setters

    public UUID getAssignmentId() { return assignmentId; }
    public void setAssignmentId(UUID assignmentId) { this.assignmentId = assignmentId; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public ScopeType getScopeType() { return scopeType; }
    public void setScopeType(ScopeType scopeType) { this.scopeType = scopeType; }

    public UUID getScopeId() { return scopeId; }
    public void setScopeId(UUID scopeId) { this.scopeId = scopeId; }

    public UUID getPackId() { return packId; }
    public void setPackId(UUID packId) { this.packId = packId; }

    public String getPackVersion() { return packVersion; }
    public void setPackVersion(String packVersion) { this.packVersion = packVersion; }

    public PolicyMode getPolicyMode() { return policyMode; }
    public void setPolicyMode(PolicyMode policyMode) { this.policyMode = policyMode; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public String getAssignedBy() { return assignedBy; }
    public void setAssignedBy(String assignedBy) { this.assignedBy = assignedBy; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
