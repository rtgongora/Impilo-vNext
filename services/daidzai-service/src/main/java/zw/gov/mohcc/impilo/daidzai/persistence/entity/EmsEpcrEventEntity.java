package zw.gov.mohcc.impilo.daidzai.persistence.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * A single timed prehospital observation on an ePCR — vitals, GCS, an intervention, a medication,
 * or a note. The time-series that reconstructs the en-route clinical picture and feeds the ED
 * pre-arrival projection.
 */
@Entity
@Table(name = "dai_ems_epcr_event", schema = "daidzai")
public class EmsEpcrEventEntity {

    @Id @Column(name = "id") private UUID id;
    @Column(name = "tenant_id", nullable = false) private UUID tenantId;
    @Column(name = "epcr_id", nullable = false) private UUID epcrId;
    @Column(name = "mission_id", nullable = false) private UUID missionId;
    @Column(name = "event_type", nullable = false, length = 32) private String eventType;
    @Column(name = "channel", length = 48) private String channel;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload_json", columnDefinition = "jsonb")
    private String payloadJson;

    @Column(name = "recorded_at", nullable = false) private OffsetDateTime recordedAt;
    @Column(name = "created_at", nullable = false) private OffsetDateTime createdAt;

    @PrePersist void onCreate() {
        if (id == null) id = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        if (createdAt == null) createdAt = now;
        if (recordedAt == null) recordedAt = now;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID v) { this.tenantId = v; }
    public UUID getEpcrId() { return epcrId; }
    public void setEpcrId(UUID v) { this.epcrId = v; }
    public UUID getMissionId() { return missionId; }
    public void setMissionId(UUID v) { this.missionId = v; }
    public String getEventType() { return eventType; }
    public void setEventType(String v) { this.eventType = v; }
    public String getChannel() { return channel; }
    public void setChannel(String v) { this.channel = v; }
    public String getPayloadJson() { return payloadJson; }
    public void setPayloadJson(String v) { this.payloadJson = v; }
    public OffsetDateTime getRecordedAt() { return recordedAt; }
    public void setRecordedAt(OffsetDateTime v) { this.recordedAt = v; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
