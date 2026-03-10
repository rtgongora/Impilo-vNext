package zw.gov.mohcc.impilo.channels.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "ch_messages")
public class ChannelMessageEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "session_id", nullable = false)
    private UUID sessionId;

    @Column(name = "tenant_id", nullable = false, length = 64)
    private String tenantId;

    @Column(name = "direction", nullable = false, length = 16)
    private String direction;

    @Column(name = "channel_type", nullable = false, length = 32)
    private String channelType;

    @Column(name = "content_type", nullable = false, length = 32)
    private String contentType = "TEXT";

    @Column(name = "payload_json", nullable = false, columnDefinition = "TEXT")
    private String payloadJson;

    @Column(name = "sender_id", length = 255)
    private String senderId;

    @Column(name = "delivery_status", nullable = false, length = 32)
    private String deliveryStatus = "PENDING";

    @Column(name = "delivered_at")
    private OffsetDateTime deliveredAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected ChannelMessageEntity() {}

    public ChannelMessageEntity(UUID sessionId, String tenantId, String direction,
                                String channelType, String contentType, String payloadJson) {
        this.id = UUID.randomUUID();
        this.sessionId = sessionId;
        this.tenantId = tenantId;
        this.direction = direction;
        this.channelType = channelType;
        this.contentType = contentType != null ? contentType : "TEXT";
        this.payloadJson = payloadJson;
    }

    @PrePersist
    protected void onCreate() {
        if (id == null) id = UUID.randomUUID();
        if (createdAt == null) createdAt = OffsetDateTime.now();
    }

    public UUID getId() { return id; }
    public UUID getSessionId() { return sessionId; }
    public String getTenantId() { return tenantId; }
    public String getDirection() { return direction; }
    public String getChannelType() { return channelType; }
    public String getContentType() { return contentType; }
    public String getPayloadJson() { return payloadJson; }
    public String getSenderId() { return senderId; }
    public void setSenderId(String senderId) { this.senderId = senderId; }
    public String getDeliveryStatus() { return deliveryStatus; }
    public void setDeliveryStatus(String deliveryStatus) { this.deliveryStatus = deliveryStatus; }
    public OffsetDateTime getDeliveredAt() { return deliveredAt; }
    public void setDeliveredAt(OffsetDateTime deliveredAt) { this.deliveredAt = deliveredAt; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
