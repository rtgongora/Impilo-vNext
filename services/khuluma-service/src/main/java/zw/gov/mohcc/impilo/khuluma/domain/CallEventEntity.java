package zw.gov.mohcc.impilo.khuluma.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Append-only call signalling/lifecycle log (RING, ACCEPT, DECLINE, END, SIGNAL, …).
 * Carries the auditable per-event detail for a call.
 */
@Entity
@Table(name = "khuluma_call_events")
public class CallEventEntity {

    @Id
    @Column(name = "call_event_id", nullable = false)
    private UUID callEventId = UUID.randomUUID();

    @Column(name = "call_id", nullable = false)
    private UUID callId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "event_type", nullable = false, length = 32)
    private String eventType;

    @Column(name = "actor_id", length = 255)
    private String actorId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "detail_json", columnDefinition = "jsonb")
    private String detailJson;

    @Column(name = "occurred_at", nullable = false)
    private OffsetDateTime occurredAt = OffsetDateTime.now();

    public CallEventEntity() {}

    public UUID getCallEventId() { return callEventId; }
    public void setCallEventId(UUID callEventId) { this.callEventId = callEventId; }

    public UUID getCallId() { return callId; }
    public void setCallId(UUID callId) { this.callId = callId; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }

    public String getActorId() { return actorId; }
    public void setActorId(String actorId) { this.actorId = actorId; }

    public String getDetailJson() { return detailJson; }
    public void setDetailJson(String detailJson) { this.detailJson = detailJson; }

    public OffsetDateTime getOccurredAt() { return occurredAt; }
    public void setOccurredAt(OffsetDateTime occurredAt) { this.occurredAt = occurredAt; }
}
