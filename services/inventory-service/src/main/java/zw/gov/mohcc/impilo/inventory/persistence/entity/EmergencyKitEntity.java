package zw.gov.mohcc.impilo.inventory.persistence.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * A tracked emergency kit/trolley as a unit (W10) — distinct from ordinary stock: periodically
 * verified complete against its own declared manifest ({@link #contentsManifest}), not replenished
 * item-by-item like {@code inv_items}.
 */
@Entity
@Table(name = "inv_emergency_kit")
public class EmergencyKitEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "kit_id")
    private UUID kitId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "facility_id", nullable = false)
    private UUID facilityId;

    @Column(name = "store_id")
    private UUID storeId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "kit_type", nullable = false)
    private String kitType = "RESUS_TROLLEY";

    /** Raw JSON: [{"itemCode","itemName","expectedQty"}, ...] — see V200's own header. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "contents_manifest", columnDefinition = "jsonb", nullable = false)
    private String contentsManifest = "[]";

    @Column(name = "status", nullable = false)
    private String status = "IN_SERVICE";

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() { updatedAt = OffsetDateTime.now(); }

    public UUID getKitId() { return kitId; }
    public void setKitId(UUID kitId) { this.kitId = kitId; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public UUID getFacilityId() { return facilityId; }
    public void setFacilityId(UUID facilityId) { this.facilityId = facilityId; }
    public UUID getStoreId() { return storeId; }
    public void setStoreId(UUID storeId) { this.storeId = storeId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getKitType() { return kitType; }
    public void setKitType(String kitType) { this.kitType = kitType; }
    public String getContentsManifest() { return contentsManifest; }
    public void setContentsManifest(String contentsManifest) { this.contentsManifest = contentsManifest; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
