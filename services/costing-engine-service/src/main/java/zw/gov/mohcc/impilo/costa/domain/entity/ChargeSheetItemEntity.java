package zw.gov.mohcc.impilo.costa.domain.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "costa_charge_sheet_items")
public class ChargeSheetItemEntity {

    @Id
    @Column(name = "charge_sheet_item_id", length = 26)
    private String chargeSheetItemId;

    @Column(name = "charge_sheet_id", nullable = false, length = 26)
    private String chargeSheetId;

    @Column(name = "item_category", nullable = false, length = 120)
    private String itemCategory;

    @Column(name = "item_name", nullable = false, length = 400)
    private String itemName;

    @Column(name = "item_code", length = 80)
    private String itemCode;

    @Column(name = "quantity", nullable = false, precision = 14, scale = 4)
    private BigDecimal quantity = BigDecimal.ONE;

    @Column(name = "unit_of_measure", length = 40)
    private String unitOfMeasure;

    @Column(name = "chargeable", nullable = false)
    private boolean chargeable = true;

    @Column(name = "duplicate_resolution", length = 40)
    private String duplicateResolution;

    @Column(name = "clinical_note", columnDefinition = "text")
    private String clinicalNote;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", nullable = false, columnDefinition = "jsonb")
    private String metadata = "{}";

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }

    public String getChargeSheetItemId() {
        return chargeSheetItemId;
    }

    public void setChargeSheetItemId(String chargeSheetItemId) {
        this.chargeSheetItemId = chargeSheetItemId;
    }

    public String getChargeSheetId() {
        return chargeSheetId;
    }

    public void setChargeSheetId(String chargeSheetId) {
        this.chargeSheetId = chargeSheetId;
    }

    public String getItemCategory() {
        return itemCategory;
    }

    public void setItemCategory(String itemCategory) {
        this.itemCategory = itemCategory;
    }

    public String getItemName() {
        return itemName;
    }

    public void setItemName(String itemName) {
        this.itemName = itemName;
    }

    public String getItemCode() {
        return itemCode;
    }

    public void setItemCode(String itemCode) {
        this.itemCode = itemCode;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public void setQuantity(BigDecimal quantity) {
        this.quantity = quantity;
    }

    public String getUnitOfMeasure() {
        return unitOfMeasure;
    }

    public void setUnitOfMeasure(String unitOfMeasure) {
        this.unitOfMeasure = unitOfMeasure;
    }

    public boolean isChargeable() {
        return chargeable;
    }

    public void setChargeable(boolean chargeable) {
        this.chargeable = chargeable;
    }

    public String getDuplicateResolution() {
        return duplicateResolution;
    }

    public void setDuplicateResolution(String duplicateResolution) {
        this.duplicateResolution = duplicateResolution;
    }

    public String getClinicalNote() {
        return clinicalNote;
    }

    public void setClinicalNote(String clinicalNote) {
        this.clinicalNote = clinicalNote;
    }

    public String getMetadata() {
        return metadata;
    }

    public void setMetadata(String metadata) {
        this.metadata = metadata;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
