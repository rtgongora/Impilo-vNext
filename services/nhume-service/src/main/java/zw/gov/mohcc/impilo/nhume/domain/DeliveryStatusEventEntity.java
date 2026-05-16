package zw.gov.mohcc.impilo.nhume.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "nhume_delivery_status_events")
public class DeliveryStatusEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "delivery_id", nullable = false)
    private UUID deliveryId;

    @Column(name = "previous_status", length = 32)
    private String previousStatus;

    @Column(name = "new_status", nullable = false, length = 32)
    private String newStatus;

    @Column(name = "actor_id", length = 255)
    private String actorId;

    @Column(name = "actor_type", length = 48)
    private String actorType;

    @Column(name = "reason", length = 512)
    private String reason;

    @Column(name = "metadata_json", nullable = false, columnDefinition = "TEXT")
    private String metadataJson = "{}";

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    public DeliveryStatusEventEntity() {}

    public Long getId() { return id; }
    public UUID getDeliveryId() { return deliveryId; }
    public void setDeliveryId(UUID v) { this.deliveryId = v; }
    public String getPreviousStatus() { return previousStatus; }
    public void setPreviousStatus(String v) { this.previousStatus = v; }
    public String getNewStatus() { return newStatus; }
    public void setNewStatus(String v) { this.newStatus = v; }
    public String getActorId() { return actorId; }
    public void setActorId(String v) { this.actorId = v; }
    public String getActorType() { return actorType; }
    public void setActorType(String v) { this.actorType = v; }
    public String getReason() { return reason; }
    public void setReason(String v) { this.reason = v; }
    public String getMetadataJson() { return metadataJson; }
    public void setMetadataJson(String v) { this.metadataJson = v; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime v) { this.createdAt = v; }
}
