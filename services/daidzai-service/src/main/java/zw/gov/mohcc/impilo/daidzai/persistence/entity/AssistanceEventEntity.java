package zw.gov.mohcc.impilo.daidzai.persistence.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Append-only assistance-case timeline event (V003; mirrors {@link MissionEventEntity}). */
@Entity
@Table(name = "dai_assistance_event", schema = "daidzai")
public class AssistanceEventEntity {

    @Id @Column(name = "id") private UUID id;
    @Column(name = "tenant_id", nullable = false) private UUID tenantId;
    @Column(name = "assistance_id", nullable = false) private UUID assistanceId;
    @Column(name = "event_type", nullable = false, length = 32) private String eventType;
    @Column(name = "from_status", length = 24) private String fromStatus;
    @Column(name = "to_status", length = 24) private String toStatus;
    @Column(name = "note", length = 1024) private String note;
    @Column(name = "actor_id", length = 128) private String actorId;
    @Column(name = "actor_type", length = 48) private String actorType;
    @Column(name = "occurred_at", nullable = false) private OffsetDateTime occurredAt;
    @Column(name = "created_at", nullable = false) private OffsetDateTime createdAt;

    @PrePersist void onCreate() {
        if (id == null) id = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        if (occurredAt == null) occurredAt = now;
        if (createdAt == null) createdAt = now;
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public UUID getAssistanceId() { return assistanceId; }
    public void setAssistanceId(UUID assistanceId) { this.assistanceId = assistanceId; }
    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }
    public String getFromStatus() { return fromStatus; }
    public void setFromStatus(String fromStatus) { this.fromStatus = fromStatus; }
    public String getToStatus() { return toStatus; }
    public void setToStatus(String toStatus) { this.toStatus = toStatus; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public String getActorId() { return actorId; }
    public void setActorId(String actorId) { this.actorId = actorId; }
    public String getActorType() { return actorType; }
    public void setActorType(String actorType) { this.actorType = actorType; }
    public OffsetDateTime getOccurredAt() { return occurredAt; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
