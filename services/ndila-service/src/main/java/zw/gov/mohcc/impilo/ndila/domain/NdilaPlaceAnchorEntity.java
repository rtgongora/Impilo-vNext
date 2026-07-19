package zw.gov.mohcc.impilo.ndila.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Shared geospatial place anchor (Place Journey Doctrine §6, D-L1): one
 * physical location / parcel / campus, shareable by multiple owner-keyed
 * {@link NdilaLocationEntity} rows across registries (a TUSO facility and an
 * INDAWO site on the same campus point at ONE anchor). Geo-only — an anchor
 * never carries names, statuses or authority data, so Ndila can never become
 * a third "place master".
 */
@Entity
@Table(name = "ndila_place_anchor")
public class NdilaPlaceAnchorEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "pod_id", nullable = false, length = 64)
    private String podId = "national-spine";

    @Column(name = "centroid_latitude", nullable = false, precision = 10, scale = 7)
    private BigDecimal centroidLatitude;

    @Column(name = "centroid_longitude", nullable = false, precision = 10, scale = 7)
    private BigDecimal centroidLongitude;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "boundary_geojson", columnDefinition = "jsonb")
    private String boundaryGeojson;

    @Column(name = "ward", length = 128)
    private String ward;

    @Column(name = "district", length = 128)
    private String district;

    @Column(name = "province", length = 128)
    private String province;

    @Column(name = "campus_label", length = 255)
    private String campusLabel;

    @Column(name = "created_by", length = 80)
    private String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        createdAt = OffsetDateTime.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public String getPodId() { return podId; }
    public void setPodId(String podId) { this.podId = podId; }
    public BigDecimal getCentroidLatitude() { return centroidLatitude; }
    public void setCentroidLatitude(BigDecimal centroidLatitude) { this.centroidLatitude = centroidLatitude; }
    public BigDecimal getCentroidLongitude() { return centroidLongitude; }
    public void setCentroidLongitude(BigDecimal centroidLongitude) { this.centroidLongitude = centroidLongitude; }
    public String getBoundaryGeojson() { return boundaryGeojson; }
    public void setBoundaryGeojson(String boundaryGeojson) { this.boundaryGeojson = boundaryGeojson; }
    public String getWard() { return ward; }
    public void setWard(String ward) { this.ward = ward; }
    public String getDistrict() { return district; }
    public void setDistrict(String district) { this.district = district; }
    public String getProvince() { return province; }
    public void setProvince(String province) { this.province = province; }
    public String getCampusLabel() { return campusLabel; }
    public void setCampusLabel(String campusLabel) { this.campusLabel = campusLabel; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
