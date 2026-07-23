package zw.gov.mohcc.impilo.telemonitoring.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.UuidGenerator;
import zw.gov.mohcc.impilo.telemonitoring.domain.ReadingStatus;
import zw.gov.mohcc.impilo.telemonitoring.domain.ShrWriteState;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Clinical-lane copy of one consumed telemetry reading plus its quarantine and
 * single-writer SHR posture (OF-B25, §14.5 steps 11–14, V004).
 *
 * <p><b>Journey #62 invariant:</b> for every QUARANTINED row {@code patientCpid},
 * {@code planId} and {@code assignmentId} stay NULL — an unattributable reading must
 * never carry an attribution it could later be projected under. The evidence trail
 * (device ref, claimed subject, reason) lives in {@code qualityStamp}.</p>
 *
 * <p><b>Journey #64 invariant:</b> {@code dedupKey} is UNIQUE — replayed bus messages
 * collapse to one row; the DB constraint backstops the pre-flight check.</p>
 */
@Entity
@Table(name = "tm_readings", schema = "telemonitoring",
        uniqueConstraints = @UniqueConstraint(name = "uq_tm_readings_dedup",
                columnNames = {"dedup_key"}))
public class TelemetryReadingEntity {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    /** iot-ingestion's reading_id — provenance link from step 10 to step 11. */
    @Column(name = "source_reading_id", length = 64)
    private String sourceReadingId;

    @Column(name = "device_ref", nullable = false, length = 64)
    private String deviceRef;

    @Column(name = "assignment_id")
    private UUID assignmentId;

    @Column(name = "plan_id")
    private UUID planId;

    /** CPID-only (R25 rule); NULL for every QUARANTINED row. */
    @Column(name = "patient_cpid", length = 64)
    private String patientCpid;

    @Column(name = "metric_code", nullable = false, length = 64)
    private String metricCode;

    @Column(name = "metric_value", precision = 14, scale = 4)
    private BigDecimal metricValue;

    @Column(name = "unit", length = 32)
    private String unit;

    /** Original device timestamp — never overwritten (§15.8 duty 3). */
    @Column(name = "measured_at", nullable = false)
    private OffsetDateTime measuredAt;

    @Column(name = "received_at", nullable = false)
    private OffsetDateTime receivedAt;

    @Column(name = "source", length = 64)
    private String source;

    /** {trustLevel, calibrationPosture, calibrationReason, source, ...} — travels into the Observation. */
    @Column(name = "quality_stamp", columnDefinition = "jsonb")
    private String qualityStamp;

    @Column(name = "dedup_key", nullable = false, length = 64)
    private String dedupKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 24)
    private ReadingStatus status;

    @Column(name = "quarantine_reason", length = 48)
    private String quarantineReason;

    @Enumerated(EnumType.STRING)
    @Column(name = "shr_write_state", nullable = false, length = 24)
    private ShrWriteState shrWriteState = ShrWriteState.NOT_ELIGIBLE;

    @Column(name = "shr_write_attempts", nullable = false)
    private int shrWriteAttempts;

    @Column(name = "shr_last_write_error", length = 255)
    private String shrLastWriteError;

    /** Set ONLY on gateway outcome SUCCESS (honesty law). */
    @Column(name = "shr_written_at")
    private OffsetDateTime shrWrittenAt;

    @Column(name = "shr_ref", length = 128)
    private String shrRef;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    void prePersist() {
        OffsetDateTime now = OffsetDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (receivedAt == null) {
            receivedAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public String getSourceReadingId() { return sourceReadingId; }
    public void setSourceReadingId(String sourceReadingId) { this.sourceReadingId = sourceReadingId; }
    public String getDeviceRef() { return deviceRef; }
    public void setDeviceRef(String deviceRef) { this.deviceRef = deviceRef; }
    public UUID getAssignmentId() { return assignmentId; }
    public void setAssignmentId(UUID assignmentId) { this.assignmentId = assignmentId; }
    public UUID getPlanId() { return planId; }
    public void setPlanId(UUID planId) { this.planId = planId; }
    public String getPatientCpid() { return patientCpid; }
    public void setPatientCpid(String patientCpid) { this.patientCpid = patientCpid; }
    public String getMetricCode() { return metricCode; }
    public void setMetricCode(String metricCode) { this.metricCode = metricCode; }
    public BigDecimal getMetricValue() { return metricValue; }
    public void setMetricValue(BigDecimal metricValue) { this.metricValue = metricValue; }
    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }
    public OffsetDateTime getMeasuredAt() { return measuredAt; }
    public void setMeasuredAt(OffsetDateTime measuredAt) { this.measuredAt = measuredAt; }
    public OffsetDateTime getReceivedAt() { return receivedAt; }
    public void setReceivedAt(OffsetDateTime receivedAt) { this.receivedAt = receivedAt; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public String getQualityStamp() { return qualityStamp; }
    public void setQualityStamp(String qualityStamp) { this.qualityStamp = qualityStamp; }
    public String getDedupKey() { return dedupKey; }
    public void setDedupKey(String dedupKey) { this.dedupKey = dedupKey; }
    public ReadingStatus getStatus() { return status; }
    public void setStatus(ReadingStatus status) { this.status = status; }
    public String getQuarantineReason() { return quarantineReason; }
    public void setQuarantineReason(String quarantineReason) { this.quarantineReason = quarantineReason; }
    public ShrWriteState getShrWriteState() { return shrWriteState; }
    public void setShrWriteState(ShrWriteState shrWriteState) { this.shrWriteState = shrWriteState; }
    public int getShrWriteAttempts() { return shrWriteAttempts; }
    public void setShrWriteAttempts(int shrWriteAttempts) { this.shrWriteAttempts = shrWriteAttempts; }
    public String getShrLastWriteError() { return shrLastWriteError; }
    public void setShrLastWriteError(String shrLastWriteError) { this.shrLastWriteError = shrLastWriteError; }
    public OffsetDateTime getShrWrittenAt() { return shrWrittenAt; }
    public void setShrWrittenAt(OffsetDateTime shrWrittenAt) { this.shrWrittenAt = shrWrittenAt; }
    public String getShrRef() { return shrRef; }
    public void setShrRef(String shrRef) { this.shrRef = shrRef; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
