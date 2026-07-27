package zw.gov.mohcc.impilo.tuso.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * What a claimant may say they are to a facility, and what that claim demands of them (FCV-W4).
 *
 * <p>A closed vocabulary rather than free text, because the answer decides whether a professional
 * identifier must resolve before the claim can be filed.</p>
 */
@Entity
@Table(name = "facility_relationship_type", schema = "tuso")
public class FacilityRelationshipTypeEntity {

    @Id
    @Column(name = "code", length = 64)
    private String code;

    @Column(name = "label", nullable = false, length = 128)
    private String label;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    /** Regulated clinical or diagnostic leadership — a claim about professional standing. */
    @Column(name = "requires_provider_id", nullable = false)
    private boolean requiresProviderId = false;

    @Column(name = "requires_justification", nullable = false)
    private boolean requiresJustification = false;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder = 100;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public boolean isRequiresProviderId() { return requiresProviderId; }
    public void setRequiresProviderId(boolean requiresProviderId) { this.requiresProviderId = requiresProviderId; }

    public boolean isRequiresJustification() { return requiresJustification; }
    public void setRequiresJustification(boolean requiresJustification) { this.requiresJustification = requiresJustification; }

    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
