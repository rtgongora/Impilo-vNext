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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;
import zw.gov.mohcc.impilo.telemonitoring.domain.AlertSeverity;
import zw.gov.mohcc.impilo.telemonitoring.domain.AlertStatus;
import zw.gov.mohcc.impilo.telemonitoring.domain.AlertTriggerClass;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * AlertEpisode aggregate (OF-B26, §14.6 minimum schema). One clinical situation = one
 * episode: {@code live_guard} is the storm/race guard — a synthetic key present while
 * the episode is live and NULLed on resolution, with a DB UNIQUE constraint so two
 * concurrent evaluators cannot open sibling episodes for the same situation
 * (the {@code device_active_guard} idiom from OF-B24):
 *
 * <ul>
 *   <li>clinical episodes — {@code PLAN:{planId}:CLINICAL} (related rules on the same
 *       patient group into ONE episode, §14.6 grouping rule);</li>
 *   <li>device episodes — {@code DEV:{deviceRef}:{DEVICE_OFFLINE|DEVICE_QUALITY}}
 *       (§14.6 rate limit: one device-quality episode max per device; storms attach).</li>
 * </ul>
 *
 * <p>Accountable closure (§14.6 invariant): RESOLVED requires reviewer + assessment +
 * action + outcome, refused otherwise; EMERGENCY episodes additionally require a
 * recorded assumption-of-care note (journey #61 rung-15 law).</p>
 */
@Entity
@Table(name = "tm_alert_episodes", schema = "telemonitoring",
        uniqueConstraints = @UniqueConstraint(name = "uq_tm_alert_live_guard", columnNames = {"live_guard"}))
public class AlertEpisodeEntity {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    /** NULL for device episodes raised from unattributed (quarantined) readings. */
    @Column(name = "plan_id")
    private UUID planId;

    @Column(name = "patient_cpid", length = 64)
    private String patientCpid;

    @Column(name = "device_ref", length = 64)
    private String deviceRef;

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_class", nullable = false, length = 32)
    private AlertTriggerClass triggerClass;

    @Column(name = "metric_code", length = 64)
    private String metricCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "initial_severity", nullable = false, length = 16)
    private AlertSeverity initialSeverity;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false, length = 16)
    private AlertSeverity severity;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 24)
    private AlertStatus status = AlertStatus.OPEN;

    /** §14.6 low-trust ceiling honesty stamp: severity was capped at AMBER because every contributing reading was degraded/low-trust. */
    @Column(name = "severity_ceiling_applied", nullable = false)
    private boolean severityCeilingApplied;

    /** Storm/race guard — see class doc. NULL once resolved. */
    @Column(name = "live_guard", length = 128)
    private String liveGuard;

    /** JSON array of contributing reading refs (readingId, metricCode, value, readingStatus, trust, measuredAt, band). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "contributing_readings", nullable = false, columnDefinition = "jsonb")
    private String contributingReadings = "[]";

    /** JSON array of ladder entries: {rung, at, actor, action, outcome} (§14.7). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "ladder_history", nullable = false, columnDefinition = "jsonb")
    private String ladderHistory = "[]";

    @Column(name = "current_rung")
    private Integer currentRung;

    /** Breaches attached to this episode (repeat-abnormal counter, trigger 5). */
    @Column(name = "breach_count", nullable = false)
    private int breachCount;

    @Column(name = "last_breach_at")
    private OffsetDateTime lastBreachAt;

    /** §14.6 acknowledgement deadline for the CURRENT severity. */
    @Column(name = "response_deadline_at")
    private OffsetDateTime responseDeadlineAt;

    @Column(name = "acknowledged_by", length = 128)
    private String acknowledgedBy;

    @Column(name = "acknowledged_at")
    private OffsetDateTime acknowledgedAt;

    /** Escalation-on-silence audit (§14.6): set when the overdue sweep auto-escalated. */
    @Column(name = "auto_escalated_at")
    private OffsetDateTime autoEscalatedAt;

    /** Rung-4 CHW task best-effort dispatch outcome (honest flag; outbox event is the durable seam). */
    @Column(name = "chw_task_dispatched")
    private Boolean chwTaskDispatched;

    /** Rung-6/7 teleconsult case ref (Vol I front door, origin=MONITORING_ALERT). */
    @Column(name = "review_referral_ref", length = 64)
    private String reviewReferralRef;

    /** Rung-11+ Daidzai EMS incident ref (distinct identifier namespace, §5.3). */
    @Column(name = "ems_incident_ref", length = 64)
    private String emsIncidentRef;

    @Column(name = "ems_dispatched")
    private Boolean emsDispatched;

    /** Journey #61 law: EMERGENCY episodes cannot RESOLVE until physical care assumed responsibility. */
    @Column(name = "assumption_of_care_note", length = 1024)
    private String assumptionOfCareNote;

    @Column(name = "assumption_of_care_by", length = 128)
    private String assumptionOfCareBy;

    @Column(name = "assumption_of_care_at")
    private OffsetDateTime assumptionOfCareAt;

    // ── Accountable closure (§14.6: all four or no closure) ──

    @Column(name = "resolved_by", length = 128)
    private String resolvedBy;

    @Column(name = "assessment", length = 2048)
    private String assessment;

    @Column(name = "action_taken", length = 1024)
    private String actionTaken;

    @Column(name = "outcome", length = 128)
    private String outcome;

    @Column(name = "resolved_at")
    private OffsetDateTime resolvedAt;

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
    public String getPatientCpid() { return patientCpid; }
    public void setPatientCpid(String patientCpid) { this.patientCpid = patientCpid; }
    public String getDeviceRef() { return deviceRef; }
    public void setDeviceRef(String deviceRef) { this.deviceRef = deviceRef; }
    public AlertTriggerClass getTriggerClass() { return triggerClass; }
    public void setTriggerClass(AlertTriggerClass triggerClass) { this.triggerClass = triggerClass; }
    public String getMetricCode() { return metricCode; }
    public void setMetricCode(String metricCode) { this.metricCode = metricCode; }
    public AlertSeverity getInitialSeverity() { return initialSeverity; }
    public void setInitialSeverity(AlertSeverity initialSeverity) { this.initialSeverity = initialSeverity; }
    public AlertSeverity getSeverity() { return severity; }
    public void setSeverity(AlertSeverity severity) { this.severity = severity; }
    public AlertStatus getStatus() { return status; }
    public void setStatus(AlertStatus status) { this.status = status; }
    public boolean isSeverityCeilingApplied() { return severityCeilingApplied; }
    public void setSeverityCeilingApplied(boolean severityCeilingApplied) { this.severityCeilingApplied = severityCeilingApplied; }
    public String getLiveGuard() { return liveGuard; }
    public void setLiveGuard(String liveGuard) { this.liveGuard = liveGuard; }
    public String getContributingReadings() { return contributingReadings; }
    public void setContributingReadings(String contributingReadings) { this.contributingReadings = contributingReadings; }
    public String getLadderHistory() { return ladderHistory; }
    public void setLadderHistory(String ladderHistory) { this.ladderHistory = ladderHistory; }
    public Integer getCurrentRung() { return currentRung; }
    public void setCurrentRung(Integer currentRung) { this.currentRung = currentRung; }
    public int getBreachCount() { return breachCount; }
    public void setBreachCount(int breachCount) { this.breachCount = breachCount; }
    public OffsetDateTime getLastBreachAt() { return lastBreachAt; }
    public void setLastBreachAt(OffsetDateTime lastBreachAt) { this.lastBreachAt = lastBreachAt; }
    public OffsetDateTime getResponseDeadlineAt() { return responseDeadlineAt; }
    public void setResponseDeadlineAt(OffsetDateTime responseDeadlineAt) { this.responseDeadlineAt = responseDeadlineAt; }
    public String getAcknowledgedBy() { return acknowledgedBy; }
    public void setAcknowledgedBy(String acknowledgedBy) { this.acknowledgedBy = acknowledgedBy; }
    public OffsetDateTime getAcknowledgedAt() { return acknowledgedAt; }
    public void setAcknowledgedAt(OffsetDateTime acknowledgedAt) { this.acknowledgedAt = acknowledgedAt; }
    public OffsetDateTime getAutoEscalatedAt() { return autoEscalatedAt; }
    public void setAutoEscalatedAt(OffsetDateTime autoEscalatedAt) { this.autoEscalatedAt = autoEscalatedAt; }
    public Boolean getChwTaskDispatched() { return chwTaskDispatched; }
    public void setChwTaskDispatched(Boolean chwTaskDispatched) { this.chwTaskDispatched = chwTaskDispatched; }
    public String getReviewReferralRef() { return reviewReferralRef; }
    public void setReviewReferralRef(String reviewReferralRef) { this.reviewReferralRef = reviewReferralRef; }
    public String getEmsIncidentRef() { return emsIncidentRef; }
    public void setEmsIncidentRef(String emsIncidentRef) { this.emsIncidentRef = emsIncidentRef; }
    public Boolean getEmsDispatched() { return emsDispatched; }
    public void setEmsDispatched(Boolean emsDispatched) { this.emsDispatched = emsDispatched; }
    public String getAssumptionOfCareNote() { return assumptionOfCareNote; }
    public void setAssumptionOfCareNote(String assumptionOfCareNote) { this.assumptionOfCareNote = assumptionOfCareNote; }
    public String getAssumptionOfCareBy() { return assumptionOfCareBy; }
    public void setAssumptionOfCareBy(String assumptionOfCareBy) { this.assumptionOfCareBy = assumptionOfCareBy; }
    public OffsetDateTime getAssumptionOfCareAt() { return assumptionOfCareAt; }
    public void setAssumptionOfCareAt(OffsetDateTime assumptionOfCareAt) { this.assumptionOfCareAt = assumptionOfCareAt; }
    public String getResolvedBy() { return resolvedBy; }
    public void setResolvedBy(String resolvedBy) { this.resolvedBy = resolvedBy; }
    public String getAssessment() { return assessment; }
    public void setAssessment(String assessment) { this.assessment = assessment; }
    public String getActionTaken() { return actionTaken; }
    public void setActionTaken(String actionTaken) { this.actionTaken = actionTaken; }
    public String getOutcome() { return outcome; }
    public void setOutcome(String outcome) { this.outcome = outcome; }
    public OffsetDateTime getResolvedAt() { return resolvedAt; }
    public void setResolvedAt(OffsetDateTime resolvedAt) { this.resolvedAt = resolvedAt; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
