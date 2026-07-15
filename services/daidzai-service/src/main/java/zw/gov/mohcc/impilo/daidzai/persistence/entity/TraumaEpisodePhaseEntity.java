package zw.gov.mohcc.impilo.daidzai.persistence.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * A single node on a trauma episode's read-model timeline. DAIDZAI records the INCIDENT phase on
 * mint; phase owners register their phase (synchronously in W1, via the outbox drainer in W2). The
 * uniqueness of {@code (tenant, episode, owner_service, owner_ref, phase)} makes registration
 * idempotent — a retried/redelivered phase never doubles a timeline row.
 */
@Entity
@Table(name = "dai_trauma_episode_phase", schema = "daidzai",
        uniqueConstraints = @UniqueConstraint(name = "uq_dai_trauma_phase",
                columnNames = {"tenant_id", "trauma_episode_id", "owner_service", "owner_ref", "phase"}))
public class TraumaEpisodePhaseEntity {

    @Id @Column(name = "id") private UUID id;
    @Column(name = "tenant_id", nullable = false) private UUID tenantId;
    @Column(name = "trauma_episode_id", nullable = false) private UUID traumaEpisodeId;
    @Column(name = "phase", nullable = false, length = 32) private String phase;
    @Column(name = "owner_service", nullable = false, length = 48) private String ownerService;
    @Column(name = "owner_ref", nullable = false, length = 128) private String ownerRef;
    @Column(name = "status", length = 48) private String status;
    @Column(name = "event_type", length = 96) private String eventType;
    @Column(name = "payload_json", columnDefinition = "TEXT") private String payloadJson;
    @Column(name = "occurred_at", nullable = false) private OffsetDateTime occurredAt;
    @Column(name = "created_at", nullable = false) private OffsetDateTime createdAt;

    @PrePersist void onCreate() {
        if (id == null) id = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        if (createdAt == null) createdAt = now;
        if (occurredAt == null) occurredAt = now;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID v) { this.tenantId = v; }
    public UUID getTraumaEpisodeId() { return traumaEpisodeId; }
    public void setTraumaEpisodeId(UUID v) { this.traumaEpisodeId = v; }
    public String getPhase() { return phase; }
    public void setPhase(String v) { this.phase = v; }
    public String getOwnerService() { return ownerService; }
    public void setOwnerService(String v) { this.ownerService = v; }
    public String getOwnerRef() { return ownerRef; }
    public void setOwnerRef(String v) { this.ownerRef = v; }
    public String getStatus() { return status; }
    public void setStatus(String v) { this.status = v; }
    public String getEventType() { return eventType; }
    public void setEventType(String v) { this.eventType = v; }
    public String getPayloadJson() { return payloadJson; }
    public void setPayloadJson(String v) { this.payloadJson = v; }
    public OffsetDateTime getOccurredAt() { return occurredAt; }
    public void setOccurredAt(OffsetDateTime v) { this.occurredAt = v; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
