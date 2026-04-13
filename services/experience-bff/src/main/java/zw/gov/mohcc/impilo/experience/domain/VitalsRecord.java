package zw.gov.mohcc.impilo.experience.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "vitals_records")
public class VitalsRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(name = "patient_id", nullable = false)
    private UUID patientId;

    @Column(name = "encounter_id")
    private UUID encounterId;

    @Column(name = "recorded_by")
    private String recordedBy;

    private Integer systolic;

    private Integer diastolic;

    @Column(name = "heart_rate")
    private Integer heartRate;

    private BigDecimal temperature;

    @Column(name = "respiratory_rate")
    private Integer respiratoryRate;

    @Column(name = "oxygen_saturation")
    private BigDecimal oxygenSaturation;

    private BigDecimal weight;

    private BigDecimal height;

    private BigDecimal bmi;

    @Column(name = "pain_score")
    private Integer painScore;

    private String notes;

    @Column(name = "recorded_at")
    private OffsetDateTime recordedAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected VitalsRecord() {}

    public UUID getId() { return id; }
    public String getTenantId() { return tenantId; }
    public UUID getPatientId() { return patientId; }
    public UUID getEncounterId() { return encounterId; }
    public String getRecordedBy() { return recordedBy; }
    public Integer getSystolic() { return systolic; }
    public Integer getDiastolic() { return diastolic; }
    public Integer getHeartRate() { return heartRate; }
    public BigDecimal getTemperature() { return temperature; }
    public Integer getRespiratoryRate() { return respiratoryRate; }
    public BigDecimal getOxygenSaturation() { return oxygenSaturation; }
    public BigDecimal getWeight() { return weight; }
    public BigDecimal getHeight() { return height; }
    public BigDecimal getBmi() { return bmi; }
    public Integer getPainScore() { return painScore; }
    public String getNotes() { return notes; }
    public OffsetDateTime getRecordedAt() { return recordedAt; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
