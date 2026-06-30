package zw.gov.mohcc.impilo.assetregistry.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Asset audit / verification run (expected list → scan/confirm → exceptions). */
@Entity
@Table(name = "asr_asset_audit")
public class AssetAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "audit_id")
    private UUID auditId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "facility_ref", nullable = false, length = 255)
    private String facilityRef;

    @Column(name = "name", nullable = false, length = 512)
    private String name;

    @Column(name = "status", nullable = false, length = 24)
    private String status = "OPEN";

    @Column(name = "conducted_by", length = 255)
    private String conductedBy;

    @Column(name = "opened_at", nullable = false)
    private OffsetDateTime openedAt = OffsetDateTime.now();

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata_json", columnDefinition = "jsonb")
    private String metadataJson;

    protected AssetAuditEntity() {}

    public AssetAuditEntity(UUID tenantId, String facilityRef, String name) {
        this.tenantId = tenantId;
        this.facilityRef = facilityRef;
        this.name = name;
        this.openedAt = OffsetDateTime.now();
    }

    public UUID getAuditId() { return auditId; }
    public UUID getTenantId() { return tenantId; }
    public String getFacilityRef() { return facilityRef; }
    public void setFacilityRef(String facilityRef) { this.facilityRef = facilityRef; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getConductedBy() { return conductedBy; }
    public void setConductedBy(String conductedBy) { this.conductedBy = conductedBy; }
    public OffsetDateTime getOpenedAt() { return openedAt; }
    public OffsetDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(OffsetDateTime completedAt) { this.completedAt = completedAt; }
    public String getMetadataJson() { return metadataJson; }
    public void setMetadataJson(String metadataJson) { this.metadataJson = metadataJson; }
}
