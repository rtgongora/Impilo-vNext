package zw.gov.mohcc.impilo.vito.events.persistence;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * Tracks processed control channel events for idempotency.
 *
 * <p>Each event_id is recorded once. Before processing any control channel
 * event, the consumer checks this table — if the event_id exists, the
 * event is skipped (idempotent replay).</p>
 */
@Entity
@Table(name = "control_channel_watermark", schema = "vito")
public class ControlChannelWatermarkEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, unique = true)
    private String eventId;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(name = "subject_id")
    private String subjectId;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;

    @Column(name = "correlation_id")
    private String correlationId;

    // --- Getters and Setters ---
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }
    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }
    public String getSubjectId() { return subjectId; }
    public void setSubjectId(String subjectId) { this.subjectId = subjectId; }
    public Instant getProcessedAt() { return processedAt; }
    public void setProcessedAt(Instant processedAt) { this.processedAt = processedAt; }
    public String getCorrelationId() { return correlationId; }
    public void setCorrelationId(String correlationId) { this.correlationId = correlationId; }
}
