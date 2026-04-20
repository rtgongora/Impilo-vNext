package zw.gov.mohcc.impilo.tshepo.offline.persistence;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "offline_action_log", schema = "tshepo_offline")
public class OfflineActionLogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "capability_token_id", nullable = false)
    private UUID capabilityTokenId;

    @Column(name = "actor_id", nullable = false)
    private String actorId;

    @Column(name = "action_type", nullable = false, length = 64)
    private String actionType;

    @Column(name = "resource_type", length = 64)
    private String resourceType;

    @Column(name = "resource_ref")
    private String resourceRef;

    @Column(name = "o_cpid")
    private UUID oCpid;

    @JdbcTypeCode(SqlTypes.JSON)


    @Column(columnDefinition = "jsonb")
    private String payload;

    @Column(name = "performed_at", nullable = false)
    private Instant performedAt;

    @Column(name = "synced_at")
    private Instant syncedAt;

    @Column(name = "sync_status", nullable = false, length = 16)
    private String syncStatus;

    // --- Getters and Setters ---
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public UUID getCapabilityTokenId() { return capabilityTokenId; }
    public void setCapabilityTokenId(UUID capabilityTokenId) { this.capabilityTokenId = capabilityTokenId; }
    public String getActorId() { return actorId; }
    public void setActorId(String actorId) { this.actorId = actorId; }
    public String getActionType() { return actionType; }
    public void setActionType(String actionType) { this.actionType = actionType; }
    public String getResourceType() { return resourceType; }
    public void setResourceType(String resourceType) { this.resourceType = resourceType; }
    public String getResourceRef() { return resourceRef; }
    public void setResourceRef(String resourceRef) { this.resourceRef = resourceRef; }
    public UUID getOCpid() { return oCpid; }
    public void setOCpid(UUID oCpid) { this.oCpid = oCpid; }
    public String getPayload() { return payload; }
    public void setPayload(String payload) { this.payload = payload; }
    public Instant getPerformedAt() { return performedAt; }
    public void setPerformedAt(Instant performedAt) { this.performedAt = performedAt; }
    public Instant getSyncedAt() { return syncedAt; }
    public void setSyncedAt(Instant syncedAt) { this.syncedAt = syncedAt; }
    public String getSyncStatus() { return syncStatus; }
    public void setSyncStatus(String syncStatus) { this.syncStatus = syncStatus; }
}
