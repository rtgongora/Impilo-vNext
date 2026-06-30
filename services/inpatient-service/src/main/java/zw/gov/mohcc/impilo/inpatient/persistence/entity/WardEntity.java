package zw.gov.mohcc.impilo.inpatient.persistence.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "ward", schema = "inpatient")
public class WardEntity {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "facility_id", nullable = false)
    private UUID facilityId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "ward_type", nullable = false)
    private String wardType = "GENERAL";

    @Column(name = "floor_label")
    private String floorLabel;

    @Column(name = "total_beds", nullable = false)
    private int totalBeds;

    @Column(name = "status", nullable = false)
    private String status = "ACTIVE";

    @Column(name = "gender_designation", nullable = false)
    private String genderDesignation = "ANY";

    @Column(name = "age_group", nullable = false)
    private String ageGroup = "ADULT";

    @Column(name = "isolation_capable", nullable = false)
    private boolean isolationCapable = false;

    @Column(name = "oxygen_available", nullable = false)
    private boolean oxygenAvailable = true;

    @Column(name = "monitoring_capable", nullable = false)
    private boolean monitoringCapable = false;

    @Column(name = "icu_capable", nullable = false)
    private boolean icuCapable = false;

    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        OffsetDateTime now = OffsetDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public UUID getFacilityId() { return facilityId; }
    public void setFacilityId(UUID facilityId) { this.facilityId = facilityId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getWardType() { return wardType; }
    public void setWardType(String wardType) { this.wardType = wardType; }
    public String getFloorLabel() { return floorLabel; }
    public void setFloorLabel(String floorLabel) { this.floorLabel = floorLabel; }
    public int getTotalBeds() { return totalBeds; }
    public void setTotalBeds(int totalBeds) { this.totalBeds = totalBeds; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
    public String getGenderDesignation() { return genderDesignation; }
    public void setGenderDesignation(String genderDesignation) { this.genderDesignation = genderDesignation; }
    public String getAgeGroup() { return ageGroup; }
    public void setAgeGroup(String ageGroup) { this.ageGroup = ageGroup; }
    public boolean isIsolationCapable() { return isolationCapable; }
    public void setIsolationCapable(boolean isolationCapable) { this.isolationCapable = isolationCapable; }
    public boolean isOxygenAvailable() { return oxygenAvailable; }
    public void setOxygenAvailable(boolean oxygenAvailable) { this.oxygenAvailable = oxygenAvailable; }
    public boolean isMonitoringCapable() { return monitoringCapable; }
    public void setMonitoringCapable(boolean monitoringCapable) { this.monitoringCapable = monitoringCapable; }
    public boolean isIcuCapable() { return icuCapable; }
    public void setIcuCapable(boolean icuCapable) { this.icuCapable = icuCapable; }
}
