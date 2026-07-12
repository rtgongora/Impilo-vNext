package zw.gov.mohcc.impilo.msikaflow.persistence.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.OffsetDateTime;
import java.util.UUID;
import zw.gov.mohcc.impilo.msikaflow.domain.VendorStatus;
import zw.gov.mohcc.impilo.msikaflow.domain.VendorType;

/**
 * Represents a vendor (pharmacy, lab, supplier, etc.) enrolled in the marketplace.
 * The vendorId is a ULID string assigned by the application layer.
 */
@Entity
@Table(name = "mf_vendor_profiles")
public class VendorProfileEntity {

    @Id
    @Column(name = "vendor_id", nullable = false, length = 26)
    private String vendorId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private VendorType type;

    @Column(name = "name", nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private VendorStatus status;

    @JdbcTypeCode(SqlTypes.JSON)


    @Column(name = "coverage", columnDefinition = "jsonb")
    private String coverage;

    /**
     * JWT principal (actor id) bound to this vendor — the BFF's vendor-me seam.
     * Nullable; set via the ops-gated bind-actor endpoint.
     */
    @Column(name = "actor_binding", length = 128)
    private String actorBinding;

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

    public String getVendorId() {
        return vendorId;
    }

    public void setVendorId(String vendorId) {
        this.vendorId = vendorId;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }

    public VendorType getType() {
        return type;
    }

    public void setType(VendorType type) {
        this.type = type;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public VendorStatus getStatus() {
        return status;
    }

    public void setStatus(VendorStatus status) {
        this.status = status;
    }

    public String getCoverage() {
        return coverage;
    }

    public void setCoverage(String coverage) {
        this.coverage = coverage;
    }

    public String getActorBinding() {
        return actorBinding;
    }

    public void setActorBinding(String actorBinding) {
        this.actorBinding = actorBinding;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
