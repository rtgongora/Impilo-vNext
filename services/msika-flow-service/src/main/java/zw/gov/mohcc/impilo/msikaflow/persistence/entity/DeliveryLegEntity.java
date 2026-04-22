package zw.gov.mohcc.impilo.msikaflow.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import zw.gov.mohcc.impilo.msikaflow.domain.DeliveryMode;
import zw.gov.mohcc.impilo.msikaflow.domain.DeliveryStatus;

import jakarta.persistence.Enumerated;
import jakarta.persistence.EnumType;
import java.time.OffsetDateTime;

@Entity
@Table(name = "mf_delivery_legs")
public class DeliveryLegEntity {

    @Id
    @Column(name = "leg_id", nullable = false, length = 26)
    private String legId;

    @Column(name = "plan_id", nullable = false, length = 26)
    private String planId;

    @Column(name = "sequence_number", nullable = false)
    private int sequenceNumber;

    @Column(name = "leg_type", nullable = false, length = 40)
    private String legType = "DIRECT";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "origin", nullable = false, columnDefinition = "jsonb")
    private String originJson = "{}";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "destination", nullable = false, columnDefinition = "jsonb")
    private String destinationJson = "{}";

    @Enumerated(EnumType.STRING)
    @Column(name = "mode", nullable = false, length = 40)
    private DeliveryMode mode;

    @Column(name = "assigned_provider_id", length = 26)
    private String assignedProviderId;

    @Column(name = "assigned_asset_id", length = 26)
    private String assignedAssetId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private DeliveryStatus status = DeliveryStatus.PLANNED;

    @Column(name = "estimated_departure")
    private OffsetDateTime estimatedDeparture;
    @Column(name = "estimated_arrival")
    private OffsetDateTime estimatedArrival;
    @Column(name = "actual_departure")
    private OffsetDateTime actualDeparture;
    @Column(name = "actual_arrival")
    private OffsetDateTime actualArrival;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private String metadataJson = "{}";

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        createdAt = now;
        updatedAt = now;
        if (originJson == null) originJson = "{}";
        if (destinationJson == null) destinationJson = "{}";
        if (metadataJson == null) metadataJson = "{}";
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    public String getLegId() { return legId; }
    public void setLegId(String legId) { this.legId = legId; }
    public String getPlanId() { return planId; }
    public void setPlanId(String planId) { this.planId = planId; }
    public int getSequenceNumber() { return sequenceNumber; }
    public void setSequenceNumber(int sequenceNumber) { this.sequenceNumber = sequenceNumber; }
    public String getLegType() { return legType; }
    public void setLegType(String legType) { this.legType = legType; }
    public String getOriginJson() { return originJson; }
    public void setOriginJson(String originJson) { this.originJson = originJson; }
    public String getDestinationJson() { return destinationJson; }
    public void setDestinationJson(String destinationJson) { this.destinationJson = destinationJson; }
    public DeliveryMode getMode() { return mode; }
    public void setMode(DeliveryMode mode) { this.mode = mode; }
    public String getAssignedProviderId() { return assignedProviderId; }
    public void setAssignedProviderId(String assignedProviderId) { this.assignedProviderId = assignedProviderId; }
    public String getAssignedAssetId() { return assignedAssetId; }
    public void setAssignedAssetId(String assignedAssetId) { this.assignedAssetId = assignedAssetId; }
    public DeliveryStatus getStatus() { return status; }
    public void setStatus(DeliveryStatus status) { this.status = status; }
    public OffsetDateTime getEstimatedDeparture() { return estimatedDeparture; }
    public void setEstimatedDeparture(OffsetDateTime estimatedDeparture) { this.estimatedDeparture = estimatedDeparture; }
    public OffsetDateTime getEstimatedArrival() { return estimatedArrival; }
    public void setEstimatedArrival(OffsetDateTime estimatedArrival) { this.estimatedArrival = estimatedArrival; }
    public OffsetDateTime getActualDeparture() { return actualDeparture; }
    public void setActualDeparture(OffsetDateTime actualDeparture) { this.actualDeparture = actualDeparture; }
    public OffsetDateTime getActualArrival() { return actualArrival; }
    public void setActualArrival(OffsetDateTime actualArrival) { this.actualArrival = actualArrival; }
    public String getMetadataJson() { return metadataJson; }
    public void setMetadataJson(String metadataJson) { this.metadataJson = metadataJson; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}

