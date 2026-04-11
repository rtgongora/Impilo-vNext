package zw.gov.mohcc.impilo.assetregistry.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Equipment entity — Health OS §9: "Equipment ID identifies a specific operational
 * device or piece of equipment in use for care, diagnostics, monitoring, support,
 * or infrastructure."
 *
 * <p>Equipment is a first-class entity separate from the governance-tracked Asset.
 * Equipment may be linked to an Asset (for ownership tracking) via assetId but
 * operates independently in clinical and operational workflows.</p>
 */
@Entity
@Table(name = "asr_equipment")
public class EquipmentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "equipment_id", nullable = false)
    private UUID equipmentId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "facility_id", nullable = false, length = 255)
    private String facilityId;

    /** Optional link to the governance-tracked Asset (ownership/finance). */
    @Column(name = "asset_id")
    private UUID assetId;

    @Column(nullable = false, length = 512)
    private String name;

    @Column(name = "equipment_type", nullable = false, length = 64)
    private String equipmentType;

    @Column(length = 255)
    private String model;

    @Column(length = 255)
    private String manufacturer;

    @Column(name = "serial_number", length = 255)
    private String serialNumber;

    @Column(nullable = false, length = 32)
    private String status = "OPERATIONAL";

    @Column(name = "department_id", length = 255)
    private String departmentId;

    @Column(name = "ward_id", length = 255)
    private String wardId;

    @Column(name = "location_description", length = 512)
    private String locationDescription;

    @Column(name = "last_calibration")
    private LocalDate lastCalibration;

    @Column(name = "next_calibration")
    private LocalDate nextCalibration;

    @Column(name = "last_maintenance")
    private LocalDate lastMaintenance;

    @Column(name = "next_maintenance")
    private LocalDate nextMaintenance;

    /** Link to IoT device ID (from iot-ingestion-service). */
    @Column(name = "device_id", length = 255)
    private String deviceId;

    @Column(name = "metadata_json", columnDefinition = "JSONB")
    private String metadataJson;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected EquipmentEntity() {}

    public EquipmentEntity(UUID tenantId, String facilityId, String name, String equipmentType) {
        this.tenantId = tenantId;
        this.facilityId = facilityId;
        this.name = name;
        this.equipmentType = equipmentType;
        OffsetDateTime now = OffsetDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    // --- Getters and Setters ---
    public UUID getEquipmentId() { return equipmentId; }
    public UUID getTenantId() { return tenantId; }
    public String getFacilityId() { return facilityId; }
    public void setFacilityId(String facilityId) { this.facilityId = facilityId; }
    public UUID getAssetId() { return assetId; }
    public void setAssetId(UUID assetId) { this.assetId = assetId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getEquipmentType() { return equipmentType; }
    public void setEquipmentType(String equipmentType) { this.equipmentType = equipmentType; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public String getManufacturer() { return manufacturer; }
    public void setManufacturer(String manufacturer) { this.manufacturer = manufacturer; }
    public String getSerialNumber() { return serialNumber; }
    public void setSerialNumber(String serialNumber) { this.serialNumber = serialNumber; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getDepartmentId() { return departmentId; }
    public void setDepartmentId(String departmentId) { this.departmentId = departmentId; }
    public String getWardId() { return wardId; }
    public void setWardId(String wardId) { this.wardId = wardId; }
    public String getLocationDescription() { return locationDescription; }
    public void setLocationDescription(String locationDescription) { this.locationDescription = locationDescription; }
    public LocalDate getLastCalibration() { return lastCalibration; }
    public void setLastCalibration(LocalDate lastCalibration) { this.lastCalibration = lastCalibration; }
    public LocalDate getNextCalibration() { return nextCalibration; }
    public void setNextCalibration(LocalDate nextCalibration) { this.nextCalibration = nextCalibration; }
    public LocalDate getLastMaintenance() { return lastMaintenance; }
    public void setLastMaintenance(LocalDate lastMaintenance) { this.lastMaintenance = lastMaintenance; }
    public LocalDate getNextMaintenance() { return nextMaintenance; }
    public void setNextMaintenance(LocalDate nextMaintenance) { this.nextMaintenance = nextMaintenance; }
    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }
    public String getMetadataJson() { return metadataJson; }
    public void setMetadataJson(String metadataJson) { this.metadataJson = metadataJson; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
