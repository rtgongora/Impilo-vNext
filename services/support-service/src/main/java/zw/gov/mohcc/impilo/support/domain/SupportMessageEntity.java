package zw.gov.mohcc.impilo.support.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "sup_support_messages")
public class SupportMessageEntity {
    @Id @Column(name = "message_id") private UUID messageId;
    @Column(name = "ticket_id", nullable = false) private UUID ticketId;
    @Column(name = "tenant_id", nullable = false) private UUID tenantId;
    @Column(name = "sender_ref", nullable = false) private String senderRef;
    @Column(name = "sender_type", nullable = false, length = 32) private String senderType = "AGENT";
    @Column(nullable = false, columnDefinition = "TEXT") private String body;
    @Column(name = "created_at", nullable = false) private OffsetDateTime createdAt;

    protected SupportMessageEntity() {}
    public SupportMessageEntity(UUID messageId, UUID ticketId, UUID tenantId, String senderRef, String senderType, String body) {
        this.messageId = messageId; this.ticketId = ticketId; this.tenantId = tenantId;
        this.senderRef = senderRef; this.senderType = senderType; this.body = body;
        this.createdAt = OffsetDateTime.now();
    }

    public UUID getMessageId() { return messageId; }
    public UUID getTicketId() { return ticketId; }
    public UUID getTenantId() { return tenantId; }
    public String getSenderRef() { return senderRef; }
    public String getSenderType() { return senderType; }
    public String getBody() { return body; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
