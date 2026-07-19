package zw.gov.mohcc.impilo.coverage.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Claim event / lineage audit trail (spec §7 §40). */
@Entity
@Table(name = "cv_claim_events")
public class ClaimEventEntity {

    @Id @Column(name = "id", nullable = false)
    private UUID id;
    @Column(name = "tenant_id", nullable = false) private UUID tenantId;
    @Column(name = "claim_id", nullable = false) private UUID claimId;
    @Column(name = "event_type", nullable = false, length = 48) private String eventType;
    @Column(name = "from_status", length = 32) private String fromStatus;
    @Column(name = "to_status", length = 32) private String toStatus;
    @Column(name = "actor", length = 128) private String actor;
    @Column(name = "organisation", length = 128) private String organisation;
    @Column(name = "reason", length = 255) private String reason;
    @Column(name = "correlation_id") private UUID correlationId;
    @Column(name = "occurred_at", nullable = false) private OffsetDateTime occurredAt = OffsetDateTime.now();

    public ClaimEventEntity() {}

    public static ClaimEventEntity of(UUID tenantId, UUID claimId, String eventType, String fromStatus,
                                      String toStatus, String actor, String reason, UUID correlationId) {
        ClaimEventEntity e = new ClaimEventEntity();
        e.id = UUID.randomUUID();
        e.tenantId = tenantId;
        e.claimId = claimId;
        e.eventType = eventType;
        e.fromStatus = fromStatus;
        e.toStatus = toStatus;
        e.actor = actor;
        e.reason = reason;
        e.correlationId = correlationId;
        return e;
    }

    public UUID getId() { return id; }
    public UUID getClaimId() { return claimId; }
    public String getEventType() { return eventType; }
    public String getFromStatus() { return fromStatus; }
    public String getToStatus() { return toStatus; }
    public String getActor() { return actor; }
    public String getReason() { return reason; }
    public OffsetDateTime getOccurredAt() { return occurredAt; }
}
