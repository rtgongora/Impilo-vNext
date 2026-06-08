package zw.gov.mohcc.impilo.madi.persistence.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "donation_drive_sync_conflicts", schema = "madi")
public class DonationDriveSyncConflictEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "conflict_id", nullable = false, unique = true)
    private UUID conflictId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "drive_id", nullable = false)
    private UUID driveId;

    @Column(name = "local_op_id", nullable = false)
    private String localOpId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "server_state", columnDefinition = "jsonb")
    private Map<String, Object> serverState;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "local_state", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> localState;

    @Column(name = "resolution")
    private String resolution;

    @Column(name = "resolved_by")
    private String resolvedBy;

    @Column(name = "resolved_at")
    private OffsetDateTime resolvedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (conflictId == null) {
            conflictId = UUID.randomUUID();
        }
        createdAt = OffsetDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public UUID getConflictId() { return conflictId; }
    public void setConflictId(UUID conflictId) { this.conflictId = conflictId; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public UUID getDriveId() { return driveId; }
    public void setDriveId(UUID driveId) { this.driveId = driveId; }

    public String getLocalOpId() { return localOpId; }
    public void setLocalOpId(String localOpId) { this.localOpId = localOpId; }

    public Map<String, Object> getServerState() { return serverState; }
    public void setServerState(Map<String, Object> serverState) { this.serverState = serverState; }

    public Map<String, Object> getLocalState() { return localState; }
    public void setLocalState(Map<String, Object> localState) { this.localState = localState; }

    public String getResolution() { return resolution; }
    public void setResolution(String resolution) { this.resolution = resolution; }

    public String getResolvedBy() { return resolvedBy; }
    public void setResolvedBy(String resolvedBy) { this.resolvedBy = resolvedBy; }

    public OffsetDateTime getResolvedAt() { return resolvedAt; }
    public void setResolvedAt(OffsetDateTime resolvedAt) { this.resolvedAt = resolvedAt; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
