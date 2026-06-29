package zw.gov.mohcc.impilo.inventory.persistence.entity;

import jakarta.persistence.*;
import zw.gov.mohcc.impilo.inventory.domain.ReservationStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * A soft hold on stock for a downstream workflow (dispense, procedure,
 * transfer, order). Active reservations reduce available quantity without
 * mutating the on-hand ledger projection.
 */
@Entity
@Table(name = "inv_stock_reservations")
public class StockReservationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "reservation_id", nullable = false)
    private UUID reservationId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "facility_id", nullable = false)
    private UUID facilityId;

    @Column(name = "store_id", nullable = false)
    private UUID storeId;

    @Column(name = "item_code", nullable = false)
    private String itemCode;

    @Column(name = "batch_lot_id")
    private UUID batchLotId;

    @Column(name = "qty", nullable = false)
    private int qty;

    @Column(name = "uom")
    private String uom;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ReservationStatus status = ReservationStatus.ACTIVE;

    @Column(name = "ref_type")
    private String refType;

    @Column(name = "ref_id")
    private String refId;

    @Column(name = "reserved_by")
    private String reservedBy;

    @Column(name = "reason")
    private String reason;

    @Column(name = "expires_at")
    private OffsetDateTime expiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
        updatedAt = OffsetDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    // Getters and setters

    public UUID getReservationId() { return reservationId; }
    public void setReservationId(UUID reservationId) { this.reservationId = reservationId; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public UUID getFacilityId() { return facilityId; }
    public void setFacilityId(UUID facilityId) { this.facilityId = facilityId; }

    public UUID getStoreId() { return storeId; }
    public void setStoreId(UUID storeId) { this.storeId = storeId; }

    public String getItemCode() { return itemCode; }
    public void setItemCode(String itemCode) { this.itemCode = itemCode; }

    public UUID getBatchLotId() { return batchLotId; }
    public void setBatchLotId(UUID batchLotId) { this.batchLotId = batchLotId; }

    public int getQty() { return qty; }
    public void setQty(int qty) { this.qty = qty; }

    public String getUom() { return uom; }
    public void setUom(String uom) { this.uom = uom; }

    public ReservationStatus getStatus() { return status; }
    public void setStatus(ReservationStatus status) { this.status = status; }

    public String getRefType() { return refType; }
    public void setRefType(String refType) { this.refType = refType; }

    public String getRefId() { return refId; }
    public void setRefId(String refId) { this.refId = refId; }

    public String getReservedBy() { return reservedBy; }
    public void setReservedBy(String reservedBy) { this.reservedBy = reservedBy; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public OffsetDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(OffsetDateTime expiresAt) { this.expiresAt = expiresAt; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
