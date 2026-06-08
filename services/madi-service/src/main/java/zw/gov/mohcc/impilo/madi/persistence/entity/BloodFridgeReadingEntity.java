package zw.gov.mohcc.impilo.madi.persistence.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "blood_fridge_readings", schema = "madi")
public class BloodFridgeReadingEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "reading_id", nullable = false, unique = true)
    private UUID readingId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "fridge_id", nullable = false)
    private UUID fridgeId;

    @Column(name = "iot_reading_ref")
    private String iotReadingRef;

    @Column(name = "temperature_c", nullable = false)
    private BigDecimal temperatureC;

    @Column(name = "alarm_status", nullable = false)
    private String alarmStatus;

    @Column(name = "recorded_at", nullable = false)
    private OffsetDateTime recordedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (readingId == null) {
            readingId = UUID.randomUUID();
        }
        if (alarmStatus == null) {
            alarmStatus = "NORMAL";
        }
        createdAt = OffsetDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public UUID getReadingId() { return readingId; }
    public void setReadingId(UUID readingId) { this.readingId = readingId; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public UUID getFridgeId() { return fridgeId; }
    public void setFridgeId(UUID fridgeId) { this.fridgeId = fridgeId; }

    public String getIotReadingRef() { return iotReadingRef; }
    public void setIotReadingRef(String iotReadingRef) { this.iotReadingRef = iotReadingRef; }

    public BigDecimal getTemperatureC() { return temperatureC; }
    public void setTemperatureC(BigDecimal temperatureC) { this.temperatureC = temperatureC; }

    public String getAlarmStatus() { return alarmStatus; }
    public void setAlarmStatus(String alarmStatus) { this.alarmStatus = alarmStatus; }

    public OffsetDateTime getRecordedAt() { return recordedAt; }
    public void setRecordedAt(OffsetDateTime recordedAt) { this.recordedAt = recordedAt; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
