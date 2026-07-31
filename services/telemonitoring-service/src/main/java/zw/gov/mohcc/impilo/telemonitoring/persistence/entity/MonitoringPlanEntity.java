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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;
import zw.gov.mohcc.impilo.telemonitoring.domain.ConsentStatus;
import zw.gov.mohcc.impilo.telemonitoring.domain.PlanStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * MonitoringPlan aggregate row (§14.3): per-patient, clinician-approved instantiation of a
 * programme profile. {@code orosOrderId} is the prescribed-plan invariant (§14.2 — the
 * OROS enrolment order spine ref); {@code suggestedBy}/{@code suggestedSystemVersion}
 * carry automated-suggestion provenance, and such plans can never self-activate.
 */
@Entity
@Table(name = "tm_monitoring_plans", schema = "telemonitoring")
public class MonitoringPlanEntity {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "patient_cpid", nullable = false, length = 64)
    private String patientCpid;

    @Column(name = "programme_code", nullable = false, length = 64)
    private String programmeCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private PlanStatus status = PlanStatus.DRAFT;

    @Column(name = "oros_order_id", length = 64)
    private String orosOrderId;

    @Column(name = "clinical_indication", length = 255)
    private String clinicalIndication;

    @Column(name = "monitoring_setting", length = 48)
    private String monitoringSetting;

    @Column(name = "suggested_by", length = 128)
    private String suggestedBy;

    @Column(name = "suggested_system_version", length = 64)
    private String suggestedSystemVersion;

    @Column(name = "suggested_at")
    private OffsetDateTime suggestedAt;

    @Column(name = "approved_by", length = 128)
    private String approvedBy;

    @Column(name = "approved_at")
    private OffsetDateTime approvedAt;

    @Column(name = "start_at")
    private OffsetDateTime startAt;

    @Column(name = "end_at")
    private OffsetDateTime endAt;

    @Column(name = "review_cadence", length = 48)
    private String reviewCadence;

    @Column(name = "review_due_at")
    private OffsetDateTime reviewDueAt;

    // ── OF-B21 (V002): MVUMO consent pointers — pointers ONLY, consent journey is MVUMO's ──

    @Column(name = "consent_reference", length = 128)
    private String consentReference;

    @Enumerated(EnumType.STRING)
    @Column(name = "consent_status", nullable = false, length = 32)
    private ConsentStatus consentStatus = ConsentStatus.UNVERIFIED;

    @Column(name = "consent_updated_at")
    private OffsetDateTime consentUpdatedAt;

    // ── OF-B21 (V002): review-cadence timer groundwork ──

    @Column(name = "last_review_at")
    private OffsetDateTime lastReviewAt;

    @Column(name = "review_due_notified_at")
    private OffsetDateTime reviewDueNotifiedAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "care_team", nullable = false, columnDefinition = "jsonb")
    private String careTeam = "{}";

    @Column(name = "lifecycle_reason", length = 512)
    private String lifecycleReason;

    // ── Wave 5.2: contribution anchor into pct_problems (pointer only — PCT is SoR) ──

    @Column(name = "pct_problem_ref")
    private UUID pctProblemRef;

    @Column(name = "pct_problem_contributed_at")
    private OffsetDateTime pctProblemContributedAt;

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
    public void setId(UUID id) { this.id = id; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public String getPatientCpid() { return patientCpid; }
    public void setPatientCpid(String patientCpid) { this.patientCpid = patientCpid; }
    public String getProgrammeCode() { return programmeCode; }
    public void setProgrammeCode(String programmeCode) { this.programmeCode = programmeCode; }
    public PlanStatus getStatus() { return status; }
    public void setStatus(PlanStatus status) { this.status = status; }
    public String getOrosOrderId() { return orosOrderId; }
    public void setOrosOrderId(String orosOrderId) { this.orosOrderId = orosOrderId; }
    public String getClinicalIndication() { return clinicalIndication; }
    public void setClinicalIndication(String clinicalIndication) { this.clinicalIndication = clinicalIndication; }
    public String getMonitoringSetting() { return monitoringSetting; }
    public void setMonitoringSetting(String monitoringSetting) { this.monitoringSetting = monitoringSetting; }
    public String getSuggestedBy() { return suggestedBy; }
    public void setSuggestedBy(String suggestedBy) { this.suggestedBy = suggestedBy; }
    public String getSuggestedSystemVersion() { return suggestedSystemVersion; }
    public void setSuggestedSystemVersion(String suggestedSystemVersion) { this.suggestedSystemVersion = suggestedSystemVersion; }
    public OffsetDateTime getSuggestedAt() { return suggestedAt; }
    public void setSuggestedAt(OffsetDateTime suggestedAt) { this.suggestedAt = suggestedAt; }
    public String getApprovedBy() { return approvedBy; }
    public void setApprovedBy(String approvedBy) { this.approvedBy = approvedBy; }
    public OffsetDateTime getApprovedAt() { return approvedAt; }
    public void setApprovedAt(OffsetDateTime approvedAt) { this.approvedAt = approvedAt; }
    public OffsetDateTime getStartAt() { return startAt; }
    public void setStartAt(OffsetDateTime startAt) { this.startAt = startAt; }
    public OffsetDateTime getEndAt() { return endAt; }
    public void setEndAt(OffsetDateTime endAt) { this.endAt = endAt; }
    public String getReviewCadence() { return reviewCadence; }
    public void setReviewCadence(String reviewCadence) { this.reviewCadence = reviewCadence; }
    public OffsetDateTime getReviewDueAt() { return reviewDueAt; }
    public void setReviewDueAt(OffsetDateTime reviewDueAt) { this.reviewDueAt = reviewDueAt; }
    public String getConsentReference() { return consentReference; }
    public void setConsentReference(String consentReference) { this.consentReference = consentReference; }
    public ConsentStatus getConsentStatus() { return consentStatus; }
    public void setConsentStatus(ConsentStatus consentStatus) { this.consentStatus = consentStatus; }
    public OffsetDateTime getConsentUpdatedAt() { return consentUpdatedAt; }
    public void setConsentUpdatedAt(OffsetDateTime consentUpdatedAt) { this.consentUpdatedAt = consentUpdatedAt; }
    public OffsetDateTime getLastReviewAt() { return lastReviewAt; }
    public void setLastReviewAt(OffsetDateTime lastReviewAt) { this.lastReviewAt = lastReviewAt; }
    public OffsetDateTime getReviewDueNotifiedAt() { return reviewDueNotifiedAt; }
    public void setReviewDueNotifiedAt(OffsetDateTime reviewDueNotifiedAt) { this.reviewDueNotifiedAt = reviewDueNotifiedAt; }
    public String getCareTeam() { return careTeam; }
    public void setCareTeam(String careTeam) { this.careTeam = careTeam; }
    public String getLifecycleReason() { return lifecycleReason; }
    public void setLifecycleReason(String lifecycleReason) { this.lifecycleReason = lifecycleReason; }
    public UUID getPctProblemRef() { return pctProblemRef; }
    public void setPctProblemRef(UUID pctProblemRef) { this.pctProblemRef = pctProblemRef; }
    public OffsetDateTime getPctProblemContributedAt() { return pctProblemContributedAt; }
    public void setPctProblemContributedAt(OffsetDateTime pctProblemContributedAt) {
        this.pctProblemContributedAt = pctProblemContributedAt;
    }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
