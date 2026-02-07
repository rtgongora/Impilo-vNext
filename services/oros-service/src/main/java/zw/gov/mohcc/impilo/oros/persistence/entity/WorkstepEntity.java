package zw.gov.mohcc.impilo.oros.persistence.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;
import zw.gov.mohcc.impilo.oros.domain.WorkstepType;
import zw.gov.mohcc.impilo.oros.domain.WorkstepStatus;

/**
 * Represents an individual workstep within an order's fulfillment workflow.
 * Each order has a sequence of worksteps that track granular progress
 * (e.g. specimen collection, analysis, reporting for a LAB order).
 */
@Entity
@Table(name = "oros_worksteps")
public class WorkstepEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "workstep_id", nullable = false)
    private UUID workstepId;

    @Column(name = "order_id", nullable = false, length = 26)
    private String orderId;

    @Enumerated(EnumType.STRING)
    @Column(name = "step_type", nullable = false, length = 30)
    private WorkstepType stepType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private WorkstepStatus status = WorkstepStatus.PENDING;

    @Column(name = "assigned_to", length = 128)
    private String assignedTo;

    @Column(name = "started_at")
    private OffsetDateTime startedAt;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
    }

    public UUID getWorkstepId() { return workstepId; }
    public void setWorkstepId(UUID workstepId) { this.workstepId = workstepId; }

    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }

    public WorkstepType getStepType() { return stepType; }
    public void setStepType(WorkstepType stepType) { this.stepType = stepType; }

    public WorkstepStatus getStatus() { return status; }
    public void setStatus(WorkstepStatus status) { this.status = status; }

    public String getAssignedTo() { return assignedTo; }
    public void setAssignedTo(String assignedTo) { this.assignedTo = assignedTo; }

    public OffsetDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(OffsetDateTime startedAt) { this.startedAt = startedAt; }

    public OffsetDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(OffsetDateTime completedAt) { this.completedAt = completedAt; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
}
