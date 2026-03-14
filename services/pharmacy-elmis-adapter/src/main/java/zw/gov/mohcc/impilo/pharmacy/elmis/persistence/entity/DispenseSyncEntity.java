package zw.gov.mohcc.impilo.pharmacy.elmis.persistence.entity;

import jakarta.persistence.*;
import zw.gov.mohcc.impilo.pharmacy.elmis.domain.SyncStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Tracks dispense synchronization runs between pharmacy and eLMIS.
 */
@Entity
@Table(name = "dispense_sync", schema = "pharmacy_elmis")
public class DispenseSyncEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "facility_id", nullable = false)
    private UUID facilityId;

    @Column(name = "sync_type", nullable = false, length = 50)
    private String syncType;

    @Column(name = "last_sync_at")
    private OffsetDateTime lastSyncAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20)
    private SyncStatus status = SyncStatus.IDLE;

    @Column(name = "records_synced")
    private Integer recordsSynced = 0;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
        updatedAt = OffsetDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    // ── Getters / Setters ───────────────────────────────────────────

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public UUID getFacilityId() { return facilityId; }
    public void setFacilityId(UUID facilityId) { this.facilityId = facilityId; }

    public String getSyncType() { return syncType; }
    public void setSyncType(String syncType) { this.syncType = syncType; }

    public OffsetDateTime getLastSyncAt() { return lastSyncAt; }
    public void setLastSyncAt(OffsetDateTime lastSyncAt) { this.lastSyncAt = lastSyncAt; }

    public SyncStatus getStatus() { return status; }
    public void setStatus(SyncStatus status) { this.status = status; }

    public Integer getRecordsSynced() { return recordsSynced; }
    public void setRecordsSynced(Integer recordsSynced) { this.recordsSynced = recordsSynced; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
