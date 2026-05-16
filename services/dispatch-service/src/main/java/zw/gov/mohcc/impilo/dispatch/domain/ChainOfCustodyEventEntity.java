package zw.gov.mohcc.impilo.dispatch.domain;

import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "dsp_chain_of_custody_events")
public class ChainOfCustodyEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "delivery_id", nullable = false)
    private UUID deliveryId;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(name = "actor_id")
    private String actorId;

    @Column(name = "actor_type")
    private String actorType;

    @Column(name = "notes_json", columnDefinition = "TEXT")
    private String notesJson;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected ChainOfCustodyEventEntity() {}

    public ChainOfCustodyEventEntity(UUID deliveryId, String eventType) {
        this.deliveryId = deliveryId;
        this.eventType = eventType;
        this.createdAt = OffsetDateTime.now();
    }

    public Long getId() { return id; }
    public UUID getDeliveryId() { return deliveryId; }
    public String getEventType() { return eventType; }
    public String getActorId() { return actorId; }
    public void setActorId(String actorId) { this.actorId = actorId; }
    public String getActorType() { return actorType; }
    public void setActorType(String actorType) { this.actorType = actorType; }
    public String getNotesJson() { return notesJson; }
    public void setNotesJson(String notesJson) { this.notesJson = notesJson; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
