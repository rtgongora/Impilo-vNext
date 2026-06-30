package zw.gov.mohcc.impilo.simba.persistence.entity;

import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Per-enrollment task progress, instantiated from a plan template task. */
@Entity
@Table(name = "enrollment_tasks", schema = "simba")
public class EnrollmentTaskEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "enrollment_task_id", nullable = false, unique = true)
    private UUID enrollmentTaskId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "enrollment_id", nullable = false)
    private UUID enrollmentId;

    @Column(name = "plan_task_id", nullable = false)
    private UUID planTaskId;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "status", nullable = false, length = 16)
    private String status = "PENDING";

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (enrollmentTaskId == null) {
            enrollmentTaskId = UUID.randomUUID();
        }
        createdAt = OffsetDateTime.now();
    }

    public Long getId() { return id; }
    public UUID getEnrollmentTaskId() { return enrollmentTaskId; }
    public void setEnrollmentTaskId(UUID enrollmentTaskId) { this.enrollmentTaskId = enrollmentTaskId; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public UUID getEnrollmentId() { return enrollmentId; }
    public void setEnrollmentId(UUID enrollmentId) { this.enrollmentId = enrollmentId; }
    public UUID getPlanTaskId() { return planTaskId; }
    public void setPlanTaskId(UUID planTaskId) { this.planTaskId = planTaskId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public OffsetDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(OffsetDateTime completedAt) { this.completedAt = completedAt; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
