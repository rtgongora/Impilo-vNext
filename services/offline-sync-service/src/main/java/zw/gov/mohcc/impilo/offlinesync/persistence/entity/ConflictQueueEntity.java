package zw.gov.mohcc.impilo.offlinesync.persistence.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * JPA entity mapping to the {@code offline_sync.conflict_queue} table.
 */
@Entity
@Table(name = "conflict_queue", schema = "offline_sync")
public class ConflictQueueEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "sync_pack_id", nullable = false)
    private Long syncPackId;

    @Column(name = "conflict_type", nullable = false, length = 50)
    private String conflictType;

    @Column(name = "local_payload", nullable = false, columnDefinition = "JSONB")
    private String localPayload;

    @Column(name = "remote_payload", columnDefinition = "JSONB")
    private String remotePayload;

    @Column(name = "resolution", length = 20)
    private String resolution;

    @Column(name = "resolved_at")
    private OffsetDateTime resolvedAt;

    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }

    // Getters and setters

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public Long getSyncPackId() { return syncPackId; }
    public void setSyncPackId(Long syncPackId) { this.syncPackId = syncPackId; }

    public String getConflictType() { return conflictType; }
    public void setConflictType(String conflictType) { this.conflictType = conflictType; }

    public String getLocalPayload() { return localPayload; }
    public void setLocalPayload(String localPayload) { this.localPayload = localPayload; }

    public String getRemotePayload() { return remotePayload; }
    public void setRemotePayload(String remotePayload) { this.remotePayload = remotePayload; }

    public String getResolution() { return resolution; }
    public void setResolution(String resolution) { this.resolution = resolution; }

    public OffsetDateTime getResolvedAt() { return resolvedAt; }
    public void setResolvedAt(OffsetDateTime resolvedAt) { this.resolvedAt = resolvedAt; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
