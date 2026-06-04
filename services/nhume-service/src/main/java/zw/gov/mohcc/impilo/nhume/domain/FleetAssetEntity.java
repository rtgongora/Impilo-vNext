package zw.gov.mohcc.impilo.nhume.domain;

import jakarta.persistence.Column;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "nhume_fleet_assets")
public class FleetAssetEntity {

    @Id
    @Column(name = "asset_id", nullable = false)
    private UUID assetId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "asset_code", nullable = false, length = 64)
    private String assetCode;

    @Column(name = "display_name", nullable = false, length = 255)
    private String displayName;

    @Column(name = "registration_number", length = 64)
    private String registrationNumber;

    @Column(name = "vehicle_type", nullable = false, length = 64)
    private String vehicleType;

    @Column(name = "mode_category", nullable = false, length = 48)
    private String modeCategory;

    @Column(name = "ownership", nullable = false, length = 48)
    private String ownership = "OWNED";

    @Column(name = "organisation_owner", length = 255)
    private String organisationOwner;

    @Column(name = "home_facility_ref", length = 255)
    private String homeFacilityRef;

    @Column(name = "assigned_programme", length = 255)
    private String assignedProgramme;

    @Column(name = "assigned_zone_id")
    private UUID assignedZoneId;

    @Column(name = "assigned_courier_id")
    private UUID assignedCourierId;

    @Column(name = "capacity_units")
    private Integer capacityUnits;

    @Column(name = "weight_limit_kg")
    private BigDecimal weightLimitKg;

    @Column(name = "volume_limit_l")
    private BigDecimal volumeLimitL;

    @Column(name = "cold_chain_capable", nullable = false)
    private boolean coldChainCapable;

    @Column(name = "temperature_monitoring", nullable = false)
    private boolean temperatureMonitoring;

    @Column(name = "specimen_capable", nullable = false)
    private boolean specimenCapable;

    @Column(name = "hazmat_capable", nullable = false)
    private boolean hazmatCapable;

    @Column(name = "gps_capable", nullable = false)
    private boolean gpsCapable = true;

    @Column(name = "telematics_provider", length = 64)
    private String telematicsProvider;

    @Column(name = "insurance_ref", length = 128)
    private String insuranceRef;

    @Column(name = "licence_ref", length = 128)
    private String licenceRef;

    @Column(name = "maintenance_status", nullable = false, length = 32)
    private String maintenanceStatus = "OK";

    @Column(name = "availability_status", nullable = false, length = 32)
    private String availabilityStatus = "AVAILABLE";

    @Column(name = "operational_zone", length = 255)
    private String operationalZone;

    @Column(name = "last_lat")
    private Double lastLat;

    @Column(name = "last_lng")
    private Double lastLng;

    @Column(name = "last_seen_at")
    private OffsetDateTime lastSeenAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "compliance_docs_json", nullable = false, columnDefinition = "jsonb")
    private String complianceDocsJson = "[]";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "autonomous_profile_json", nullable = false, columnDefinition = "jsonb")
    private String autonomousProfileJson = "{}";

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "is_demo", nullable = false)
    private boolean demo;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    public FleetAssetEntity() {}

    public UUID getAssetId() { return assetId; }
    public void setAssetId(UUID v) { this.assetId = v; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID v) { this.tenantId = v; }
    public String getAssetCode() { return assetCode; }
    public void setAssetCode(String v) { this.assetCode = v; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String v) { this.displayName = v; }
    public String getRegistrationNumber() { return registrationNumber; }
    public void setRegistrationNumber(String v) { this.registrationNumber = v; }
    public String getVehicleType() { return vehicleType; }
    public void setVehicleType(String v) { this.vehicleType = v; }
    public String getModeCategory() { return modeCategory; }
    public void setModeCategory(String v) { this.modeCategory = v; }
    public String getOwnership() { return ownership; }
    public void setOwnership(String v) { this.ownership = v; }
    public String getOrganisationOwner() { return organisationOwner; }
    public void setOrganisationOwner(String v) { this.organisationOwner = v; }
    public String getHomeFacilityRef() { return homeFacilityRef; }
    public void setHomeFacilityRef(String v) { this.homeFacilityRef = v; }
    public String getAssignedProgramme() { return assignedProgramme; }
    public void setAssignedProgramme(String v) { this.assignedProgramme = v; }
    public UUID getAssignedZoneId() { return assignedZoneId; }
    public void setAssignedZoneId(UUID v) { this.assignedZoneId = v; }
    public UUID getAssignedCourierId() { return assignedCourierId; }
    public void setAssignedCourierId(UUID v) { this.assignedCourierId = v; }
    public Integer getCapacityUnits() { return capacityUnits; }
    public void setCapacityUnits(Integer v) { this.capacityUnits = v; }
    public BigDecimal getWeightLimitKg() { return weightLimitKg; }
    public void setWeightLimitKg(BigDecimal v) { this.weightLimitKg = v; }
    public BigDecimal getVolumeLimitL() { return volumeLimitL; }
    public void setVolumeLimitL(BigDecimal v) { this.volumeLimitL = v; }
    public boolean isColdChainCapable() { return coldChainCapable; }
    public void setColdChainCapable(boolean v) { this.coldChainCapable = v; }
    public boolean isTemperatureMonitoring() { return temperatureMonitoring; }
    public void setTemperatureMonitoring(boolean v) { this.temperatureMonitoring = v; }
    public boolean isSpecimenCapable() { return specimenCapable; }
    public void setSpecimenCapable(boolean v) { this.specimenCapable = v; }
    public boolean isHazmatCapable() { return hazmatCapable; }
    public void setHazmatCapable(boolean v) { this.hazmatCapable = v; }
    public boolean isGpsCapable() { return gpsCapable; }
    public void setGpsCapable(boolean v) { this.gpsCapable = v; }
    public String getTelematicsProvider() { return telematicsProvider; }
    public void setTelematicsProvider(String v) { this.telematicsProvider = v; }
    public String getInsuranceRef() { return insuranceRef; }
    public void setInsuranceRef(String v) { this.insuranceRef = v; }
    public String getLicenceRef() { return licenceRef; }
    public void setLicenceRef(String v) { this.licenceRef = v; }
    public String getMaintenanceStatus() { return maintenanceStatus; }
    public void setMaintenanceStatus(String v) { this.maintenanceStatus = v; }
    public String getAvailabilityStatus() { return availabilityStatus; }
    public void setAvailabilityStatus(String v) { this.availabilityStatus = v; }
    public String getOperationalZone() { return operationalZone; }
    public void setOperationalZone(String v) { this.operationalZone = v; }
    public Double getLastLat() { return lastLat; }
    public void setLastLat(Double v) { this.lastLat = v; }
    public Double getLastLng() { return lastLng; }
    public void setLastLng(Double v) { this.lastLng = v; }
    public OffsetDateTime getLastSeenAt() { return lastSeenAt; }
    public void setLastSeenAt(OffsetDateTime v) { this.lastSeenAt = v; }
    public String getComplianceDocsJson() { return complianceDocsJson; }
    public void setComplianceDocsJson(String v) { this.complianceDocsJson = v; }
    public String getAutonomousProfileJson() { return autonomousProfileJson; }
    public void setAutonomousProfileJson(String v) { this.autonomousProfileJson = v; }
    public boolean isActive() { return active; }
    public void setActive(boolean v) { this.active = v; }
    public boolean isDemo() { return demo; }
    public void setDemo(boolean v) { this.demo = v; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime v) { this.createdAt = v; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime v) { this.updatedAt = v; }
}
