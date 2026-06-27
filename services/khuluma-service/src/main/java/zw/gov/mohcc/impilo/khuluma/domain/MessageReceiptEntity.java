package zw.gov.mohcc.impilo.khuluma.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Per-actor delivery/read receipt for a message. One row per (message, actor, state);
 * a READ receipt is recorded when an actor marks a conversation read up to a message.
 */
@Entity
@Table(name = "khuluma_message_receipts")
public class MessageReceiptEntity {

    @Id
    @Column(name = "receipt_id", nullable = false)
    private UUID receiptId = UUID.randomUUID();

    @Column(name = "message_id", nullable = false)
    private UUID messageId;

    @Column(name = "conversation_id", nullable = false)
    private UUID conversationId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "actor_id", nullable = false, length = 255)
    private String actorId;

    @Column(name = "receipt_state", nullable = false, length = 32)
    private String receiptState;

    @Column(name = "occurred_at", nullable = false)
    private OffsetDateTime occurredAt = OffsetDateTime.now();

    public MessageReceiptEntity() {}

    public UUID getReceiptId() { return receiptId; }
    public void setReceiptId(UUID receiptId) { this.receiptId = receiptId; }

    public UUID getMessageId() { return messageId; }
    public void setMessageId(UUID messageId) { this.messageId = messageId; }

    public UUID getConversationId() { return conversationId; }
    public void setConversationId(UUID conversationId) { this.conversationId = conversationId; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public String getActorId() { return actorId; }
    public void setActorId(String actorId) { this.actorId = actorId; }

    public String getReceiptState() { return receiptState; }
    public void setReceiptState(String receiptState) { this.receiptState = receiptState; }

    public OffsetDateTime getOccurredAt() { return occurredAt; }
    public void setOccurredAt(OffsetDateTime occurredAt) { this.occurredAt = occurredAt; }
}
