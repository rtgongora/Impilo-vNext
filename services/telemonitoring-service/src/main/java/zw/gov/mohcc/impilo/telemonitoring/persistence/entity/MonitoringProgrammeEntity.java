package zw.gov.mohcc.impilo.telemonitoring.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Programme profile catalogue row (§14.1) — a clinically governed template that plans
 * reference by {@code code}, never copy.
 */
@Entity
@Table(name = "tm_monitoring_programmes", schema = "telemonitoring",
        uniqueConstraints = @UniqueConstraint(name = "uq_tm_programme_code", columnNames = "code"))
public class MonitoringProgrammeEntity {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(name = "code", nullable = false, length = 64)
    private String code;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "condition_code", length = 64)
    private String conditionCode;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "default_thresholds", nullable = false, columnDefinition = "jsonb")
    private String defaultThresholds = "{}";

    @Column(name = "active", nullable = false)
    private boolean active = true;

    // ── Governance (OF-B21, V002): versioned metadata, effective windows, retire-not-delete ──

    @Column(name = "version", nullable = false)
    private int version = 1;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", nullable = false, columnDefinition = "jsonb")
    private String metadata = "{}";

    @Column(name = "effective_from")
    private OffsetDateTime effectiveFrom;

    @Column(name = "effective_to")
    private OffsetDateTime effectiveTo;

    @Column(name = "retired", nullable = false)
    private boolean retired = false;

    @Column(name = "retired_at")
    private OffsetDateTime retiredAt;

    @Column(name = "retired_by", length = 128)
    private String retiredBy;

    @Column(name = "retired_reason", length = 512)
    private String retiredReason;

    @Column(name = "updated_by", length = 128)
    private String updatedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getConditionCode() { return conditionCode; }
    public void setConditionCode(String conditionCode) { this.conditionCode = conditionCode; }
    public String getDefaultThresholds() { return defaultThresholds; }
    public void setDefaultThresholds(String defaultThresholds) { this.defaultThresholds = defaultThresholds; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public int getVersion() { return version; }
    public void setVersion(int version) { this.version = version; }
    public String getMetadata() { return metadata; }
    public void setMetadata(String metadata) { this.metadata = metadata; }
    public OffsetDateTime getEffectiveFrom() { return effectiveFrom; }
    public void setEffectiveFrom(OffsetDateTime effectiveFrom) { this.effectiveFrom = effectiveFrom; }
    public OffsetDateTime getEffectiveTo() { return effectiveTo; }
    public void setEffectiveTo(OffsetDateTime effectiveTo) { this.effectiveTo = effectiveTo; }
    public boolean isRetired() { return retired; }
    public void setRetired(boolean retired) { this.retired = retired; }
    public OffsetDateTime getRetiredAt() { return retiredAt; }
    public void setRetiredAt(OffsetDateTime retiredAt) { this.retiredAt = retiredAt; }
    public String getRetiredBy() { return retiredBy; }
    public void setRetiredBy(String retiredBy) { this.retiredBy = retiredBy; }
    public String getRetiredReason() { return retiredReason; }
    public void setRetiredReason(String retiredReason) { this.retiredReason = retiredReason; }
    public String getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
