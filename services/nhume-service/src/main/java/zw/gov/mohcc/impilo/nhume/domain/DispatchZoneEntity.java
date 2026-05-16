package zw.gov.mohcc.impilo.nhume.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "nhume_dispatch_zones")
public class DispatchZoneEntity {

    @Id
    @Column(name = "zone_id", nullable = false)
    private UUID zoneId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "code", nullable = false, length = 64)
    private String code;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "zone_kind", nullable = false, length = 32)
    private String zoneKind = "OPERATIONAL";

    @Column(name = "jurisdiction_ref", length = 255)
    private String jurisdictionRef;

    @Column(name = "geometry_kind", nullable = false, length = 32)
    private String geometryKind = "CIRCLE";

    @Column(name = "centre_lat")
    private Double centreLat;

    @Column(name = "centre_lng")
    private Double centreLng;

    @Column(name = "radius_m")
    private Integer radiusM;

    @Column(name = "polygon_json", columnDefinition = "TEXT")
    private String polygonJson;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "is_demo", nullable = false)
    private boolean demo;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    public DispatchZoneEntity() {}

    public UUID getZoneId() { return zoneId; }
    public void setZoneId(UUID v) { this.zoneId = v; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID v) { this.tenantId = v; }
    public String getCode() { return code; }
    public void setCode(String v) { this.code = v; }
    public String getName() { return name; }
    public void setName(String v) { this.name = v; }
    public String getZoneKind() { return zoneKind; }
    public void setZoneKind(String v) { this.zoneKind = v; }
    public String getJurisdictionRef() { return jurisdictionRef; }
    public void setJurisdictionRef(String v) { this.jurisdictionRef = v; }
    public String getGeometryKind() { return geometryKind; }
    public void setGeometryKind(String v) { this.geometryKind = v; }
    public Double getCentreLat() { return centreLat; }
    public void setCentreLat(Double v) { this.centreLat = v; }
    public Double getCentreLng() { return centreLng; }
    public void setCentreLng(Double v) { this.centreLng = v; }
    public Integer getRadiusM() { return radiusM; }
    public void setRadiusM(Integer v) { this.radiusM = v; }
    public String getPolygonJson() { return polygonJson; }
    public void setPolygonJson(String v) { this.polygonJson = v; }
    public String getNotes() { return notes; }
    public void setNotes(String v) { this.notes = v; }
    public boolean isActive() { return active; }
    public void setActive(boolean v) { this.active = v; }
    public boolean isDemo() { return demo; }
    public void setDemo(boolean v) { this.demo = v; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime v) { this.createdAt = v; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime v) { this.updatedAt = v; }
}
