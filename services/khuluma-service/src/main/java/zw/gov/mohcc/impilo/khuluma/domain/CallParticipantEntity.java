package zw.gov.mohcc.impilo.khuluma.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * An actor invited to / participating in a call, with ring/accept/decline/leave state.
 */
@Entity
@Table(name = "khuluma_call_participants")
public class CallParticipantEntity {

    @Id
    @Column(name = "call_participant_id", nullable = false)
    private UUID callParticipantId = UUID.randomUUID();

    @Column(name = "call_id", nullable = false)
    private UUID callId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "actor_id", nullable = false, length = 255)
    private String actorId;

    @Column(name = "actor_type", nullable = false, length = 64)
    private String actorType = "PROVIDER";

    @Column(name = "display_name", length = 255)
    private String displayName;

    @Column(name = "state", nullable = false, length = 32)
    private String state = "INVITED";

    @Column(name = "invited_at", nullable = false)
    private OffsetDateTime invitedAt = OffsetDateTime.now();

    @Column(name = "joined_at")
    private OffsetDateTime joinedAt;

    @Column(name = "left_at")
    private OffsetDateTime leftAt;

    public CallParticipantEntity() {}

    public UUID getCallParticipantId() { return callParticipantId; }
    public void setCallParticipantId(UUID callParticipantId) { this.callParticipantId = callParticipantId; }

    public UUID getCallId() { return callId; }
    public void setCallId(UUID callId) { this.callId = callId; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public String getActorId() { return actorId; }
    public void setActorId(String actorId) { this.actorId = actorId; }

    public String getActorType() { return actorType; }
    public void setActorType(String actorType) { this.actorType = actorType; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public String getState() { return state; }
    public void setState(String state) { this.state = state; }

    public OffsetDateTime getInvitedAt() { return invitedAt; }
    public void setInvitedAt(OffsetDateTime invitedAt) { this.invitedAt = invitedAt; }

    public OffsetDateTime getJoinedAt() { return joinedAt; }
    public void setJoinedAt(OffsetDateTime joinedAt) { this.joinedAt = joinedAt; }

    public OffsetDateTime getLeftAt() { return leftAt; }
    public void setLeftAt(OffsetDateTime leftAt) { this.leftAt = leftAt; }
}
