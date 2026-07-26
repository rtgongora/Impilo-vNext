package zw.gov.mohcc.impilo.pct.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * A segment of one CTG channel: the samples themselves, stored as an array under a declared
 * sample rate and count rather than one row per sample. A 30-minute external trace at 4 Hz is
 * 7,200 points per channel, and the clinical unit of review is the segment, not the sample.
 *
 * <p>{@code missingSampleCount} records gaps rather than interpolating them. A transducer that
 * slipped for ninety seconds must read as ninety seconds of absent trace, never as a reassuring
 * flat line.</p>
 */
@Entity
@Table(name = "pct_ctg_chunks", schema = "pct")
public class CtgChunkEntity {

    @Id
    @Column(name = "chunk_id")
    private UUID chunkId;

    @Column(name = "session_id", nullable = false)
    private UUID sessionId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "subject_cpid", nullable = false)
    private String subjectCpid;

    @Column(name = "channel", nullable = false)
    private String channel;

    @Column(name = "started_at", nullable = false)
    private OffsetDateTime startedAt;

    @Column(name = "ended_at")
    private OffsetDateTime endedAt;

    @Column(name = "sample_rate_hz", nullable = false)
    private BigDecimal sampleRateHz;

    @Column(name = "sample_count", nullable = false)
    private Integer sampleCount;

    @Column(name = "duration_seconds", nullable = false)
    private BigDecimal durationSeconds;

    @Column(name = "unit", nullable = false)
    private String unit = "BPM";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "samples_json", nullable = false, columnDefinition = "jsonb")
    private String samplesJson;

    @Column(name = "missing_sample_count", nullable = false)
    private Integer missingSampleCount = 0;

    @Column(name = "captured_by")
    private String capturedBy;

    @Column(name = "device_id")
    private String deviceId;

    @Column(name = "notes")
    private String notes;

    @Column(name = "created_at")
    private OffsetDateTime createdAt = OffsetDateTime.now();

    public UUID getChunkId() {
        return chunkId;
    }

    public void setChunkId(UUID chunkId) {
        this.chunkId = chunkId;
    }

    public UUID getSessionId() {
        return sessionId;
    }

    public void setSessionId(UUID sessionId) {
        this.sessionId = sessionId;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }

    public String getSubjectCpid() {
        return subjectCpid;
    }

    public void setSubjectCpid(String subjectCpid) {
        this.subjectCpid = subjectCpid;
    }

    public String getChannel() {
        return channel;
    }

    public void setChannel(String channel) {
        this.channel = channel;
    }

    public OffsetDateTime getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(OffsetDateTime startedAt) {
        this.startedAt = startedAt;
    }

    public OffsetDateTime getEndedAt() {
        return endedAt;
    }

    public void setEndedAt(OffsetDateTime endedAt) {
        this.endedAt = endedAt;
    }

    public BigDecimal getSampleRateHz() {
        return sampleRateHz;
    }

    public void setSampleRateHz(BigDecimal sampleRateHz) {
        this.sampleRateHz = sampleRateHz;
    }

    public Integer getSampleCount() {
        return sampleCount;
    }

    public void setSampleCount(Integer sampleCount) {
        this.sampleCount = sampleCount;
    }

    public BigDecimal getDurationSeconds() {
        return durationSeconds;
    }

    public void setDurationSeconds(BigDecimal durationSeconds) {
        this.durationSeconds = durationSeconds;
    }

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    public String getSamplesJson() {
        return samplesJson;
    }

    public void setSamplesJson(String samplesJson) {
        this.samplesJson = samplesJson;
    }

    public Integer getMissingSampleCount() {
        return missingSampleCount;
    }

    public void setMissingSampleCount(Integer missingSampleCount) {
        this.missingSampleCount = missingSampleCount;
    }

    public String getCapturedBy() {
        return capturedBy;
    }

    public void setCapturedBy(String capturedBy) {
        this.capturedBy = capturedBy;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
