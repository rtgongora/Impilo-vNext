package zw.gov.mohcc.impilo.inventory.persistence.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;
import zw.gov.mohcc.impilo.inventory.domain.CapabilityMode;

/**
 * Defines the inventory management capability mode for a tenant or facility.
 * Controls whether inventory is managed internally, via an external adapter,
 * or in hybrid mode with reconciliation support.
 */
@Entity
@Table(name = "inv_capabilities")
public class CapabilityEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "cap_id", nullable = false)
    private UUID capId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "facility_id")
    private UUID facilityId;

    @Enumerated(EnumType.STRING)
    @Column(name = "capability_mode", nullable = false, length = 10)
    private CapabilityMode capabilityMode = CapabilityMode.INTERNAL;

    @Column(name = "config", columnDefinition = "jsonb")
    private String config;

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

    public UUID getCapId() { return capId; }
    public void setCapId(UUID capId) { this.capId = capId; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public UUID getFacilityId() { return facilityId; }
    public void setFacilityId(UUID facilityId) { this.facilityId = facilityId; }

    public CapabilityMode getCapabilityMode() { return capabilityMode; }
    public void setCapabilityMode(CapabilityMode capabilityMode) { this.capabilityMode = capabilityMode; }

    public String getConfig() { return config; }
    public void setConfig(String config) { this.config = config; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
