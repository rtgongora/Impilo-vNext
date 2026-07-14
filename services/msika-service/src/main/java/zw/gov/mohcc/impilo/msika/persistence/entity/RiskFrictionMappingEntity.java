package zw.gov.mohcc.impilo.msika.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Governed risk-class → friction mapping (seeded in V004). Read-mostly
 * reference truth: publish gates in Msika enforce the seller-context flags;
 * msika-flow consumes friction_level / max_qty_per_order at order time.
 */
@Entity
@Table(name = "msika_risk_friction_mapping")
public class RiskFrictionMappingEntity {

    @Id
    @Column(name = "risk_classification", length = 32)
    private String riskClassification;

    @Column(name = "friction_level", nullable = false, length = 32)
    private String frictionLevel;

    @Column(name = "requires_provider", nullable = false)
    private boolean requiresProvider;

    @Column(name = "requires_facility", nullable = false)
    private boolean requiresFacility;

    @Column(name = "max_qty_per_order")
    private Integer maxQtyPerOrder;

    /**
     * V009: when TRUE, a recognised licensed provider in good standing may
     * self-satisfy the prescriber-standing requirement for their own purchase
     * of items in this class (identity checks unchanged). Governed: default FALSE.
     */
    @Column(name = "professional_self_authorizable", nullable = false)
    private boolean professionalSelfAuthorizable;

    @Column(name = "notes")
    private String notes;

    public String getRiskClassification() { return riskClassification; }
    public void setRiskClassification(String riskClassification) { this.riskClassification = riskClassification; }
    public String getFrictionLevel() { return frictionLevel; }
    public void setFrictionLevel(String frictionLevel) { this.frictionLevel = frictionLevel; }
    public boolean isRequiresProvider() { return requiresProvider; }
    public void setRequiresProvider(boolean requiresProvider) { this.requiresProvider = requiresProvider; }
    public boolean isRequiresFacility() { return requiresFacility; }
    public void setRequiresFacility(boolean requiresFacility) { this.requiresFacility = requiresFacility; }
    public Integer getMaxQtyPerOrder() { return maxQtyPerOrder; }
    public void setMaxQtyPerOrder(Integer maxQtyPerOrder) { this.maxQtyPerOrder = maxQtyPerOrder; }
    public boolean isProfessionalSelfAuthorizable() { return professionalSelfAuthorizable; }
    public void setProfessionalSelfAuthorizable(boolean professionalSelfAuthorizable) { this.professionalSelfAuthorizable = professionalSelfAuthorizable; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
}
