package zw.gov.mohcc.impilo.msikaflow.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EnumType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import zw.gov.mohcc.impilo.msikaflow.domain.FulfillmentOrderStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "mf_fulfillment_orders")
public class FulfillmentOrderEntity {

    @Id
    @Column(name = "fulfillment_id", nullable = false, length = 26)
    private String fulfillmentId;

    @Column(name = "order_id", nullable = false, length = 26)
    private String orderId;

    @Column(name = "fulfillment_type", nullable = false, length = 30)
    private String fulfillmentType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private FulfillmentOrderStatus status = FulfillmentOrderStatus.PENDING;

    @Column(name = "provider_ref", length = 255)
    private String providerRef;

    @Column(name = "facility_id")
    private UUID facilityId;

    @Column(name = "vendor_id")
    private UUID vendorId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private String metadata;

    @Column(name = "staged_at")
    private OffsetDateTime stagedAt;
    @Column(name = "packed_at")
    private OffsetDateTime packedAt;
    @Column(name = "handed_off_at")
    private OffsetDateTime handedOffAt;
    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    public String getFulfillmentId() { return fulfillmentId; }
    public void setFulfillmentId(String fulfillmentId) { this.fulfillmentId = fulfillmentId; }
    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }
    public String getFulfillmentType() { return fulfillmentType; }
    public void setFulfillmentType(String fulfillmentType) { this.fulfillmentType = fulfillmentType; }
    public FulfillmentOrderStatus getStatus() { return status; }
    public void setStatus(FulfillmentOrderStatus status) { this.status = status; }
    public String getProviderRef() { return providerRef; }
    public void setProviderRef(String providerRef) { this.providerRef = providerRef; }
    public UUID getFacilityId() { return facilityId; }
    public void setFacilityId(UUID facilityId) { this.facilityId = facilityId; }
    public UUID getVendorId() { return vendorId; }
    public void setVendorId(UUID vendorId) { this.vendorId = vendorId; }
    public String getMetadata() { return metadata; }
    public void setMetadata(String metadata) { this.metadata = metadata; }
    public OffsetDateTime getStagedAt() { return stagedAt; }
    public void setStagedAt(OffsetDateTime stagedAt) { this.stagedAt = stagedAt; }
    public OffsetDateTime getPackedAt() { return packedAt; }
    public void setPackedAt(OffsetDateTime packedAt) { this.packedAt = packedAt; }
    public OffsetDateTime getHandedOffAt() { return handedOffAt; }
    public void setHandedOffAt(OffsetDateTime handedOffAt) { this.handedOffAt = handedOffAt; }
    public OffsetDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(OffsetDateTime completedAt) { this.completedAt = completedAt; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}

