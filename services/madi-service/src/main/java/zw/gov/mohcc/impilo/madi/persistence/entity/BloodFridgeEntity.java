package zw.gov.mohcc.impilo.madi.persistence.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "blood_fridges", schema = "madi")
public class BloodFridgeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "fridge_id", nullable = false, unique = true)
    private UUID fridgeId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

@Column(name = "blood_bank_id")
    private UUID bloodBankId;

@Column(name = "location_id")
    private UUID locationId;

@Column(name = "fridge_code")
    private String fridgeCode;

@Column(name = "temperature_c")
    private BigDecimal temperatureC;

@Column(name = "status")
    private String status;

@Column(name = "iot_device_ref")
    private String iotDeviceRef;

@Column(name = "target_temp_min_c")
    private BigDecimal targetTempMinC;

@Column(name = "target_temp_max_c")
    private BigDecimal targetTempMaxC;

@Column(name = "last_reading_at")
    private OffsetDateTime lastReadingAt;

@Column(name = "alarm_status")
    private String alarmStatus;

@Column(name = "facility_id")
    private UUID facilityId;

@Column(name = "jurisdiction")
    private String jurisdiction;

@Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (fridgeId == null) {
            fridgeId = UUID.randomUUID();
        }
        createdAt = OffsetDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public UUID getFridgeId() { return fridgeId; }
    public void setFridgeId(UUID fridgeId) { this.fridgeId = fridgeId; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public UUID getBloodBankId() { return bloodBankId; }
    public void setBloodBankId(UUID bloodBankId) { this.bloodBankId = bloodBankId; }

    public UUID getLocationId() { return locationId; }
    public void setLocationId(UUID locationId) { this.locationId = locationId; }

    public String getFridgeCode() { return fridgeCode; }
    public void setFridgeCode(String fridgeCode) { this.fridgeCode = fridgeCode; }

    public BigDecimal getTemperatureC() { return temperatureC; }
    public void setTemperatureC(BigDecimal temperatureC) { this.temperatureC = temperatureC; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getIotDeviceRef() { return iotDeviceRef; }
    public void setIotDeviceRef(String iotDeviceRef) { this.iotDeviceRef = iotDeviceRef; }

    public BigDecimal getTargetTempMinC() { return targetTempMinC; }
    public void setTargetTempMinC(BigDecimal targetTempMinC) { this.targetTempMinC = targetTempMinC; }

    public BigDecimal getTargetTempMaxC() { return targetTempMaxC; }
    public void setTargetTempMaxC(BigDecimal targetTempMaxC) { this.targetTempMaxC = targetTempMaxC; }

    public OffsetDateTime getLastReadingAt() { return lastReadingAt; }
    public void setLastReadingAt(OffsetDateTime lastReadingAt) { this.lastReadingAt = lastReadingAt; }

    public String getAlarmStatus() { return alarmStatus; }
    public void setAlarmStatus(String alarmStatus) { this.alarmStatus = alarmStatus; }

    public UUID getFacilityId() { return facilityId; }
    public void setFacilityId(UUID facilityId) { this.facilityId = facilityId; }

    public String getJurisdiction() { return jurisdiction; }
    public void setJurisdiction(String jurisdiction) { this.jurisdiction = jurisdiction; }

    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

}