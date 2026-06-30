package zw.gov.mohcc.impilo.assetregistry.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Append-only audit log of every meaningful equipment lifecycle action. */
@Entity
@Table(name = "asr_equipment_lifecycle_event")
public class EquipmentLifecycleEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "event_id")
    private UUID eventId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "equipment_id", nullable = false)
    private UUID equipmentId;

    @Column(name = "event_type", nullable = false, length = 48)
    private String eventType;

    @Column(name = "from_value", length = 255)
    private String fromValue;

    @Column(name = "to_value", length = 255)
    private String toValue;

    @Column(name = "actor_ref", length = 255)
    private String actorRef;

    @Column(name = "note", columnDefinition = "text")
    private String note;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata_json", columnDefinition = "jsonb")
    private String metadataJson;

    @Column(name = "occurred_at", nullable = false)
    private OffsetDateTime occurredAt = OffsetDateTime.now();

    protected EquipmentLifecycleEventEntity() {}

    public EquipmentLifecycleEventEntity(UUID tenantId, UUID equipmentId, String eventType) {
        this.tenantId = tenantId;
        this.equipmentId = equipmentId;
        this.eventType = eventType;
        this.occurredAt = OffsetDateTime.now();
    }

    public UUID getEventId() { return eventId; }
    public UUID getTenantId() { return tenantId; }
    public UUID getEquipmentId() { return equipmentId; }
    public String getEventType() { return eventType; }
    public String getFromValue() { return fromValue; }
    public void setFromValue(String fromValue) { this.fromValue = fromValue; }
    public String getToValue() { return toValue; }
    public void setToValue(String toValue) { this.toValue = toValue; }
    public String getActorRef() { return actorRef; }
    public void setActorRef(String actorRef) { this.actorRef = actorRef; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public String getMetadataJson() { return metadataJson; }
    public void setMetadataJson(String metadataJson) { this.metadataJson = metadataJson; }
    public OffsetDateTime getOccurredAt() { return occurredAt; }
}
