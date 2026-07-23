package zw.gov.mohcc.impilo.oros.persistence.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * A prescription line, owned by a specific immutable version (OF-B2).
 * Coded medication reference — never a free-text product (Vol II §8.1.5).
 */
@Entity
@Table(name = "oros_prescription_items")
public class PrescriptionItemEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "item_id")
    private UUID itemId;

    @Column(name = "prescription_version_id", nullable = false, length = 26)
    private String prescriptionVersionId;

    @Column(name = "medication_code", nullable = false, length = 64)
    private String medicationCode;

    @Column(name = "coding_system", nullable = false, length = 128)
    private String codingSystem = "http://www.whocc.no/atc";

    @Column(name = "display_name", length = 255)
    private String displayName;

    @Column(name = "dose", length = 64)
    private String dose;

    @Column(name = "route", length = 64)
    private String route;

    @Column(name = "frequency", length = 64)
    private String frequency;

    @Column(name = "duration_days")
    private Integer durationDays;

    @Column(name = "quantity", precision = 12, scale = 3)
    private BigDecimal quantity;

    @Column(name = "quantity_unit", length = 32)
    private String quantityUnit;

    @Column(name = "instructions", columnDefinition = "TEXT")
    private String instructions;

    @Column(name = "controlled", nullable = false)
    private boolean controlled;

    @Column(name = "substitution_permitted", nullable = false)
    private boolean substitutionPermitted = true;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    public UUID getItemId() { return itemId; }
    public void setItemId(UUID itemId) { this.itemId = itemId; }
    public String getPrescriptionVersionId() { return prescriptionVersionId; }
    public void setPrescriptionVersionId(String v) { this.prescriptionVersionId = v; }
    public String getMedicationCode() { return medicationCode; }
    public void setMedicationCode(String medicationCode) { this.medicationCode = medicationCode; }
    public String getCodingSystem() { return codingSystem; }
    public void setCodingSystem(String codingSystem) { this.codingSystem = codingSystem; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public String getDose() { return dose; }
    public void setDose(String dose) { this.dose = dose; }
    public String getRoute() { return route; }
    public void setRoute(String route) { this.route = route; }
    public String getFrequency() { return frequency; }
    public void setFrequency(String frequency) { this.frequency = frequency; }
    public Integer getDurationDays() { return durationDays; }
    public void setDurationDays(Integer durationDays) { this.durationDays = durationDays; }
    public BigDecimal getQuantity() { return quantity; }
    public void setQuantity(BigDecimal quantity) { this.quantity = quantity; }
    public String getQuantityUnit() { return quantityUnit; }
    public void setQuantityUnit(String quantityUnit) { this.quantityUnit = quantityUnit; }
    public String getInstructions() { return instructions; }
    public void setInstructions(String instructions) { this.instructions = instructions; }
    public boolean isControlled() { return controlled; }
    public void setControlled(boolean controlled) { this.controlled = controlled; }
    public boolean isSubstitutionPermitted() { return substitutionPermitted; }
    public void setSubstitutionPermitted(boolean substitutionPermitted) { this.substitutionPermitted = substitutionPermitted; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
