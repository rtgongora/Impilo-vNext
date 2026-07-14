package zw.gov.mohcc.impilo.tuso.persistence.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * A theatre equipment asset. TUSO registers presence + maintenance_state; the
 * asset-registry service remains the live-readiness SoR — this is not a duplicate
 * of its maintenance workflow.
 */
@Entity
@Table(name = "equipment_asset", schema = "tuso")
public class EquipmentAssetEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "facility_id", nullable = false)
    private Long facilityId;

    @Column(name = "clinical_space_id")
    private UUID clinicalSpaceId;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "equipment_type", length = 64)
    private String equipmentType;

    @Column(name = "maintenance_state", nullable = false, length = 24)
    private String maintenanceState = "OPERATIONAL";

    @Column(name = "status", nullable = false, length = 24)
    private String status = "AVAILABLE";

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public Map<String, Object> toMap() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", id != null ? id.toString() : null);
        out.put("facilityId", facilityId);
        out.put("clinicalSpaceId", clinicalSpaceId != null ? clinicalSpaceId.toString() : null);
        out.put("name", name);
        out.put("equipmentType", equipmentType);
        out.put("maintenanceState", maintenanceState);
        out.put("status", status);
        return out;
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public Long getFacilityId() { return facilityId; }
    public void setFacilityId(Long facilityId) { this.facilityId = facilityId; }
    public UUID getClinicalSpaceId() { return clinicalSpaceId; }
    public void setClinicalSpaceId(UUID clinicalSpaceId) { this.clinicalSpaceId = clinicalSpaceId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getEquipmentType() { return equipmentType; }
    public void setEquipmentType(String equipmentType) { this.equipmentType = equipmentType; }
    public String getMaintenanceState() { return maintenanceState; }
    public void setMaintenanceState(String maintenanceState) { this.maintenanceState = maintenanceState; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
