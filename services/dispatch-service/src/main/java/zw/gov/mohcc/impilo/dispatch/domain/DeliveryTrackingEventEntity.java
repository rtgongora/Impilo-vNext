package zw.gov.mohcc.impilo.dispatch.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "dsp_delivery_tracking_events")
public class DeliveryTrackingEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "delivery_id", nullable = false)
    private UUID deliveryId;

    @Column(name = "assignment_id")
    private UUID assignmentId;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(name = "latitude")
    private Double latitude;

    @Column(name = "longitude")
    private Double longitude;

    @Column(name = "accuracy_meters")
    private Double accuracyMeters;

    @Column(name = "source")
    private String source;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "details_json", columnDefinition = "jsonb")
    private String detailsJson;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected DeliveryTrackingEventEntity() {}

    public DeliveryTrackingEventEntity(UUID deliveryId, String eventType) {
        this.deliveryId = deliveryId;
        this.eventType = eventType;
        this.createdAt = OffsetDateTime.now();
    }

    public Long getId() { return id; }
    public UUID getDeliveryId() { return deliveryId; }
    public UUID getAssignmentId() { return assignmentId; }
    public void setAssignmentId(UUID assignmentId) { this.assignmentId = assignmentId; }
    public String getEventType() { return eventType; }
    public Double getLatitude() { return latitude; }
    public void setLatitude(Double latitude) { this.latitude = latitude; }
    public Double getLongitude() { return longitude; }
    public void setLongitude(Double longitude) { this.longitude = longitude; }
    public Double getAccuracyMeters() { return accuracyMeters; }
    public void setAccuracyMeters(Double accuracyMeters) { this.accuracyMeters = accuracyMeters; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public String getDetailsJson() { return detailsJson; }
    public void setDetailsJson(String detailsJson) { this.detailsJson = detailsJson; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
