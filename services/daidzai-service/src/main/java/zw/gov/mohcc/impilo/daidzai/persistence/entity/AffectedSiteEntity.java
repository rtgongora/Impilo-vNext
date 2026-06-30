package zw.gov.mohcc.impilo.daidzai.persistence.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "dai_affected_site", schema = "daidzai")
public class AffectedSiteEntity {

    @Id @Column(name = "id") private UUID id;
    @Column(name = "tenant_id", nullable = false) private UUID tenantId;
    @Column(name = "incident_id", nullable = false) private UUID incidentId;
    @Column(name = "site_label", nullable = false, length = 255) private String siteLabel;
    @Column(name = "indawo_site_ref", length = 128) private String indawoSiteRef;
    @Column(name = "ndila_area_ref", length = 128) private String ndilaAreaRef;
    @Column(name = "location_lat") private Double locationLat;
    @Column(name = "location_lng") private Double locationLng;
    @Column(name = "estimated_affected") private Integer estimatedAffected;
    @Column(name = "status", nullable = false, length = 24) private String status = "ACTIVE";
    @Column(name = "created_at", nullable = false) private OffsetDateTime createdAt;
    @Column(name = "updated_at", nullable = false) private OffsetDateTime updatedAt;

    @PrePersist void onCreate() {
        if (id == null) id = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }
    @PreUpdate void onUpdate() { updatedAt = OffsetDateTime.now(); }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID v) { this.tenantId = v; }
    public UUID getIncidentId() { return incidentId; }
    public void setIncidentId(UUID v) { this.incidentId = v; }
    public String getSiteLabel() { return siteLabel; }
    public void setSiteLabel(String v) { this.siteLabel = v; }
    public String getIndawoSiteRef() { return indawoSiteRef; }
    public void setIndawoSiteRef(String v) { this.indawoSiteRef = v; }
    public String getNdilaAreaRef() { return ndilaAreaRef; }
    public void setNdilaAreaRef(String v) { this.ndilaAreaRef = v; }
    public Double getLocationLat() { return locationLat; }
    public void setLocationLat(Double v) { this.locationLat = v; }
    public Double getLocationLng() { return locationLng; }
    public void setLocationLng(Double v) { this.locationLng = v; }
    public Integer getEstimatedAffected() { return estimatedAffected; }
    public void setEstimatedAffected(Integer v) { this.estimatedAffected = v; }
    public String getStatus() { return status; }
    public void setStatus(String v) { this.status = v; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
