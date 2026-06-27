package zw.gov.mohcc.impilo.khuluma.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Call lifecycle context layered over an rtc-gateway/LiveKit media session. Khuluma owns
 * ringing/accept/decline/end + escalation links; the media room and tokens stay in
 * rtc-gateway ({@code rtcSessionId}/{@code roomName} reference the provisioned session).
 */
@Entity
@Table(name = "khuluma_calls")
public class CallEntity {

    @Id
    @Column(name = "call_id", nullable = false)
    private UUID callId = UUID.randomUUID();

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "conversation_id")
    private UUID conversationId;

    @Column(name = "call_type", nullable = false, length = 16)
    private String callType = "AUDIO";

    @Column(name = "status", nullable = false, length = 32)
    private String status = "RINGING";

    @Column(name = "initiated_by", nullable = false, length = 255)
    private String initiatedBy;

    @Column(name = "initiated_by_type", nullable = false, length = 64)
    private String initiatedByType = "PROVIDER";

    @Column(name = "rtc_session_id", length = 255)
    private String rtcSessionId;

    @Column(name = "room_name", length = 255)
    private String roomName;

    @Column(name = "provider", length = 64)
    private String provider;

    @Column(name = "end_reason", length = 64)
    private String endReason;

    @Column(name = "initiated_at", nullable = false)
    private OffsetDateTime initiatedAt = OffsetDateTime.now();

    @Column(name = "started_at")
    private OffsetDateTime startedAt;

    @Column(name = "ended_at")
    private OffsetDateTime endedAt;

    public CallEntity() {}

    public UUID getCallId() { return callId; }
    public void setCallId(UUID callId) { this.callId = callId; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public UUID getConversationId() { return conversationId; }
    public void setConversationId(UUID conversationId) { this.conversationId = conversationId; }

    public String getCallType() { return callType; }
    public void setCallType(String callType) { this.callType = callType; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getInitiatedBy() { return initiatedBy; }
    public void setInitiatedBy(String initiatedBy) { this.initiatedBy = initiatedBy; }

    public String getInitiatedByType() { return initiatedByType; }
    public void setInitiatedByType(String initiatedByType) { this.initiatedByType = initiatedByType; }

    public String getRtcSessionId() { return rtcSessionId; }
    public void setRtcSessionId(String rtcSessionId) { this.rtcSessionId = rtcSessionId; }

    public String getRoomName() { return roomName; }
    public void setRoomName(String roomName) { this.roomName = roomName; }

    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }

    public String getEndReason() { return endReason; }
    public void setEndReason(String endReason) { this.endReason = endReason; }

    public OffsetDateTime getInitiatedAt() { return initiatedAt; }
    public void setInitiatedAt(OffsetDateTime initiatedAt) { this.initiatedAt = initiatedAt; }

    public OffsetDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(OffsetDateTime startedAt) { this.startedAt = startedAt; }

    public OffsetDateTime getEndedAt() { return endedAt; }
    public void setEndedAt(OffsetDateTime endedAt) { this.endedAt = endedAt; }
}
