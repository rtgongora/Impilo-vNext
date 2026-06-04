package zw.gov.mohcc.impilo.nhume.domain;

import jakarta.persistence.Column;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "nhume_delivery_audit_events")
public class DeliveryAuditEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "delivery_id")
    private UUID deliveryId;

    @Column(name = "aggregate_type", nullable = false, length = 64)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false, length = 255)
    private String aggregateId;

    @Column(name = "action", nullable = false, length = 64)
    private String action;

    @Column(name = "actor_id", length = 255)
    private String actorId;

    @Column(name = "actor_type", length = 48)
    private String actorType;

    @Column(name = "purpose_of_use", length = 48)
    private String purposeOfUse;

    @Column(name = "correlation_id")
    private UUID correlationId;

    @Column(name = "facility_id", length = 255)
    private String facilityId;

    @Column(name = "workspace_id", length = 255)
    private String workspaceId;

    @Column(name = "shift_id", length = 255)
    private String shiftId;

    @Column(name = "device_fingerprint", length = 255)
    private String deviceFingerprint;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata_json", nullable = false, columnDefinition = "jsonb")
    private String metadataJson = "{}";

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    public DeliveryAuditEventEntity() {}

    public Long getId() { return id; }
    public UUID getDeliveryId() { return deliveryId; }
    public void setDeliveryId(UUID v) { this.deliveryId = v; }
    public String getAggregateType() { return aggregateType; }
    public void setAggregateType(String v) { this.aggregateType = v; }
    public String getAggregateId() { return aggregateId; }
    public void setAggregateId(String v) { this.aggregateId = v; }
    public String getAction() { return action; }
    public void setAction(String v) { this.action = v; }
    public String getActorId() { return actorId; }
    public void setActorId(String v) { this.actorId = v; }
    public String getActorType() { return actorType; }
    public void setActorType(String v) { this.actorType = v; }
    public String getPurposeOfUse() { return purposeOfUse; }
    public void setPurposeOfUse(String v) { this.purposeOfUse = v; }
    public UUID getCorrelationId() { return correlationId; }
    public void setCorrelationId(UUID v) { this.correlationId = v; }
    public String getFacilityId() { return facilityId; }
    public void setFacilityId(String v) { this.facilityId = v; }
    public String getWorkspaceId() { return workspaceId; }
    public void setWorkspaceId(String v) { this.workspaceId = v; }
    public String getShiftId() { return shiftId; }
    public void setShiftId(String v) { this.shiftId = v; }
    public String getDeviceFingerprint() { return deviceFingerprint; }
    public void setDeviceFingerprint(String v) { this.deviceFingerprint = v; }
    public String getMetadataJson() { return metadataJson; }
    public void setMetadataJson(String v) { this.metadataJson = v; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime v) { this.createdAt = v; }
}
