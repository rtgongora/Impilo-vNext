package zw.gov.mohcc.impilo.experience.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "inventory_items")
public class InventoryItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(name = "facility_id", nullable = false)
    private UUID facilityId;

    @Column(name = "product_code", nullable = false)
    private String productCode;

    @Column(name = "product_name", nullable = false)
    private String productName;

    private String category;

    @Column(name = "quantity_on_hand", nullable = false)
    private int quantityOnHand;

    @Column(name = "reorder_level", nullable = false)
    private int reorderLevel;

    private String unit;

    private String status;

    @Column(name = "last_counted_at")
    private OffsetDateTime lastCountedAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected InventoryItem() {}

    public UUID getId() { return id; }
    public String getTenantId() { return tenantId; }
    public UUID getFacilityId() { return facilityId; }
    public String getProductCode() { return productCode; }
    public String getProductName() { return productName; }
    public String getCategory() { return category; }
    public int getQuantityOnHand() { return quantityOnHand; }
    public int getReorderLevel() { return reorderLevel; }
    public String getUnit() { return unit; }
    public String getStatus() { return status; }
    public OffsetDateTime getLastCountedAt() { return lastCountedAt; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
