package zw.gov.mohcc.impilo.nhume.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "nhume_delivery_tracking_events")
public class DeliveryTrackingEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "delivery_id", nullable = false)
    private UUID deliveryId;

    @Column(name = "captured_at", nullable = false)
    private OffsetDateTime capturedAt;

    @Column(name = "event_kind", nullable = false, length = 48)
    private String eventKind;

    @Column(name = "lat")
    private Double lat;

    @Column(name = "lng")
    private Double lng;

    @Column(name = "accuracy_m")
    private BigDecimal accuracyM;

    @Column(name = "courier_id")
    private UUID courierId;

    @Column(name = "asset_id")
    private UUID assetId;

    @Column(name = "source", nullable = false, length = 48)
    private String source = "NHUME";

    @Column(name = "payload_json", columnDefinition = "TEXT")
    private String payloadJson;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    public DeliveryTrackingEventEntity() {}

    public Long getId() { return id; }
    public UUID getDeliveryId() { return deliveryId; }
    public void setDeliveryId(UUID v) { this.deliveryId = v; }
    public OffsetDateTime getCapturedAt() { return capturedAt; }
    public void setCapturedAt(OffsetDateTime v) { this.capturedAt = v; }
    public String getEventKind() { return eventKind; }
    public void setEventKind(String v) { this.eventKind = v; }
    public Double getLat() { return lat; }
    public void setLat(Double v) { this.lat = v; }
    public Double getLng() { return lng; }
    public void setLng(Double v) { this.lng = v; }
    public BigDecimal getAccuracyM() { return accuracyM; }
    public void setAccuracyM(BigDecimal v) { this.accuracyM = v; }
    public UUID getCourierId() { return courierId; }
    public void setCourierId(UUID v) { this.courierId = v; }
    public UUID getAssetId() { return assetId; }
    public void setAssetId(UUID v) { this.assetId = v; }
    public String getSource() { return source; }
    public void setSource(String v) { this.source = v; }
    public String getPayloadJson() { return payloadJson; }
    public void setPayloadJson(String v) { this.payloadJson = v; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime v) { this.createdAt = v; }
}
