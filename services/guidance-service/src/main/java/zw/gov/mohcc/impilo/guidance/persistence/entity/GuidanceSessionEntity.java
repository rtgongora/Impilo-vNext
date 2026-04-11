package zw.gov.mohcc.impilo.guidance.persistence.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "guidance_sessions", schema = "guidance")
public class GuidanceSessionEntity {

    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false) private String tenantId;
    @Column(name = "actor_id", nullable = false) private String actorId;
    @Column(name = "started_at", nullable = false) private OffsetDateTime startedAt;
    @Column(name = "last_message_at", nullable = false) private OffsetDateTime lastMessageAt;
    @Column(name = "message_count", nullable = false) private int messageCount;
    @Column(nullable = false) private boolean personalized;
    @Column(name = "context_json", columnDefinition = "JSONB") private String contextJson;
    @Column(nullable = false, length = 32) private String status = "ACTIVE";

    protected GuidanceSessionEntity() {}

    public UUID getId() { return id; }
    public String getTenantId() { return tenantId; }
    public String getActorId() { return actorId; }
    public OffsetDateTime getStartedAt() { return startedAt; }
    public int getMessageCount() { return messageCount; }
    public boolean isPersonalized() { return personalized; }
    public String getStatus() { return status; }

    public void setLastMessageAt(OffsetDateTime t) { this.lastMessageAt = t; }
    public void setMessageCount(int c) { this.messageCount = c; }
}
