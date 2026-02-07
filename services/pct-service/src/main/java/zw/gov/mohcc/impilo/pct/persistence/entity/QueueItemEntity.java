package zw.gov.mohcc.impilo.pct.persistence.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;
import zw.gov.mohcc.impilo.pct.domain.QueueItemStatus;

/**
 * Represents a single item in a patient queue.
 * Tracks the patient's position, priority, token number, and service lifecycle timestamps.
 */
@Entity
@Table(name = "pct_queue_items")
public class QueueItemEntity {

    @Id
    @Column(name = "item_id", nullable = false)
    private UUID itemId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "facility_id")
    private UUID facilityId;

    @Column(name = "queue_id", nullable = false)
    private UUID queueId;

    @Column(name = "journey_id", nullable = false)
    private String journeyId;

    @Column(name = "patient_cpid", nullable = false)
    private String patientCpid;

    @Column(name = "priority", nullable = false)
    private int priority = 0;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private QueueItemStatus status = QueueItemStatus.WAITING;

    @Column(name = "token_number")
    private Integer tokenNumber;

    @Column(name = "called_by")
    private String calledBy;

    @Column(name = "called_at")
    private OffsetDateTime calledAt;

    @Column(name = "service_started_at")
    private OffsetDateTime serviceStartedAt;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
    }

    // Getters and setters

    public UUID getId() { return itemId; }
    public void setId(UUID id) { this.itemId = id; }

    public UUID getItemId() { return itemId; }
    public void setItemId(UUID itemId) { this.itemId = itemId; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public UUID getFacilityId() { return facilityId; }
    public void setFacilityId(UUID facilityId) { this.facilityId = facilityId; }

    public UUID getQueueId() { return queueId; }
    public void setQueueId(UUID queueId) { this.queueId = queueId; }

    public String getJourneyId() { return journeyId; }
    public void setJourneyId(String journeyId) { this.journeyId = journeyId; }

    public String getPatientCpid() { return patientCpid; }
    public void setPatientCpid(String patientCpid) { this.patientCpid = patientCpid; }

    public int getPriority() { return priority; }
    public void setPriority(int priority) { this.priority = priority; }

    public QueueItemStatus getStatus() { return status; }
    public void setStatus(QueueItemStatus status) { this.status = status; }

    public Integer getTokenNumber() { return tokenNumber; }
    public void setTokenNumber(Integer tokenNumber) { this.tokenNumber = tokenNumber; }

    public String getCalledBy() { return calledBy; }
    public void setCalledBy(String calledBy) { this.calledBy = calledBy; }

    public OffsetDateTime getCalledAt() { return calledAt; }
    public void setCalledAt(OffsetDateTime calledAt) { this.calledAt = calledAt; }

    public OffsetDateTime getServiceStartedAt() { return serviceStartedAt; }
    public void setServiceStartedAt(OffsetDateTime serviceStartedAt) { this.serviceStartedAt = serviceStartedAt; }

    public OffsetDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(OffsetDateTime completedAt) { this.completedAt = completedAt; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

}
