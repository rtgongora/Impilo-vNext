package zw.gov.mohcc.impilo.experience.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "queue_entries")
public class QueueEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(name = "facility_id", nullable = false)
    private UUID facilityId;

    @Column(name = "workspace_id")
    private UUID workspaceId;

    @Column(name = "patient_id", nullable = false)
    private UUID patientId;

    @Column(name = "queue_type")
    private String queueType;

    private String priority;

    private String status;

    @Column(name = "triage_category")
    private String triageCategory;

    private String reason;

    private String notes;

    @Column(name = "assigned_to")
    private String assignedTo;

    @Column(name = "arrival_time")
    private OffsetDateTime arrivalTime;

    @Column(name = "called_at")
    private OffsetDateTime calledAt;

    @Column(name = "seen_at")
    private OffsetDateTime seenAt;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected QueueEntry() {}

    public void call() {
        this.calledAt = OffsetDateTime.now();
        this.status = "CALLED";
        this.updatedAt = OffsetDateTime.now();
    }

    public void startSeen() {
        this.seenAt = OffsetDateTime.now();
        this.status = "SEEN";
        this.updatedAt = OffsetDateTime.now();
    }

    public void complete() {
        this.completedAt = OffsetDateTime.now();
        this.status = "COMPLETED";
        this.updatedAt = OffsetDateTime.now();
    }

    public UUID getId() { return id; }
    public String getTenantId() { return tenantId; }
    public UUID getFacilityId() { return facilityId; }
    public UUID getWorkspaceId() { return workspaceId; }
    public UUID getPatientId() { return patientId; }
    public String getQueueType() { return queueType; }
    public String getPriority() { return priority; }
    public String getStatus() { return status; }
    public String getTriageCategory() { return triageCategory; }
    public String getReason() { return reason; }
    public String getNotes() { return notes; }
    public String getAssignedTo() { return assignedTo; }
    public OffsetDateTime getArrivalTime() { return arrivalTime; }
    public OffsetDateTime getCalledAt() { return calledAt; }
    public OffsetDateTime getSeenAt() { return seenAt; }
    public OffsetDateTime getCompletedAt() { return completedAt; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
