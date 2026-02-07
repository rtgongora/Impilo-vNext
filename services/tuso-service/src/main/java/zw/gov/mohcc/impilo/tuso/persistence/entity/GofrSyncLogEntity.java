package zw.gov.mohcc.impilo.tuso.persistence.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "gofr_sync_log", schema = "tuso")
public class GofrSyncLogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "sync_type", nullable = false, length = 30)
    private String syncType;

    @Column(name = "direction", nullable = false, length = 10)
    private String direction;

    @Column(name = "facilities_processed", nullable = false)
    private Integer facilitiesProcessed = 0;

    @Column(name = "facilities_created", nullable = false)
    private Integer facilitiesCreated = 0;

    @Column(name = "facilities_updated", nullable = false)
    private Integer facilitiesUpdated = 0;

    @Column(name = "errors", nullable = false)
    private Integer errors = 0;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "error_details", columnDefinition = "jsonb")
    private Map<String, Object> errorDetails;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "status", nullable = false, length = 20)
    private String status = "RUNNING";

    @PrePersist
    protected void onCreate() {
        if (startedAt == null) {
            startedAt = Instant.now();
        }
    }

    // Getters and setters

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public String getSyncType() { return syncType; }
    public void setSyncType(String syncType) { this.syncType = syncType; }

    public String getDirection() { return direction; }
    public void setDirection(String direction) { this.direction = direction; }

    public Integer getFacilitiesProcessed() { return facilitiesProcessed; }
    public void setFacilitiesProcessed(Integer facilitiesProcessed) { this.facilitiesProcessed = facilitiesProcessed; }

    public Integer getFacilitiesCreated() { return facilitiesCreated; }
    public void setFacilitiesCreated(Integer facilitiesCreated) { this.facilitiesCreated = facilitiesCreated; }

    public Integer getFacilitiesUpdated() { return facilitiesUpdated; }
    public void setFacilitiesUpdated(Integer facilitiesUpdated) { this.facilitiesUpdated = facilitiesUpdated; }

    public Integer getErrors() { return errors; }
    public void setErrors(Integer errors) { this.errors = errors; }

    public Map<String, Object> getErrorDetails() { return errorDetails; }
    public void setErrorDetails(Map<String, Object> errorDetails) { this.errorDetails = errorDetails; }

    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }

    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
