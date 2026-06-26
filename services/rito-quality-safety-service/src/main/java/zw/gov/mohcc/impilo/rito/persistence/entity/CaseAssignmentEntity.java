package zw.gov.mohcc.impilo.rito.persistence.entity;

import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "rit_case_assignment", schema = "rito")
public class CaseAssignmentEntity {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "case_id", nullable = false)
    private UUID caseId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "assignee_actor_id", nullable = false, length = 128)
    private String assigneeActorId;

    @Column(name = "assignee_actor_type", length = 48)
    private String assigneeActorType;

    @Column(name = "assignee_role", length = 64)
    private String assigneeRole;

    @Column(name = "assigned_by", length = 128)
    private String assignedBy;

    @Column(name = "reason", length = 512)
    private String reason;

    @Column(name = "active", nullable = false)
    private Boolean active;

    @Column(name = "assigned_at", nullable = false)
    private OffsetDateTime assignedAt;

    @Column(name = "unassigned_at")
    private OffsetDateTime unassignedAt;

    @PrePersist
    protected void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (assignedAt == null) {
            assignedAt = OffsetDateTime.now();
        }
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getCaseId() { return caseId; }
    public void setCaseId(UUID caseId) { this.caseId = caseId; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public String getAssigneeActorId() { return assigneeActorId; }
    public void setAssigneeActorId(String assigneeActorId) { this.assigneeActorId = assigneeActorId; }
    public String getAssigneeActorType() { return assigneeActorType; }
    public void setAssigneeActorType(String assigneeActorType) { this.assigneeActorType = assigneeActorType; }
    public String getAssigneeRole() { return assigneeRole; }
    public void setAssigneeRole(String assigneeRole) { this.assigneeRole = assigneeRole; }
    public String getAssignedBy() { return assignedBy; }
    public void setAssignedBy(String assignedBy) { this.assignedBy = assignedBy; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public Boolean getActive() { return active; }
    public void setActive(Boolean active) { this.active = active; }
    public OffsetDateTime getAssignedAt() { return assignedAt; }
    public void setAssignedAt(OffsetDateTime assignedAt) { this.assignedAt = assignedAt; }
    public OffsetDateTime getUnassignedAt() { return unassignedAt; }
    public void setUnassignedAt(OffsetDateTime unassignedAt) { this.unassignedAt = unassignedAt; }
}
