package zw.gov.mohcc.impilo.msika.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "msika_offerings")
public class OfferingEntity {

    @Id
    @Column(name = "offering_id", length = 26)
    private String offeringId;

    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(name = "catalog_item_id", nullable = false, length = 26)
    private String catalogItemId;

    @Column(name = "offering_type", nullable = false, length = 30)
    private String offeringType;

    @Column(name = "provider_type", length = 30)
    private String providerType;

    @Column(name = "provider_ref", nullable = false, length = 255)
    private String providerRef;

    @Column(name = "facility_ref")
    private UUID facilityRef;

    @Column(name = "vendor_ref")
    private UUID vendorRef;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "availability_state", nullable = false, length = 30)
    private String availabilityState = "ACTIVE";

    @Column(name = "inventory_mode", length = 30)
    private String inventoryMode;

    @Column(name = "booking_mode", length = 30)
    private String bookingMode;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "pickup_modes_supported", nullable = false, columnDefinition = "jsonb")
    private String pickupModesSupportedJson = "[]";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "delivery_modes_supported", nullable = false, columnDefinition = "jsonb")
    private String deliveryModesSupportedJson = "[]";

    @Column(name = "lead_time_minutes")
    private Integer leadTimeMinutes;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "geographic_coverage", columnDefinition = "jsonb")
    private String geographicCoverageJson = "{}";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private String metadataJson = "{}";

    @Column(name = "created_by", nullable = false, length = 128)
    private String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        createdAt = now;
        updatedAt = now;
        if (pickupModesSupportedJson == null) pickupModesSupportedJson = "[]";
        if (deliveryModesSupportedJson == null) deliveryModesSupportedJson = "[]";
        if (geographicCoverageJson == null) geographicCoverageJson = "{}";
        if (metadataJson == null) metadataJson = "{}";
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    public String getOfferingId() { return offeringId; }
    public void setOfferingId(String offeringId) { this.offeringId = offeringId; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public String getCatalogItemId() { return catalogItemId; }
    public void setCatalogItemId(String catalogItemId) { this.catalogItemId = catalogItemId; }
    public String getOfferingType() { return offeringType; }
    public void setOfferingType(String offeringType) { this.offeringType = offeringType; }
    public String getProviderType() { return providerType; }
    public void setProviderType(String providerType) { this.providerType = providerType; }
    public String getProviderRef() { return providerRef; }
    public void setProviderRef(String providerRef) { this.providerRef = providerRef; }
    public UUID getFacilityRef() { return facilityRef; }
    public void setFacilityRef(UUID facilityRef) { this.facilityRef = facilityRef; }
    public UUID getVendorRef() { return vendorRef; }
    public void setVendorRef(UUID vendorRef) { this.vendorRef = vendorRef; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public String getAvailabilityState() { return availabilityState; }
    public void setAvailabilityState(String availabilityState) { this.availabilityState = availabilityState; }
    public String getInventoryMode() { return inventoryMode; }
    public void setInventoryMode(String inventoryMode) { this.inventoryMode = inventoryMode; }
    public String getBookingMode() { return bookingMode; }
    public void setBookingMode(String bookingMode) { this.bookingMode = bookingMode; }
    public String getPickupModesSupportedJson() { return pickupModesSupportedJson; }
    public void setPickupModesSupportedJson(String pickupModesSupportedJson) { this.pickupModesSupportedJson = pickupModesSupportedJson; }
    public String getDeliveryModesSupportedJson() { return deliveryModesSupportedJson; }
    public void setDeliveryModesSupportedJson(String deliveryModesSupportedJson) { this.deliveryModesSupportedJson = deliveryModesSupportedJson; }
    public Integer getLeadTimeMinutes() { return leadTimeMinutes; }
    public void setLeadTimeMinutes(Integer leadTimeMinutes) { this.leadTimeMinutes = leadTimeMinutes; }
    public String getGeographicCoverageJson() { return geographicCoverageJson; }
    public void setGeographicCoverageJson(String geographicCoverageJson) { this.geographicCoverageJson = geographicCoverageJson; }
    public String getMetadataJson() { return metadataJson; }
    public void setMetadataJson(String metadataJson) { this.metadataJson = metadataJson; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}

