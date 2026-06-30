package zw.gov.mohcc.impilo.inventory.persistence.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
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

    @JdbcTypeCode(SqlTypes.JSON)


    @Column(name = "zibo_refs", columnDefinition = "jsonb")
    private String ziboRefs;

    // --- Dura catalogue enrichment (V005) ---

    @Column(name = "category_id")
    private UUID categoryId;

    @Column(name = "subcategory")
    private String subcategory;

    @Column(name = "generic_name")
    private String genericName;

    @Column(name = "brand_name")
    private String brandName;

    @Column(name = "form")
    private String form;

    @Column(name = "strength")
    private String strength;

    @Column(name = "pack_size")
    private String packSize;

    @Column(name = "stocking_unit")
    private String stockingUnit;

    @Column(name = "dispensing_unit")
    private String dispensingUnit;

    @Column(name = "programme_area")
    private String programmeArea;

    @Column(name = "cold_chain_required", nullable = false)
    private boolean coldChainRequired = false;

    @Column(name = "temperature_min")
    private Double temperatureMin;

    @Column(name = "temperature_max")
    private Double temperatureMax;

    @Column(name = "batch_tracking_required", nullable = false)
    private boolean batchTrackingRequired = false;

    @Column(name = "expiry_tracking_required", nullable = false)
    private boolean expiryTrackingRequired = false;

    @Column(name = "serial_tracking_required", nullable = false)
    private boolean serialTrackingRequired = false;

    @Column(name = "substitution_group")
    private String substitutionGroup;

    @Column(name = "essential_medicine", nullable = false)
    private boolean essentialMedicine = false;

    @Column(name = "regulatory_status")
    private String regulatoryStatus;

    @Column(name = "gtin")
    private String gtin;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "external_codes", columnDefinition = "jsonb")
    private String externalCodes;

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

    // --- Dura catalogue enrichment accessors ---

    public UUID getCategoryId() { return categoryId; }
    public void setCategoryId(UUID categoryId) { this.categoryId = categoryId; }

    public String getSubcategory() { return subcategory; }
    public void setSubcategory(String subcategory) { this.subcategory = subcategory; }

    public String getGenericName() { return genericName; }
    public void setGenericName(String genericName) { this.genericName = genericName; }

    public String getBrandName() { return brandName; }
    public void setBrandName(String brandName) { this.brandName = brandName; }

    public String getForm() { return form; }
    public void setForm(String form) { this.form = form; }

    public String getStrength() { return strength; }
    public void setStrength(String strength) { this.strength = strength; }

    public String getPackSize() { return packSize; }
    public void setPackSize(String packSize) { this.packSize = packSize; }

    public String getStockingUnit() { return stockingUnit; }
    public void setStockingUnit(String stockingUnit) { this.stockingUnit = stockingUnit; }

    public String getDispensingUnit() { return dispensingUnit; }
    public void setDispensingUnit(String dispensingUnit) { this.dispensingUnit = dispensingUnit; }

    public String getProgrammeArea() { return programmeArea; }
    public void setProgrammeArea(String programmeArea) { this.programmeArea = programmeArea; }

    public boolean isColdChainRequired() { return coldChainRequired; }
    public void setColdChainRequired(boolean coldChainRequired) { this.coldChainRequired = coldChainRequired; }

    public Double getTemperatureMin() { return temperatureMin; }
    public void setTemperatureMin(Double temperatureMin) { this.temperatureMin = temperatureMin; }

    public Double getTemperatureMax() { return temperatureMax; }
    public void setTemperatureMax(Double temperatureMax) { this.temperatureMax = temperatureMax; }

    public boolean isBatchTrackingRequired() { return batchTrackingRequired; }
    public void setBatchTrackingRequired(boolean batchTrackingRequired) { this.batchTrackingRequired = batchTrackingRequired; }

    public boolean isExpiryTrackingRequired() { return expiryTrackingRequired; }
    public void setExpiryTrackingRequired(boolean expiryTrackingRequired) { this.expiryTrackingRequired = expiryTrackingRequired; }

    public boolean isSerialTrackingRequired() { return serialTrackingRequired; }
    public void setSerialTrackingRequired(boolean serialTrackingRequired) { this.serialTrackingRequired = serialTrackingRequired; }

    public String getSubstitutionGroup() { return substitutionGroup; }
    public void setSubstitutionGroup(String substitutionGroup) { this.substitutionGroup = substitutionGroup; }

    public boolean isEssentialMedicine() { return essentialMedicine; }
    public void setEssentialMedicine(boolean essentialMedicine) { this.essentialMedicine = essentialMedicine; }

    public String getRegulatoryStatus() { return regulatoryStatus; }
    public void setRegulatoryStatus(String regulatoryStatus) { this.regulatoryStatus = regulatoryStatus; }

    public String getGtin() { return gtin; }
    public void setGtin(String gtin) { this.gtin = gtin; }

    public String getExternalCodes() { return externalCodes; }
    public void setExternalCodes(String externalCodes) { this.externalCodes = externalCodes; }
}
