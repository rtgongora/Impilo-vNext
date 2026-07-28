package zw.gov.mohcc.impilo.madi.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

/** One coordinated blood-component release under an active MHP activation (madi V200, W8a). */
@Entity
@Table(name = "madi_mhp_pack")
public class MhpPackEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "pack_id")
    private UUID packId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "activation_id", nullable = false)
    private UUID activationId;

    @Column(name = "pack_number", nullable = false)
    private Integer packNumber;

    @Column(name = "component_type", nullable = false)
    private String componentType;

    @Column(name = "units_issued", nullable = false)
    private Integer unitsIssued;

    @Column(name = "blood_group")
    private String bloodGroup;

    @Column(name = "issued_by", nullable = false)
    private String issuedBy;

    @Column(name = "issued_at", nullable = false)
    private OffsetDateTime issuedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        if (issuedAt == null) issuedAt = now;
        if (createdAt == null) createdAt = now;
    }

    public UUID getPackId() { return packId; }
    public void setPackId(UUID packId) { this.packId = packId; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public UUID getActivationId() { return activationId; }
    public void setActivationId(UUID activationId) { this.activationId = activationId; }
    public Integer getPackNumber() { return packNumber; }
    public void setPackNumber(Integer packNumber) { this.packNumber = packNumber; }
    public String getComponentType() { return componentType; }
    public void setComponentType(String componentType) { this.componentType = componentType; }
    public Integer getUnitsIssued() { return unitsIssued; }
    public void setUnitsIssued(Integer unitsIssued) { this.unitsIssued = unitsIssued; }
    public String getBloodGroup() { return bloodGroup; }
    public void setBloodGroup(String bloodGroup) { this.bloodGroup = bloodGroup; }
    public String getIssuedBy() { return issuedBy; }
    public void setIssuedBy(String issuedBy) { this.issuedBy = issuedBy; }
    public OffsetDateTime getIssuedAt() { return issuedAt; }
    public void setIssuedAt(OffsetDateTime issuedAt) { this.issuedAt = issuedAt; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
