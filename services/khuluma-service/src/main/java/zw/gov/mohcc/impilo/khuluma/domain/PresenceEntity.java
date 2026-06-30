package zw.gov.mohcc.impilo.khuluma.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Current presence for an actor (one row per tenant+actor). Updated on
 * heartbeat/status change and broadcast over the realtime gateway (W1.3).
 */
@Entity
@Table(name = "khuluma_presence")
public class PresenceEntity {

    @Id
    @Column(name = "presence_id", nullable = false)
    private UUID presenceId = UUID.randomUUID();

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "actor_id", nullable = false, length = 255)
    private String actorId;

    @Column(name = "actor_type", nullable = false, length = 64)
    private String actorType = "PROVIDER";

    @Column(name = "status", nullable = false, length = 32)
    private String status = "OFFLINE";

    @Column(name = "status_message", length = 255)
    private String statusMessage;

    @Column(name = "device", length = 128)
    private String device;

    @Column(name = "last_active_at")
    private OffsetDateTime lastActiveAt;

    @Column(name = "last_seen_at")
    private OffsetDateTime lastSeenAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    /** Vashandi duty status (W7): OFF_DUTY | ON_DUTY | ON_CALL — orthogonal to availability status. */
    @Column(name = "duty_status", nullable = false, length = 16)
    private String dutyStatus = "OFF_DUTY";

    public PresenceEntity() {}

    public String getDutyStatus() { return dutyStatus; }
    public void setDutyStatus(String dutyStatus) { this.dutyStatus = dutyStatus; }

    public UUID getPresenceId() { return presenceId; }
    public void setPresenceId(UUID presenceId) { this.presenceId = presenceId; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public String getActorId() { return actorId; }
    public void setActorId(String actorId) { this.actorId = actorId; }

    public String getActorType() { return actorType; }
    public void setActorType(String actorType) { this.actorType = actorType; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getStatusMessage() { return statusMessage; }
    public void setStatusMessage(String statusMessage) { this.statusMessage = statusMessage; }

    public String getDevice() { return device; }
    public void setDevice(String device) { this.device = device; }

    public OffsetDateTime getLastActiveAt() { return lastActiveAt; }
    public void setLastActiveAt(OffsetDateTime lastActiveAt) { this.lastActiveAt = lastActiveAt; }

    public OffsetDateTime getLastSeenAt() { return lastSeenAt; }
    public void setLastSeenAt(OffsetDateTime lastSeenAt) { this.lastSeenAt = lastSeenAt; }

    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
