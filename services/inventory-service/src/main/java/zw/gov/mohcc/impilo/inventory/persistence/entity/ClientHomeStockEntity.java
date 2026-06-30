package zw.gov.mohcc.impilo.inventory.persistence.entity;

import jakarta.persistence.*;
import zw.gov.mohcc.impilo.inventory.domain.HomeStockSource;
import zw.gov.mohcc.impilo.inventory.domain.HomeStockStatus;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * A client/caregiver household stock item (e.g. chronic medicines, glucometer
 * strips, inhalers). Personal health data — access governed by Tshepo/Vito.
 */
@Entity
@Table(name = "inv_client_home_stock")
public class ClientHomeStockEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "home_stock_id", nullable = false)
    private UUID homeStockId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "client_id", nullable = false)
    private String clientId;

    @Column(name = "item_code")
    private String itemCode;

    @Column(name = "item_name", nullable = false)
    private String itemName;

    @Column(name = "qty", nullable = false)
    private int qty;

    @Column(name = "uom")
    private String uom;

    @Column(name = "batch_number")
    private String batchNumber;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 20)
    private HomeStockSource source = HomeStockSource.SELF;

    @Column(name = "managed_by_caregiver")
    private String managedByCaregiver;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private HomeStockStatus status = HomeStockStatus.ACTIVE;

    @Column(name = "notes")
    private String notes;

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

    public UUID getHomeStockId() { return homeStockId; }
    public void setHomeStockId(UUID homeStockId) { this.homeStockId = homeStockId; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public String getClientId() { return clientId; }
    public void setClientId(String clientId) { this.clientId = clientId; }

    public String getItemCode() { return itemCode; }
    public void setItemCode(String itemCode) { this.itemCode = itemCode; }

    public String getItemName() { return itemName; }
    public void setItemName(String itemName) { this.itemName = itemName; }

    public int getQty() { return qty; }
    public void setQty(int qty) { this.qty = qty; }

    public String getUom() { return uom; }
    public void setUom(String uom) { this.uom = uom; }

    public String getBatchNumber() { return batchNumber; }
    public void setBatchNumber(String batchNumber) { this.batchNumber = batchNumber; }

    public LocalDate getExpiryDate() { return expiryDate; }
    public void setExpiryDate(LocalDate expiryDate) { this.expiryDate = expiryDate; }

    public HomeStockSource getSource() { return source; }
    public void setSource(HomeStockSource source) { this.source = source; }

    public String getManagedByCaregiver() { return managedByCaregiver; }
    public void setManagedByCaregiver(String managedByCaregiver) { this.managedByCaregiver = managedByCaregiver; }

    public HomeStockStatus getStatus() { return status; }
    public void setStatus(HomeStockStatus status) { this.status = status; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
