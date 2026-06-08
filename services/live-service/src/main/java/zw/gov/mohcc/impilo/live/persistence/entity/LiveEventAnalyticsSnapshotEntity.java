package zw.gov.mohcc.impilo.live.persistence.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "live_event_analytics_snapshots", schema = "live")
public class LiveEventAnalyticsSnapshotEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    @Column(name = "metric_name", nullable = false, length = 128)
    private String metricName;

    @Column(name = "metric_value", nullable = false, precision = 18, scale = 4)
    private BigDecimal metricValue;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String dimensions = "{}";

    @Column(name = "captured_at", nullable = false)
    private OffsetDateTime capturedAt;

    @PrePersist
    void onCreate() {
        if (capturedAt == null) capturedAt = OffsetDateTime.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getEventId() { return eventId; }
    public void setEventId(UUID eventId) { this.eventId = eventId; }
    public String getMetricName() { return metricName; }
    public void setMetricName(String metricName) { this.metricName = metricName; }
    public BigDecimal getMetricValue() { return metricValue; }
    public void setMetricValue(BigDecimal metricValue) { this.metricValue = metricValue; }
    public String getDimensions() { return dimensions; }
    public void setDimensions(String dimensions) { this.dimensions = dimensions; }
    public OffsetDateTime getCapturedAt() { return capturedAt; }
    public void setCapturedAt(OffsetDateTime capturedAt) { this.capturedAt = capturedAt; }
}
