package zw.gov.mohcc.impilo.support.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "sup_ticket_assignments")
public class AssignmentEntity {
    @Id @Column(name = "assignment_id") private UUID assignmentId;
    @Column(name = "ticket_id", nullable = false) private UUID ticketId;
    @Column(name = "tenant_id", nullable = false) private UUID tenantId;
    @Column(name = "assignee_ref", nullable = false) private String assigneeRef;
    @Column(name = "assigned_by", nullable = false) private String assignedBy;
    @Column(name = "assigned_at", nullable = false) private OffsetDateTime assignedAt;
    @Column(name = "unassigned_at") private OffsetDateTime unassignedAt;

    protected AssignmentEntity() {}
    public AssignmentEntity(UUID assignmentId, UUID ticketId, UUID tenantId, String assigneeRef, String assignedBy) {
        this.assignmentId = assignmentId; this.ticketId = ticketId; this.tenantId = tenantId;
        this.assigneeRef = assigneeRef; this.assignedBy = assignedBy; this.assignedAt = OffsetDateTime.now();
    }

    public UUID getAssignmentId() { return assignmentId; }
    public UUID getTicketId() { return ticketId; }
    public UUID getTenantId() { return tenantId; }
    public String getAssigneeRef() { return assigneeRef; }
    public String getAssignedBy() { return assignedBy; }
    public OffsetDateTime getAssignedAt() { return assignedAt; }
    public OffsetDateTime getUnassignedAt() { return unassignedAt; }
    public void setUnassignedAt(OffsetDateTime v) { this.unassignedAt = v; }
}
