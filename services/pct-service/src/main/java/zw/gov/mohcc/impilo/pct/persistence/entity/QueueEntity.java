package zw.gov.mohcc.impilo.pct.persistence.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Represents a patient queue at a facility workspace.
 * Queues govern the order in which patients are seen, with configurable rules stored as JSONB.
 */
@Entity
@Table(name = "pct_queues")
public class QueueEntity {

    /** Queue provenance / materialisation vocabulary. */
    public static final String SOURCE_TUSO = "TUSO_MATERIALISED";
    public static final String SOURCE_SEED = "SEED_DEMO";
    public static final String STATUS_MATERIALIZED = "MATERIALIZED";
    public static final String STATUS_STALE = "STALE";
    public static final String STATUS_MISSING = "MISSING";
    public static final String STATUS_RETIRED = "RETIRED";
    public static final String STATUS_FAILED = "FAILED";
    public static final String STATUS_SEED_DEMO = "SEED_DEMO";
    public static final String STATUS_CONFIG_MISMATCH = "CONFIGURATION_MISMATCH";

    @Id
    @Column(name = "queue_id", nullable = false)
    private UUID queueId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    /** Physical facility scope — NULL for virtual-pool queues (V034). */
    @Column(name = "facility_id")
    private UUID facilityId;

    /**
     * Virtual-pool scope: the TUSO routing seam target_ref (frozen pool key,
     * carried by pool-routed referrals as routing_pool_id). NULL for facility
     * queues. A queue always has exactly one scope (DB CHECK, V034).
     */
    @Column(name = "virtual_pool_id", length = 128)
    private String virtualPoolId;

    @Column(name = "workspace_id")
    private UUID workspaceId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "queue_type", nullable = false)
    private String queueType = "FIFO";

    @JdbcTypeCode(SqlTypes.JSON)


    @Column(name = "rules", columnDefinition = "jsonb")
    private String rules;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    /** Where this queue came from: TUSO_MATERIALISED (production truth) or SEED_DEMO (dev/test only). */
    @Column(name = "source", nullable = false)
    private String source = SOURCE_SEED;

    /** Stable reference to the owning TUSO config object (service point id) for idempotent materialisation. */
    @Column(name = "source_ref")
    private String sourceRef;

    @Column(name = "materialization_status", nullable = false)
    private String materializationStatus = STATUS_SEED_DEMO;

    @Column(name = "last_materialized_at")
    private OffsetDateTime lastMaterializedAt;

    @Column(name = "tuso_updated_at")
    private OffsetDateTime tusoUpdatedAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
        if (updatedAt == null) {
            updatedAt = createdAt;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    // Getters and setters

    public UUID getQueueId() { return queueId; }
    public void setQueueId(UUID queueId) { this.queueId = queueId; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public UUID getFacilityId() { return facilityId; }
    public void setFacilityId(UUID facilityId) { this.facilityId = facilityId; }

    public UUID getWorkspaceId() { return workspaceId; }
    public void setWorkspaceId(UUID workspaceId) { this.workspaceId = workspaceId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getQueueType() { return queueType; }
    public void setQueueType(String queueType) { this.queueType = queueType; }

    public String getRules() { return rules; }
    public void setRules(String rules) { this.rules = rules; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    public String getSourceRef() { return sourceRef; }
    public void setSourceRef(String sourceRef) { this.sourceRef = sourceRef; }

    public String getVirtualPoolId() { return virtualPoolId; }
    public void setVirtualPoolId(String virtualPoolId) { this.virtualPoolId = virtualPoolId; }

    public String getMaterializationStatus() { return materializationStatus; }
    public void setMaterializationStatus(String materializationStatus) { this.materializationStatus = materializationStatus; }

    public OffsetDateTime getLastMaterializedAt() { return lastMaterializedAt; }
    public void setLastMaterializedAt(OffsetDateTime lastMaterializedAt) { this.lastMaterializedAt = lastMaterializedAt; }

    public OffsetDateTime getTusoUpdatedAt() { return tusoUpdatedAt; }
    public void setTusoUpdatedAt(OffsetDateTime tusoUpdatedAt) { this.tusoUpdatedAt = tusoUpdatedAt; }

    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
}
