package zw.gov.mohcc.impilo.governance.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "wgv_assignment_status_history")
public class AssignmentStatusHistoryEntity {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "assignment_id", nullable = false)
    private UUID assignmentId;

    @Column(name = "previous_status", length = 32)
    private String previousStatus;

    @Column(name = "new_status", nullable = false, length = 32)
    private String newStatus;

    @Column(name = "changed_by", nullable = false, length = 255)
    private String changedBy;

    @Column(name = "changed_at", nullable = false)
    private Instant changedAt;

    @Column(name = "reason", columnDefinition = "TEXT")
    private String reason;

    @Column(name = "authority_context", columnDefinition = "TEXT")
    private String authorityContext;

    protected AssignmentStatusHistoryEntity() {}

    public AssignmentStatusHistoryEntity(UUID id, UUID tenantId, UUID assignmentId, String previousStatus,
                                         String newStatus, String changedBy, String reason) {
        this.id = id;
        this.tenantId = tenantId;
        this.assignmentId = assignmentId;
        this.previousStatus = previousStatus;
        this.newStatus = newStatus;
        this.changedBy = changedBy;
        this.changedAt = Instant.now();
        this.reason = reason;
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getAssignmentId() { return assignmentId; }
    public String getPreviousStatus() { return previousStatus; }
    public String getNewStatus() { return newStatus; }
    public String getChangedBy() { return changedBy; }
    public Instant getChangedAt() { return changedAt; }
    public String getReason() { return reason; }
    public String getAuthorityContext() { return authorityContext; }
}
