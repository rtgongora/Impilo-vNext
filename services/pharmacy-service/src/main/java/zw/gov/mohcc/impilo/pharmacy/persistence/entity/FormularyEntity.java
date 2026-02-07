package zw.gov.mohcc.impilo.pharmacy.persistence.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Formulary entry controlling which drugs are allowed at a tenant or facility level.
 * When facilityId is null, the rule applies tenant-wide.
 */
@Entity
@Table(name = "rx_formulary")
public class FormularyEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "formulary_id", nullable = false)
    private UUID formularyId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "facility_id")
    private UUID facilityId;

    @Column(name = "drug_code", nullable = false)
    private String drugCode;

    @Column(name = "drug_display")
    private String drugDisplay;

    @Column(name = "allowed", nullable = false)
    private boolean allowed;

    @Column(name = "constraints", columnDefinition = "jsonb")
    private String constraints;

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

    public UUID getFormularyId() { return formularyId; }
    public void setFormularyId(UUID formularyId) { this.formularyId = formularyId; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public UUID getFacilityId() { return facilityId; }
    public void setFacilityId(UUID facilityId) { this.facilityId = facilityId; }

    public String getDrugCode() { return drugCode; }
    public void setDrugCode(String drugCode) { this.drugCode = drugCode; }

    public String getDrugDisplay() { return drugDisplay; }
    public void setDrugDisplay(String drugDisplay) { this.drugDisplay = drugDisplay; }

    public boolean isAllowed() { return allowed; }
    public void setAllowed(boolean allowed) { this.allowed = allowed; }

    public String getConstraints() { return constraints; }
    public void setConstraints(String constraints) { this.constraints = constraints; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
