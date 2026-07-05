package zw.gov.mohcc.impilo.khuluma.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * MEETING-domain record of a lobby admission decision, mirroring the rtc-gateway
 * waiting-room (transport truth stays in {@code rtc.session_participants}): who knocked
 * on the meeting, which host/cohost admitted or denied them, and the attendance stamps
 * ({@code joinedAt}/{@code leftAt}) derived from {@code impilo.rtc.participant.*}
 * media-truth events. One row per (conversation, actor).
 */
@Entity
@Table(name = "khuluma_meeting_admissions")
public class MeetingAdmissionEntity {

    public static final String STATUS_WAITING = "WAITING";
    public static final String STATUS_ADMITTED = "ADMITTED";
    public static final String STATUS_DENIED = "DENIED";

    @Id
    @Column(name = "admission_id", nullable = false)
    private UUID admissionId = UUID.randomUUID();

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "conversation_id", nullable = false)
    private UUID conversationId;

    @Column(name = "actor_id", nullable = false, length = 255)
    private String actorId;

    @Column(name = "actor_type", nullable = false, length = 64)
    private String actorType = "PROVIDER";

    @Column(name = "display_name", length = 255)
    private String displayName;

    @Column(name = "meeting_role", length = 32)
    private String meetingRole;

    @Column(name = "status", nullable = false, length = 32)
    private String status = STATUS_WAITING;

    @Column(name = "rtc_session_id", length = 255)
    private String rtcSessionId;

    @Column(name = "requested_at", nullable = false)
    private OffsetDateTime requestedAt = OffsetDateTime.now();

    @Column(name = "decided_by", length = 255)
    private String decidedBy;

    @Column(name = "decided_at")
    private OffsetDateTime decidedAt;

    @Column(name = "denied_reason", length = 255)
    private String deniedReason;

    @Column(name = "joined_at")
    private OffsetDateTime joinedAt;

    @Column(name = "left_at")
    private OffsetDateTime leftAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    public MeetingAdmissionEntity() {}

    public UUID getAdmissionId() { return admissionId; }
    public void setAdmissionId(UUID admissionId) { this.admissionId = admissionId; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public UUID getConversationId() { return conversationId; }
    public void setConversationId(UUID conversationId) { this.conversationId = conversationId; }

    public String getActorId() { return actorId; }
    public void setActorId(String actorId) { this.actorId = actorId; }

    public String getActorType() { return actorType; }
    public void setActorType(String actorType) { this.actorType = actorType; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public String getMeetingRole() { return meetingRole; }
    public void setMeetingRole(String meetingRole) { this.meetingRole = meetingRole; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getRtcSessionId() { return rtcSessionId; }
    public void setRtcSessionId(String rtcSessionId) { this.rtcSessionId = rtcSessionId; }

    public OffsetDateTime getRequestedAt() { return requestedAt; }
    public void setRequestedAt(OffsetDateTime requestedAt) { this.requestedAt = requestedAt; }

    public String getDecidedBy() { return decidedBy; }
    public void setDecidedBy(String decidedBy) { this.decidedBy = decidedBy; }

    public OffsetDateTime getDecidedAt() { return decidedAt; }
    public void setDecidedAt(OffsetDateTime decidedAt) { this.decidedAt = decidedAt; }

    public String getDeniedReason() { return deniedReason; }
    public void setDeniedReason(String deniedReason) { this.deniedReason = deniedReason; }

    public OffsetDateTime getJoinedAt() { return joinedAt; }
    public void setJoinedAt(OffsetDateTime joinedAt) { this.joinedAt = joinedAt; }

    public OffsetDateTime getLeftAt() { return leftAt; }
    public void setLeftAt(OffsetDateTime leftAt) { this.leftAt = leftAt; }

    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
