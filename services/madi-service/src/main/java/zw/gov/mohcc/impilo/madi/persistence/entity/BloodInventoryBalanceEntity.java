package zw.gov.mohcc.impilo.madi.persistence.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "blood_inventory_balances", schema = "madi")
public class BloodInventoryBalanceEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "balance_id", nullable = false, unique = true)
    private UUID balanceId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

@Column(name = "blood_bank_id")
    private UUID bloodBankId;

@Column(name = "inventory_item_code", nullable = false)
    private String inventoryItemCode;

@Column(name = "blood_group", nullable = false)
    private String bloodGroup;

@Column(name = "component_type", nullable = false)
    private String componentType;

@Column(name = "quantity_on_hand")
    private Integer quantityOnHand;

@Column(name = "quantity_reserved")
    private Integer quantityReserved;

@Column(name = "status")
    private String status;

@Column(name = "facility_id")
    private UUID facilityId;

@Column(name = "jurisdiction")
    private String jurisdiction;

@Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (balanceId == null) {
            balanceId = UUID.randomUUID();
        }
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public UUID getBalanceId() { return balanceId; }
    public void setBalanceId(UUID balanceId) { this.balanceId = balanceId; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public UUID getBloodBankId() { return bloodBankId; }
    public void setBloodBankId(UUID bloodBankId) { this.bloodBankId = bloodBankId; }

    public String getInventoryItemCode() { return inventoryItemCode; }
    public void setInventoryItemCode(String inventoryItemCode) { this.inventoryItemCode = inventoryItemCode; }

    public String getBloodGroup() { return bloodGroup; }
    public void setBloodGroup(String bloodGroup) { this.bloodGroup = bloodGroup; }

    public String getComponentType() { return componentType; }
    public void setComponentType(String componentType) { this.componentType = componentType; }

    public Integer getQuantityOnHand() { return quantityOnHand; }
    public void setQuantityOnHand(Integer quantityOnHand) { this.quantityOnHand = quantityOnHand; }

    public Integer getQuantityReserved() { return quantityReserved; }
    public void setQuantityReserved(Integer quantityReserved) { this.quantityReserved = quantityReserved; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public UUID getFacilityId() { return facilityId; }
    public void setFacilityId(UUID facilityId) { this.facilityId = facilityId; }

    public String getJurisdiction() { return jurisdiction; }
    public void setJurisdiction(String jurisdiction) { this.jurisdiction = jurisdiction; }

    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }

}