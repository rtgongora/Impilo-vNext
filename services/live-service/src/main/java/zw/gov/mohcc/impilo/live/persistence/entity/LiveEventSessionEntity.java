package zw.gov.mohcc.impilo.live.persistence.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "live_event_sessions", schema = "live")
public class LiveEventSessionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    @Column(name = "provider_type", nullable = false, length = 32)
    private String providerType = "RTC_GATEWAY";

    @Column(name = "provider_room_id")
    private String providerRoomId;

    @Column(name = "stream_key_ref")
    private String streamKeyRef;

    @Column(name = "playback_url_ref")
    private String playbackUrlRef;

    @Column(name = "recording_ref")
    private String recordingRef;

    @Column(name = "started_at")
    private OffsetDateTime startedAt;

    @Column(name = "ended_at")
    private OffsetDateTime endedAt;

    @Column(name = "health_status", length = 32)
    private String healthStatus = "UNKNOWN";

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = OffsetDateTime.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getEventId() { return eventId; }
    public void setEventId(UUID eventId) { this.eventId = eventId; }
    public String getProviderType() { return providerType; }
    public void setProviderType(String providerType) { this.providerType = providerType; }
    public String getProviderRoomId() { return providerRoomId; }
    public void setProviderRoomId(String providerRoomId) { this.providerRoomId = providerRoomId; }
    public String getStreamKeyRef() { return streamKeyRef; }
    public void setStreamKeyRef(String streamKeyRef) { this.streamKeyRef = streamKeyRef; }
    public String getPlaybackUrlRef() { return playbackUrlRef; }
    public void setPlaybackUrlRef(String playbackUrlRef) { this.playbackUrlRef = playbackUrlRef; }
    public String getRecordingRef() { return recordingRef; }
    public void setRecordingRef(String recordingRef) { this.recordingRef = recordingRef; }
    public OffsetDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(OffsetDateTime startedAt) { this.startedAt = startedAt; }
    public OffsetDateTime getEndedAt() { return endedAt; }
    public void setEndedAt(OffsetDateTime endedAt) { this.endedAt = endedAt; }
    public String getHealthStatus() { return healthStatus; }
    public void setHealthStatus(String healthStatus) { this.healthStatus = healthStatus; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
