package zw.gov.mohcc.impilo.khuluma.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Membership of an actor in a conversation. Drives the inbox query, the unread
 * computation (via {@code lastReadAt} / {@code lastReadMessageId}), and message-access
 * membership checks.
 */
@Entity
@Table(name = "khuluma_conversation_participants")
public class ConversationParticipantEntity {

    @Id
    @Column(name = "participant_id", nullable = false)
    private UUID participantId = UUID.randomUUID();

    @Column(name = "conversation_id", nullable = false)
    private UUID conversationId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "actor_id", nullable = false, length = 255)
    private String actorId;

    @Column(name = "actor_type", nullable = false, length = 64)
    private String actorType = "PROVIDER";

    @Column(name = "display_name", length = 255)
    private String displayName;

    @Column(name = "role", nullable = false, length = 32)
    private String role = "MEMBER";

    @Column(name = "joined_at", nullable = false)
    private OffsetDateTime joinedAt = OffsetDateTime.now();

    @Column(name = "left_at")
    private OffsetDateTime leftAt;

    @Column(name = "last_read_at")
    private OffsetDateTime lastReadAt;

    @Column(name = "last_read_message_id")
    private UUID lastReadMessageId;

    @Column(name = "muted", nullable = false)
    private boolean muted = false;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    public ConversationParticipantEntity() {}

    public UUID getParticipantId() { return participantId; }
    public void setParticipantId(UUID participantId) { this.participantId = participantId; }

    public UUID getConversationId() { return conversationId; }
    public void setConversationId(UUID conversationId) { this.conversationId = conversationId; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public String getActorId() { return actorId; }
    public void setActorId(String actorId) { this.actorId = actorId; }

    public String getActorType() { return actorType; }
    public void setActorType(String actorType) { this.actorType = actorType; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public OffsetDateTime getJoinedAt() { return joinedAt; }
    public void setJoinedAt(OffsetDateTime joinedAt) { this.joinedAt = joinedAt; }

    public OffsetDateTime getLeftAt() { return leftAt; }
    public void setLeftAt(OffsetDateTime leftAt) { this.leftAt = leftAt; }

    public OffsetDateTime getLastReadAt() { return lastReadAt; }
    public void setLastReadAt(OffsetDateTime lastReadAt) { this.lastReadAt = lastReadAt; }

    public UUID getLastReadMessageId() { return lastReadMessageId; }
    public void setLastReadMessageId(UUID lastReadMessageId) { this.lastReadMessageId = lastReadMessageId; }

    public boolean isMuted() { return muted; }
    public void setMuted(boolean muted) { this.muted = muted; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
