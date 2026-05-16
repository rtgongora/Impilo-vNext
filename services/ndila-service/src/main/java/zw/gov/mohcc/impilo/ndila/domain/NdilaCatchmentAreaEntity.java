package zw.gov.mohcc.impilo.ndila.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "ndila_catchment_areas")
public class NdilaCatchmentAreaEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "owner_service", nullable = false, length = 64)
    private String ownerService;

    @Column(name = "owner_entity_type", length = 64)
    private String ownerEntityType;

    @Column(name = "owner_entity_id", length = 255)
    private String ownerEntityId;

    @Column(name = "geometry_type", nullable = false, length = 16)
    private String geometryType = "POLYGON"; // RADIUS|POLYGON|MULTIPOLYGON|ISOCHRONE

    @Column(name = "geometry_json", columnDefinition = "jsonb")
    private String geometryJson;

    @Column(name = "center_latitude", precision = 10, scale = 7)
    private BigDecimal centerLatitude;

    @Column(name = "center_longitude", precision = 10, scale = 7)
    private BigDecimal centerLongitude;

    @Column(name = "radius_meters", precision = 12, scale = 2)
    private BigDecimal radiusMeters;

    @Column(name = "population_estimate")
    private Integer populationEstimate;

    @Column(name = "households_estimate")
    private Integer householdsEstimate;

    @Column(name = "source", nullable = false, length = 32)
    private String source = "MANUAL";

    @Column(name = "verification_status", nullable = false, length = 32)
    private String verificationStatus = "UNVERIFIED";

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected NdilaCatchmentAreaEntity() {}

    public NdilaCatchmentAreaEntity(UUID tenantId, String name, String ownerService) {
        this.id = UUID.randomUUID();
        this.tenantId = tenantId;
        this.name = name;
        this.ownerService = ownerService;
        OffsetDateTime now = OffsetDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getOwnerService() { return ownerService; }
    public String getOwnerEntityType() { return ownerEntityType; }
    public void setOwnerEntityType(String ownerEntityType) { this.ownerEntityType = ownerEntityType; }
    public String getOwnerEntityId() { return ownerEntityId; }
    public void setOwnerEntityId(String ownerEntityId) { this.ownerEntityId = ownerEntityId; }
    public String getGeometryType() { return geometryType; }
    public void setGeometryType(String geometryType) { this.geometryType = geometryType; }
    public String getGeometryJson() { return geometryJson; }
    public void setGeometryJson(String geometryJson) { this.geometryJson = geometryJson; }
    public BigDecimal getCenterLatitude() { return centerLatitude; }
    public void setCenterLatitude(BigDecimal centerLatitude) { this.centerLatitude = centerLatitude; }
    public BigDecimal getCenterLongitude() { return centerLongitude; }
    public void setCenterLongitude(BigDecimal centerLongitude) { this.centerLongitude = centerLongitude; }
    public BigDecimal getRadiusMeters() { return radiusMeters; }
    public void setRadiusMeters(BigDecimal radiusMeters) { this.radiusMeters = radiusMeters; }
    public Integer getPopulationEstimate() { return populationEstimate; }
    public void setPopulationEstimate(Integer populationEstimate) { this.populationEstimate = populationEstimate; }
    public Integer getHouseholdsEstimate() { return householdsEstimate; }
    public void setHouseholdsEstimate(Integer householdsEstimate) { this.householdsEstimate = householdsEstimate; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public String getVerificationStatus() { return verificationStatus; }
    public void setVerificationStatus(String verificationStatus) { this.verificationStatus = verificationStatus; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void touch() { this.updatedAt = OffsetDateTime.now(); }
}
