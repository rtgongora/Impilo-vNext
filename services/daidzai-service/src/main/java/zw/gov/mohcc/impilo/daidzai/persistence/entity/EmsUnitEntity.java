package zw.gov.mohcc.impilo.daidzai.persistence.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * A responding EMS unit (ambulance + crew). The ambulance is an asset-registry Object ID and the
 * crew are VASHANDI provider ids — both carried by value (no cross-service FK), per architecture
 * decision #2.
 */
@Entity
@Table(name = "dai_ems_unit", schema = "daidzai")
public class EmsUnitEntity {

    @Id @Column(name = "id") private UUID id;
    @Column(name = "tenant_id", nullable = false) private UUID tenantId;
    @Column(name = "unit_reference", nullable = false, length = 48) private String unitReference;
    @Column(name = "call_sign", length = 64) private String callSign;
    @Column(name = "ambulance_asset_id", length = 128) private String ambulanceAssetId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "crew_json", nullable = false, columnDefinition = "jsonb")
    private String crewJson = "[]";

    @Column(name = "status", nullable = false, length = 24) private String status = "AVAILABLE";
    @Column(name = "home_facility_id") private UUID homeFacilityId;
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
    public void setId(UUID id) { this.id = id; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID v) { this.tenantId = v; }
    public String getUnitReference() { return unitReference; }
    public void setUnitReference(String v) { this.unitReference = v; }
    public String getCallSign() { return callSign; }
    public void setCallSign(String v) { this.callSign = v; }
    public String getAmbulanceAssetId() { return ambulanceAssetId; }
    public void setAmbulanceAssetId(String v) { this.ambulanceAssetId = v; }
    public String getCrewJson() { return crewJson; }
    public void setCrewJson(String v) { this.crewJson = v; }
    public String getStatus() { return status; }
    public void setStatus(String v) { this.status = v; }
    public UUID getHomeFacilityId() { return homeFacilityId; }
    public void setHomeFacilityId(UUID v) { this.homeFacilityId = v; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
