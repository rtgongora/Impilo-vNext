package zw.gov.mohcc.impilo.inventory.persistence.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Represents an inventory item in the product catalogue.
 * Items are tenant-scoped with a unique (tenant_id, item_code) constraint.
 * ZIBO terminology references are stored as JSONB for flexible cross-referencing.
 */
@Entity
@Table(name = "inv_items",
       uniqueConstraints = @UniqueConstraint(columnNames = {"tenant_id", "item_code"}))
public class ItemEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "item_id", nullable = false)
    private UUID itemId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "item_code", nullable = false)
    private String itemCode;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "uom")
    private String uom;

    @Column(name = "barcode")
    private String barcode;

    @Column(name = "category")
    private String category;

    @Column(name = "controlled", nullable = false)
    private boolean controlled = false;

    @Column(name = "zibo_refs", columnDefinition = "jsonb")
    private String ziboRefs;

    @Column(name = "active", nullable = false)
    private boolean active = true;

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

    public UUID getItemId() { return itemId; }
    public void setItemId(UUID itemId) { this.itemId = itemId; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public String getItemCode() { return itemCode; }
    public void setItemCode(String itemCode) { this.itemCode = itemCode; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getUom() { return uom; }
    public void setUom(String uom) { this.uom = uom; }

    public String getBarcode() { return barcode; }
    public void setBarcode(String barcode) { this.barcode = barcode; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public boolean isControlled() { return controlled; }
    public void setControlled(boolean controlled) { this.controlled = controlled; }

    public String getZiboRefs() { return ziboRefs; }
    public void setZiboRefs(String ziboRefs) { this.ziboRefs = ziboRefs; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
