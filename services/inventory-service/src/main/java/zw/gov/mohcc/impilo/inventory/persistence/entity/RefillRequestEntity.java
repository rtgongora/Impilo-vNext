package zw.gov.mohcc.impilo.inventory.persistence.entity;

import jakarta.persistence.*;
import zw.gov.mohcc.impilo.inventory.domain.RefillStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * A client/caregiver request to refill a household commodity.
 */
@Entity
@Table(name = "inv_refill_requests")
public class RefillRequestEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "refill_id", nullable = false)
    private UUID refillId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "client_id", nullable = false)
    private String clientId;

    @Column(name = "item_code")
    private String itemCode;

    @Column(name = "item_name", nullable = false)
    private String itemName;

    @Column(name = "qty_requested", nullable = false)
    private int qtyRequested;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private RefillStatus status = RefillStatus.REQUESTED;

    @Column(name = "preferred_facility_id")
    private UUID preferredFacilityId;

    @Column(name = "requested_by")
    private String requestedBy;

    @Column(name = "notes")
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @Column(name = "fulfilled_at")
    private OffsetDateTime fulfilledAt;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
        updatedAt = OffsetDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    public UUID getRefillId() { return refillId; }
    public void setRefillId(UUID refillId) { this.refillId = refillId; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public String getClientId() { return clientId; }
    public void setClientId(String clientId) { this.clientId = clientId; }

    public String getItemCode() { return itemCode; }
    public void setItemCode(String itemCode) { this.itemCode = itemCode; }

    public String getItemName() { return itemName; }
    public void setItemName(String itemName) { this.itemName = itemName; }

    public int getQtyRequested() { return qtyRequested; }
    public void setQtyRequested(int qtyRequested) { this.qtyRequested = qtyRequested; }

    public RefillStatus getStatus() { return status; }
    public void setStatus(RefillStatus status) { this.status = status; }

    public UUID getPreferredFacilityId() { return preferredFacilityId; }
    public void setPreferredFacilityId(UUID preferredFacilityId) { this.preferredFacilityId = preferredFacilityId; }

    public String getRequestedBy() { return requestedBy; }
    public void setRequestedBy(String requestedBy) { this.requestedBy = requestedBy; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }

    public OffsetDateTime getFulfilledAt() { return fulfilledAt; }
    public void setFulfilledAt(OffsetDateTime fulfilledAt) { this.fulfilledAt = fulfilledAt; }
}
