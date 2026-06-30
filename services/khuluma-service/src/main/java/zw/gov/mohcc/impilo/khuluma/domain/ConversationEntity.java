package zw.gov.mohcc.impilo.khuluma.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * The unified conversation index — one row per conversation across every interaction
 * surface (direct, group, facility/programme channel, broadcast). Khuluma owns this
 * index; message bodies live in {@link MessageEntity}, membership in
 * {@link ConversationParticipantEntity}, object links in {@link ConversationLinkEntity}.
 */
@Entity
@Table(name = "khuluma_conversations")
public class ConversationEntity {

    @Id
    @Column(name = "conversation_id", nullable = false)
    private UUID conversationId = UUID.randomUUID();

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "conversation_type", nullable = false, length = 32)
    private String conversationType = "DIRECT";

    @Column(name = "title", length = 255)
    private String title;

    @Column(name = "topic", length = 512)
    private String topic;

    @Column(name = "created_by", nullable = false, length = 255)
    private String createdBy;

    @Column(name = "created_by_type", nullable = false, length = 64)
    private String createdByType = "PROVIDER";

    @Column(name = "status", nullable = false, length = 32)
    private String status = "ACTIVE";

    @Column(name = "last_message_id")
    private UUID lastMessageId;

    @Column(name = "last_message_preview", length = 512)
    private String lastMessagePreview;

    @Column(name = "last_message_at")
    private OffsetDateTime lastMessageAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    /** Channel/community scope (W5): FACILITY | PROGRAMME | COMMUNITY; null for direct/group. */
    @Column(name = "scope_type", length = 32)
    private String scopeType;

    @Column(name = "scope_ref")
    private UUID scopeRef;

    public ConversationEntity() {}

    public UUID getConversationId() { return conversationId; }
    public void setConversationId(UUID conversationId) { this.conversationId = conversationId; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public String getConversationType() { return conversationType; }
    public void setConversationType(String conversationType) { this.conversationType = conversationType; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getTopic() { return topic; }
    public void setTopic(String topic) { this.topic = topic; }

    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }

    public String getCreatedByType() { return createdByType; }
    public void setCreatedByType(String createdByType) { this.createdByType = createdByType; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public UUID getLastMessageId() { return lastMessageId; }
    public void setLastMessageId(UUID lastMessageId) { this.lastMessageId = lastMessageId; }

    public String getLastMessagePreview() { return lastMessagePreview; }
    public void setLastMessagePreview(String lastMessagePreview) { this.lastMessagePreview = lastMessagePreview; }

    public OffsetDateTime getLastMessageAt() { return lastMessageAt; }
    public void setLastMessageAt(OffsetDateTime lastMessageAt) { this.lastMessageAt = lastMessageAt; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }

    public String getScopeType() { return scopeType; }
    public void setScopeType(String scopeType) { this.scopeType = scopeType; }

    public UUID getScopeRef() { return scopeRef; }
    public void setScopeRef(UUID scopeRef) { this.scopeRef = scopeRef; }
}
