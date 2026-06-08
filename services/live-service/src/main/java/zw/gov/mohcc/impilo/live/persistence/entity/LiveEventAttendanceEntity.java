package zw.gov.mohcc.impilo.live.persistence.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "live_event_attendance", schema = "live")
public class LiveEventAttendanceEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    @Column(name = "participant_id", nullable = false)
    private String participantId;

    @Column(name = "participant_type", nullable = false, length = 32)
    private String participantType;

    @Column(name = "joined_at")
    private OffsetDateTime joinedAt;

    @Column(name = "left_at")
    private OffsetDateTime leftAt;

    @Column(name = "total_watch_minutes", nullable = false)
    private int totalWatchMinutes;

    @Column(name = "live_watch_minutes", nullable = false)
    private int liveWatchMinutes;

    @Column(name = "replay_watch_minutes", nullable = false)
    private int replayWatchMinutes;

    @Column(name = "completion_status", nullable = false, length = 32)
    private String completionStatus = "IN_PROGRESS";

    @Column(name = "eligible_for_certificate", nullable = false)
    private boolean eligibleForCertificate;

    @Column(name = "eligible_for_cpd", nullable = false)
    private boolean eligibleForCpd;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    @PreUpdate
    void touch() {
        updatedAt = OffsetDateTime.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getEventId() { return eventId; }
    public void setEventId(UUID eventId) { this.eventId = eventId; }
    public String getParticipantId() { return participantId; }
    public void setParticipantId(String participantId) { this.participantId = participantId; }
    public String getParticipantType() { return participantType; }
    public void setParticipantType(String participantType) { this.participantType = participantType; }
    public OffsetDateTime getJoinedAt() { return joinedAt; }
    public void setJoinedAt(OffsetDateTime joinedAt) { this.joinedAt = joinedAt; }
    public OffsetDateTime getLeftAt() { return leftAt; }
    public void setLeftAt(OffsetDateTime leftAt) { this.leftAt = leftAt; }
    public int getTotalWatchMinutes() { return totalWatchMinutes; }
    public void setTotalWatchMinutes(int totalWatchMinutes) { this.totalWatchMinutes = totalWatchMinutes; }
    public int getLiveWatchMinutes() { return liveWatchMinutes; }
    public void setLiveWatchMinutes(int liveWatchMinutes) { this.liveWatchMinutes = liveWatchMinutes; }
    public int getReplayWatchMinutes() { return replayWatchMinutes; }
    public void setReplayWatchMinutes(int replayWatchMinutes) { this.replayWatchMinutes = replayWatchMinutes; }
    public String getCompletionStatus() { return completionStatus; }
    public void setCompletionStatus(String completionStatus) { this.completionStatus = completionStatus; }
    public boolean isEligibleForCertificate() { return eligibleForCertificate; }
    public void setEligibleForCertificate(boolean eligibleForCertificate) { this.eligibleForCertificate = eligibleForCertificate; }
    public boolean isEligibleForCpd() { return eligibleForCpd; }
    public void setEligibleForCpd(boolean eligibleForCpd) { this.eligibleForCpd = eligibleForCpd; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
