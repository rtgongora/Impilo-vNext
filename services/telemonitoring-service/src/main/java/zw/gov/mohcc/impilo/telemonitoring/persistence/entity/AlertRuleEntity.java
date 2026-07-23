package zw.gov.mohcc.impilo.telemonitoring.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.UuidGenerator;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Alert-engine OVERRIDE configuration (OF-B26). Deliberately NOT a duplicate band
 * store: the metric bounds/bands that drive threshold evaluation are the plan's
 * CURRENT immutable threshold-profile version ({@code tm_threshold_profiles} — §14.3
 * field 10, already versioned/attributed/approved). This table carries only what the
 * profile vocabulary cannot express: repeat-abnormal windows, per-severity response
 * windows, offline heartbeat tolerance and the device-alert switch.
 *
 * <p>Resolution order (most specific wins, field-level): plan override →
 * programme default ({@code plan_id} NULL) → engine defaults (hardcoded in
 * {@code AlertRuleConfigService}). {@code UNIQUE(plan_id)} guards one override per
 * plan; programme rows are guarded per (tenant, programme_code) in the service.</p>
 */
@Entity
@Table(name = "tm_alert_rules", schema = "telemonitoring",
        uniqueConstraints = @UniqueConstraint(name = "uq_tm_alert_rules_plan", columnNames = {"plan_id"}))
public class AlertRuleEntity {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    /** Per-plan override; NULL for a programme-default row. */
    @Column(name = "plan_id")
    private UUID planId;

    /** Programme-default scope; NULL for a per-plan row. */
    @Column(name = "programme_code", length = 64)
    private String programmeCode;

    /** Trigger 5 — N breaches within the window escalate the live episode. */
    @Column(name = "repeat_abnormal_count")
    private Integer repeatAbnormalCount;

    @Column(name = "repeat_abnormal_window_minutes")
    private Integer repeatAbnormalWindowMinutes;

    /** §14.6 acknowledgement response windows, per severity. */
    @Column(name = "amber_response_minutes")
    private Integer amberResponseMinutes;

    @Column(name = "red_response_minutes")
    private Integer redResponseMinutes;

    @Column(name = "emergency_response_minutes")
    private Integer emergencyResponseMinutes;

    /** Trigger 8 — device silent beyond this tolerance (journey #65 default: 72h). */
    @Column(name = "offline_tolerance_hours")
    private Integer offlineToleranceHours;

    /** Trigger 9 — degraded/quarantined readings from one device within 24h before a device-quality episode opens. */
    @Column(name = "device_quality_min_count")
    private Integer deviceQualityMinCount;

    @Column(name = "device_alerts_enabled", nullable = false)
    private boolean deviceAlertsEnabled = true;

    @Column(name = "created_by", length = 128)
    private String createdBy;

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
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public UUID getPlanId() { return planId; }
    public void setPlanId(UUID planId) { this.planId = planId; }
    public String getProgrammeCode() { return programmeCode; }
    public void setProgrammeCode(String programmeCode) { this.programmeCode = programmeCode; }
    public Integer getRepeatAbnormalCount() { return repeatAbnormalCount; }
    public void setRepeatAbnormalCount(Integer repeatAbnormalCount) { this.repeatAbnormalCount = repeatAbnormalCount; }
    public Integer getRepeatAbnormalWindowMinutes() { return repeatAbnormalWindowMinutes; }
    public void setRepeatAbnormalWindowMinutes(Integer v) { this.repeatAbnormalWindowMinutes = v; }
    public Integer getAmberResponseMinutes() { return amberResponseMinutes; }
    public void setAmberResponseMinutes(Integer amberResponseMinutes) { this.amberResponseMinutes = amberResponseMinutes; }
    public Integer getRedResponseMinutes() { return redResponseMinutes; }
    public void setRedResponseMinutes(Integer redResponseMinutes) { this.redResponseMinutes = redResponseMinutes; }
    public Integer getEmergencyResponseMinutes() { return emergencyResponseMinutes; }
    public void setEmergencyResponseMinutes(Integer v) { this.emergencyResponseMinutes = v; }
    public Integer getOfflineToleranceHours() { return offlineToleranceHours; }
    public void setOfflineToleranceHours(Integer offlineToleranceHours) { this.offlineToleranceHours = offlineToleranceHours; }
    public Integer getDeviceQualityMinCount() { return deviceQualityMinCount; }
    public void setDeviceQualityMinCount(Integer deviceQualityMinCount) { this.deviceQualityMinCount = deviceQualityMinCount; }
    public boolean isDeviceAlertsEnabled() { return deviceAlertsEnabled; }
    public void setDeviceAlertsEnabled(boolean deviceAlertsEnabled) { this.deviceAlertsEnabled = deviceAlertsEnabled; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
