package zw.gov.mohcc.impilo.ndila.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "ndila_geofences")
public class NdilaGeofenceEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "geofence_type", nullable = false, length = 48)
    private String geofenceType;

    /** GeoJSON polygon or {"type":"Circle", "center":[lng,lat], "radius_m":...}. */
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(name = "geometry_json", nullable = false, columnDefinition = "jsonb")
    private String geometryJson;

    @Column(name = "center_latitude", precision = 10, scale = 7)
    private BigDecimal centerLatitude;

    @Column(name = "center_longitude", precision = 10, scale = 7)
    private BigDecimal centerLongitude;

    @Column(name = "radius_meters", precision = 12, scale = 2)
    private BigDecimal radiusMeters;

    @Column(name = "owner_service", length = 64)
    private String ownerService;

    @Column(name = "owner_entity_type", length = 64)
    private String ownerEntityType;

    @Column(name = "owner_entity_id", length = 255)
    private String ownerEntityId;

    @Column(name = "risk_level", nullable = false, length = 16)
    private String riskLevel = "LOW";

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(name = "rules_json", columnDefinition = "jsonb")
    private String rulesJson;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected NdilaGeofenceEntity() {}

    public NdilaGeofenceEntity(UUID tenantId, String name, String geofenceType, String geometryJson) {
        this.id = UUID.randomUUID();
        this.tenantId = tenantId;
        this.name = name;
        this.geofenceType = geofenceType;
        this.geometryJson = geometryJson;
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
    public String getGeofenceType() { return geofenceType; }
    public void setGeofenceType(String geofenceType) { this.geofenceType = geofenceType; }
    public String getGeometryJson() { return geometryJson; }
    public void setGeometryJson(String geometryJson) { this.geometryJson = geometryJson; }
    public BigDecimal getCenterLatitude() { return centerLatitude; }
    public void setCenterLatitude(BigDecimal v) { this.centerLatitude = v; }
    public BigDecimal getCenterLongitude() { return centerLongitude; }
    public void setCenterLongitude(BigDecimal v) { this.centerLongitude = v; }
    public BigDecimal getRadiusMeters() { return radiusMeters; }
    public void setRadiusMeters(BigDecimal radiusMeters) { this.radiusMeters = radiusMeters; }
    public String getOwnerService() { return ownerService; }
    public void setOwnerService(String ownerService) { this.ownerService = ownerService; }
    public String getOwnerEntityType() { return ownerEntityType; }
    public void setOwnerEntityType(String ownerEntityType) { this.ownerEntityType = ownerEntityType; }
    public String getOwnerEntityId() { return ownerEntityId; }
    public void setOwnerEntityId(String ownerEntityId) { this.ownerEntityId = ownerEntityId; }
    public String getRiskLevel() { return riskLevel; }
    public void setRiskLevel(String riskLevel) { this.riskLevel = riskLevel; }
    public String getRulesJson() { return rulesJson; }
    public void setRulesJson(String rulesJson) { this.rulesJson = rulesJson; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void touch() { this.updatedAt = OffsetDateTime.now(); }
}
