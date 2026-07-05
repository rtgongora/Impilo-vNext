package zw.gov.mohcc.impilo.live.persistence.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * An audience participant's request to join the stage of a LIVE_EVENT room.
 * Server-side role escalation truth: SPEAKER tokens for non-crew participants
 * are minted only against an APPROVED row.
 */
@Entity
@Table(name = "live_event_stage_requests", schema = "live")
public class LiveEventStageRequestEntity {

    public static final String STATUS_REQUESTED = "REQUESTED";
    public static final String STATUS_APPROVED = "APPROVED";
    public static final String STATUS_DENIED = "DENIED";
    public static final String STATUS_DEMOTED = "DEMOTED";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    @Column(name = "participant_id", nullable = false)
    private String participantId;

    @Column(name = "participant_type", nullable = false, length = 32)
    private String participantType = "CITIZEN";

    @Column(nullable = false, length = 16)
    private String status = STATUS_REQUESTED;

    @Column(name = "requested_at", nullable = false)
    private OffsetDateTime requestedAt;

    @Column(name = "decided_by")
    private String decidedBy;

    @Column(name = "decided_at")
    private OffsetDateTime decidedAt;

    @PrePersist
    void onCreate() {
        if (requestedAt == null) requestedAt = OffsetDateTime.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getEventId() { return eventId; }
    public void setEventId(UUID eventId) { this.eventId = eventId; }
    public String getParticipantId() { return participantId; }
    public void setParticipantId(String participantId) { this.participantId = participantId; }
    public String getParticipantType() { return participantType; }
    public void setParticipantType(String participantType) { this.participantType = participantType; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public OffsetDateTime getRequestedAt() { return requestedAt; }
    public void setRequestedAt(OffsetDateTime requestedAt) { this.requestedAt = requestedAt; }
    public String getDecidedBy() { return decidedBy; }
    public void setDecidedBy(String decidedBy) { this.decidedBy = decidedBy; }
    public OffsetDateTime getDecidedAt() { return decidedAt; }
    public void setDecidedAt(OffsetDateTime decidedAt) { this.decidedAt = decidedAt; }
}
