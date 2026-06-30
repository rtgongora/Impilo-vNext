package zw.gov.mohcc.impilo.khuluma.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

/** An auditable per-channel delivery outcome (W6, G-KH-03). */
@Entity
@Table(name = "khuluma_delivery_attempt")
public class DeliveryAttemptEntity {

    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false) private UUID tenantId;
    @Column(name = "message_ref") private String messageRef;
    @Column private String recipient;
    @Column(nullable = false, length = 16) private String channel;
    @Column(nullable = false, length = 28) private String status;
    @Column(columnDefinition = "TEXT") private String detail;
    @Column(name = "attempted_at", nullable = false) private OffsetDateTime attemptedAt;

    @PrePersist void onCreate() { if (attemptedAt == null) attemptedAt = OffsetDateTime.now(); }

    public DeliveryAttemptEntity() {}

    public DeliveryAttemptEntity(UUID tenantId, String messageRef, String recipient,
                                 String channel, String status, String detail) {
        this.tenantId = tenantId;
        this.messageRef = messageRef;
        this.recipient = recipient;
        this.channel = channel;
        this.status = status;
        this.detail = detail;
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public String getMessageRef() { return messageRef; }
    public String getRecipient() { return recipient; }
    public String getChannel() { return channel; }
    public String getStatus() { return status; }
    public void setStatus(String v) { this.status = v; }
    public String getDetail() { return detail; }
    public OffsetDateTime getAttemptedAt() { return attemptedAt; }
}
