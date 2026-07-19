package zw.gov.mohcc.impilo.ndila.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Canonical Ndila location entity.
 *
 * <p>Ndila does not replace authoritative entity identity (Tuso owns facility
 * identity, Indawo owns public-health-site identity, Varapi owns provider
 * identity, etc.). Ndila owns and indexes the <em>geospatial representation</em>
 * of those entities and the spatial operations performed on them, along with
 * locations that have no other natural owner (delivery waypoints, drone
 * launch points, emergency incidents, etc.).
 */
@Entity
@Table(name = "ndila_locations")
public class NdilaLocationEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "pod_id", nullable = false, length = 64)
    private String podId = "national-spine";

    @Column(name = "owner_service", nullable = false, length = 64)
    private String ownerService;

    @Column(name = "owner_entity_type", nullable = false, length = 64)
    private String ownerEntityType;

    @Column(name = "owner_entity_id", length = 255)
    private String ownerEntityId;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "location_type", nullable = false, length = 64)
    private String locationType;

    @Column(name = "latitude", precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(name = "longitude", precision = 10, scale = 7)
    private BigDecimal longitude;

    @Column(name = "altitude_meters", precision = 8, scale = 2)
    private BigDecimal altitudeMeters;

    @Column(name = "address_line1", length = 255)
    private String addressLine1;

    @Column(name = "address_line2", length = 255)
    private String addressLine2;

    @Column(name = "locality", length = 128)
    private String locality;

    @Column(name = "ward", length = 128)
    private String ward;

    @Column(name = "district", length = 128)
    private String district;

    @Column(name = "province", length = 128)
    private String province;

    @Column(name = "country", nullable = false, length = 3)
    private String country = "ZWE";

    @Column(name = "postal_code", length = 16)
    private String postalCode;

    @Column(name = "plus_code", length = 32)
    private String plusCode;

    @Column(name = "what3words", length = 64)
    private String what3words;

    @Column(name = "geohash", length = 16)
    private String geohash;

    @Column(name = "h3_index", length = 32)
    private String h3Index;

    @Column(name = "coordinate_accuracy_meters", precision = 8, scale = 2)
    private BigDecimal coordinateAccuracyMeters;

    @Column(name = "source", nullable = false, length = 32)
    private String source = "USER_ENTERED";

    @Column(name = "verification_status", nullable = false, length = 32)
    private String verificationStatus = "UNVERIFIED";

    @Column(name = "verified_by", length = 255)
    private String verifiedBy;

    @Column(name = "verified_at")
    private OffsetDateTime verifiedAt;

    @Column(name = "boundary_id")
    private UUID boundaryId;

    /**
     * Shared place anchor (D-L1): multiple owner-keyed locations on one campus
     * point at one {@link NdilaPlaceAnchorEntity}. Stewarded assignment.
     */
    @Column(name = "anchor_id")
    private UUID anchorId;

    @Column(name = "catchment_area_id")
    private UUID catchmentAreaId;

    @Column(name = "sensitivity_level", nullable = false, length = 32)
    private String sensitivityLevel = "PUBLIC";

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(name = "geojson", columnDefinition = "jsonb")
    private String geojson;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected NdilaLocationEntity() {}

    public NdilaLocationEntity(UUID tenantId, String ownerService, String ownerEntityType,
                               String name, String locationType) {
        this.id = UUID.randomUUID();
        this.tenantId = tenantId;
        this.ownerService = ownerService;
        this.ownerEntityType = ownerEntityType;
        this.name = name;
        this.locationType = locationType;
        OffsetDateTime now = OffsetDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public String getPodId() { return podId; }
    public void setPodId(String podId) { this.podId = podId; }
    public String getOwnerService() { return ownerService; }
    public String getOwnerEntityType() { return ownerEntityType; }
    public String getOwnerEntityId() { return ownerEntityId; }
    public void setOwnerEntityId(String ownerEntityId) { this.ownerEntityId = ownerEntityId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getLocationType() { return locationType; }
    public void setLocationType(String locationType) { this.locationType = locationType; }
    public BigDecimal getLatitude() { return latitude; }
    public void setLatitude(BigDecimal latitude) { this.latitude = latitude; }
    public BigDecimal getLongitude() { return longitude; }
    public void setLongitude(BigDecimal longitude) { this.longitude = longitude; }
    public BigDecimal getAltitudeMeters() { return altitudeMeters; }
    public void setAltitudeMeters(BigDecimal altitudeMeters) { this.altitudeMeters = altitudeMeters; }
    public String getAddressLine1() { return addressLine1; }
    public void setAddressLine1(String addressLine1) { this.addressLine1 = addressLine1; }
    public String getAddressLine2() { return addressLine2; }
    public void setAddressLine2(String addressLine2) { this.addressLine2 = addressLine2; }
    public String getLocality() { return locality; }
    public void setLocality(String locality) { this.locality = locality; }
    public String getWard() { return ward; }
    public void setWard(String ward) { this.ward = ward; }
    public String getDistrict() { return district; }
    public void setDistrict(String district) { this.district = district; }
    public String getProvince() { return province; }
    public void setProvince(String province) { this.province = province; }
    public String getCountry() { return country; }
    public void setCountry(String country) { this.country = country; }
    public String getPostalCode() { return postalCode; }
    public void setPostalCode(String postalCode) { this.postalCode = postalCode; }
    public String getPlusCode() { return plusCode; }
    public void setPlusCode(String plusCode) { this.plusCode = plusCode; }
    public String getWhat3words() { return what3words; }
    public void setWhat3words(String what3words) { this.what3words = what3words; }
    public String getGeohash() { return geohash; }
    public void setGeohash(String geohash) { this.geohash = geohash; }
    public String getH3Index() { return h3Index; }
    public void setH3Index(String h3Index) { this.h3Index = h3Index; }
    public BigDecimal getCoordinateAccuracyMeters() { return coordinateAccuracyMeters; }
    public void setCoordinateAccuracyMeters(BigDecimal v) { this.coordinateAccuracyMeters = v; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public String getVerificationStatus() { return verificationStatus; }
    public void setVerificationStatus(String verificationStatus) { this.verificationStatus = verificationStatus; }
    public String getVerifiedBy() { return verifiedBy; }
    public void setVerifiedBy(String verifiedBy) { this.verifiedBy = verifiedBy; }
    public OffsetDateTime getVerifiedAt() { return verifiedAt; }
    public void setVerifiedAt(OffsetDateTime verifiedAt) { this.verifiedAt = verifiedAt; }
    public UUID getBoundaryId() { return boundaryId; }
    public void setBoundaryId(UUID boundaryId) { this.boundaryId = boundaryId; }
    public UUID getAnchorId() { return anchorId; }
    public void setAnchorId(UUID anchorId) { this.anchorId = anchorId; }
    public UUID getCatchmentAreaId() { return catchmentAreaId; }
    public void setCatchmentAreaId(UUID catchmentAreaId) { this.catchmentAreaId = catchmentAreaId; }
    public String getSensitivityLevel() { return sensitivityLevel; }
    public void setSensitivityLevel(String sensitivityLevel) { this.sensitivityLevel = sensitivityLevel; }
    public String getGeojson() { return geojson; }
    public void setGeojson(String geojson) { this.geojson = geojson; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void touch() { this.updatedAt = OffsetDateTime.now(); }
}
