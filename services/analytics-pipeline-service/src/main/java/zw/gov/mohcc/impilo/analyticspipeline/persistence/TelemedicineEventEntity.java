package zw.gov.mohcc.impilo.analyticspipeline.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "telemedicine_events", schema = "analytics")
public class TelemedicineEventEntity {

    @Id
    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "event_type", nullable = false, length = 64)
    private String eventType;

    @Column(name = "facility_id", length = 64)
    private String facilityId;

    @Column(name = "payload_json", columnDefinition = "TEXT")
    private String payloadJson;

    @Column(name = "received_at", nullable = false)
    private OffsetDateTime receivedAt = OffsetDateTime.now();

    protected TelemedicineEventEntity() {}

    public TelemedicineEventEntity(
            UUID eventId,
            UUID tenantId,
            String eventType,
            String facilityId,
            String payloadJson) {
        this.eventId = eventId;
        this.tenantId = tenantId;
        this.eventType = eventType;
        this.facilityId = facilityId;
        this.payloadJson = payloadJson;
    }

    public UUID getEventId() { return eventId; }
    public UUID getTenantId() { return tenantId; }
    public String getEventType() { return eventType; }
    public String getFacilityId() { return facilityId; }
    public String getPayloadJson() { return payloadJson; }
    public OffsetDateTime getReceivedAt() { return receivedAt; }
}
