package zw.gov.mohcc.impilo.pharmacy.persistence.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;
import zw.gov.mohcc.impilo.pharmacy.domain.DispenseItemStatus;

/**
 * Represents an individual line item within a dispense order.
 * Tracks the drug, quantities, batch, and pick/dispense workflow.
 */
@Entity
@Table(name = "rx_dispense_items")
public class DispenseItemEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "item_id", nullable = false)
    private UUID itemId;

    @Column(name = "dispense_order_id", nullable = false)
    private UUID dispenseOrderId;

    @Column(name = "drug_code", nullable = false)
    private String drugCode;

    @Column(name = "drug_display")
    private String drugDisplay;

    @Column(name = "qty_requested", nullable = false)
    private int qtyRequested;

    @Column(name = "qty_dispensed")
    private int qtyDispensed;

    @Column(name = "qty_remaining")
    private int qtyRemaining;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private DispenseItemStatus status = DispenseItemStatus.PENDING;

    @Column(name = "batch_number")
    private String batchNumber;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @Column(name = "barcode")
    private String barcode;

    @Column(name = "substitution", columnDefinition = "jsonb")
    private String substitution;

    @Column(name = "instructions", columnDefinition = "TEXT")
    private String instructions;

    @Column(name = "specimen_type")
    private String specimenType;

    @Column(name = "body_site")
    private String bodySite;

    @Column(name = "picked_by")
    private String pickedBy;

    @Column(name = "picked_at")
    private OffsetDateTime pickedAt;

    @Column(name = "dispensed_by")
    private String dispensedBy;

    @Column(name = "dispensed_at")
    private OffsetDateTime dispensedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
    }

    // Getters and setters

    public UUID getItemId() { return itemId; }
    public void setItemId(UUID itemId) { this.itemId = itemId; }

    public UUID getDispenseOrderId() { return dispenseOrderId; }
    public void setDispenseOrderId(UUID dispenseOrderId) { this.dispenseOrderId = dispenseOrderId; }

    public String getDrugCode() { return drugCode; }
    public void setDrugCode(String drugCode) { this.drugCode = drugCode; }

    public String getDrugDisplay() { return drugDisplay; }
    public void setDrugDisplay(String drugDisplay) { this.drugDisplay = drugDisplay; }

    public int getQtyRequested() { return qtyRequested; }
    public void setQtyRequested(int qtyRequested) { this.qtyRequested = qtyRequested; }

    public int getQtyDispensed() { return qtyDispensed; }
    public void setQtyDispensed(int qtyDispensed) { this.qtyDispensed = qtyDispensed; }

    public int getQtyRemaining() { return qtyRemaining; }
    public void setQtyRemaining(int qtyRemaining) { this.qtyRemaining = qtyRemaining; }

    public DispenseItemStatus getStatus() { return status; }
    public void setStatus(DispenseItemStatus status) { this.status = status; }

    public String getBatchNumber() { return batchNumber; }
    public void setBatchNumber(String batchNumber) { this.batchNumber = batchNumber; }

    public LocalDate getExpiryDate() { return expiryDate; }
    public void setExpiryDate(LocalDate expiryDate) { this.expiryDate = expiryDate; }

    public String getBarcode() { return barcode; }
    public void setBarcode(String barcode) { this.barcode = barcode; }

    public String getSubstitution() { return substitution; }
    public void setSubstitution(String substitution) { this.substitution = substitution; }

    public String getInstructions() { return instructions; }
    public void setInstructions(String instructions) { this.instructions = instructions; }

    public String getSpecimenType() { return specimenType; }
    public void setSpecimenType(String specimenType) { this.specimenType = specimenType; }

    public String getBodySite() { return bodySite; }
    public void setBodySite(String bodySite) { this.bodySite = bodySite; }

    public String getPickedBy() { return pickedBy; }
    public void setPickedBy(String pickedBy) { this.pickedBy = pickedBy; }

    public OffsetDateTime getPickedAt() { return pickedAt; }
    public void setPickedAt(OffsetDateTime pickedAt) { this.pickedAt = pickedAt; }

    public String getDispensedBy() { return dispensedBy; }
    public void setDispensedBy(String dispensedBy) { this.dispensedBy = dispensedBy; }

    public OffsetDateTime getDispensedAt() { return dispensedAt; }
    public void setDispensedAt(OffsetDateTime dispensedAt) { this.dispensedAt = dispensedAt; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
