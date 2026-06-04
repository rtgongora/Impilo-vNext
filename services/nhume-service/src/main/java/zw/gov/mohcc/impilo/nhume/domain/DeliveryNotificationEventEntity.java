package zw.gov.mohcc.impilo.nhume.domain;

import jakarta.persistence.Column;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "nhume_delivery_notification_events")
public class DeliveryNotificationEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "delivery_id")
    private UUID deliveryId;

    @Column(name = "event_code", nullable = false, length = 64)
    private String eventCode;

    @Column(name = "channel", nullable = false, length = 32)
    private String channel;

    @Column(name = "recipient_ref", length = 255)
    private String recipientRef;

    @Column(name = "template_code", length = 64)
    private String templateCode;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload_json", nullable = false, columnDefinition = "jsonb")
    private String payloadJson = "{}";

    @Column(name = "dispatched_at", nullable = false)
    private OffsetDateTime dispatchedAt = OffsetDateTime.now();

    @Column(name = "delivery_state", nullable = false, length = 32)
    private String deliveryState = "QUEUED";

    @Column(name = "provider_ref", length = 255)
    private String providerRef;

    public DeliveryNotificationEventEntity() {}

    public Long getId() { return id; }
    public UUID getDeliveryId() { return deliveryId; }
    public void setDeliveryId(UUID v) { this.deliveryId = v; }
    public String getEventCode() { return eventCode; }
    public void setEventCode(String v) { this.eventCode = v; }
    public String getChannel() { return channel; }
    public void setChannel(String v) { this.channel = v; }
    public String getRecipientRef() { return recipientRef; }
    public void setRecipientRef(String v) { this.recipientRef = v; }
    public String getTemplateCode() { return templateCode; }
    public void setTemplateCode(String v) { this.templateCode = v; }
    public String getPayloadJson() { return payloadJson; }
    public void setPayloadJson(String v) { this.payloadJson = v; }
    public OffsetDateTime getDispatchedAt() { return dispatchedAt; }
    public void setDispatchedAt(OffsetDateTime v) { this.dispatchedAt = v; }
    public String getDeliveryState() { return deliveryState; }
    public void setDeliveryState(String v) { this.deliveryState = v; }
    public String getProviderRef() { return providerRef; }
    public void setProviderRef(String v) { this.providerRef = v; }
}
