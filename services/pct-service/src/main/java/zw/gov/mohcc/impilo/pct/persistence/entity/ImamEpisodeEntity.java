package zw.gov.mohcc.impilo.pct.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * A course of treatment for acute malnutrition: the programme the child was admitted to, the
 * measurements that admitted them, the schedule they are held to, and how the episode ended.
 *
 * <p>The admission and discharge anthropometry are stored as recorded, not recomputed on read. A
 * child admitted on an arm circumference of 11.2 cm was admitted on that number, and a later
 * revision of the cut-off must not retrospectively make the admission look unjustified.</p>
 */
@Entity
@Table(name = "pct_imam_episodes")
public class ImamEpisodeEntity {

    @Id
    @Column(name = "imam_episode_id")
    private UUID imamEpisodeId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "subject_cpid", nullable = false)
    private String subjectCpid;

    @Column(name = "journey_id")
    private String journeyId;

    @Column(name = "encounter_id")
    private String encounterId;

    @Column(name = "facility_id")
    private UUID facilityId;

    @Column(name = "programme", nullable = false)
    private String programme;

    @Column(name = "status", nullable = false)
    private String status = "ACTIVE";

    @Column(name = "enrolled_at", nullable = false)
    private OffsetDateTime enrolledAt;

    @Column(name = "enrolled_by", nullable = false)
    private String enrolledBy;

    @Column(name = "enrolment_source", nullable = false)
    private String enrolmentSource = "DIRECT";

    @Column(name = "source_classification")
    private String sourceClassification;

    @Column(name = "source_ref")
    private String sourceRef;

    @Column(name = "admission_criterion")
    private String admissionCriterion;

    @Column(name = "admission_age_days")
    private Integer admissionAgeDays;

    @Column(name = "admission_muac_cm")
    private BigDecimal admissionMuacCm;

    @Column(name = "admission_weight_kg")
    private BigDecimal admissionWeightKg;

    @Column(name = "admission_height_cm")
    private BigDecimal admissionHeightCm;

    @Column(name = "admission_weight_for_height_z")
    private BigDecimal admissionWeightForHeightZ;

    @Column(name = "admission_oedema", nullable = false)
    private String admissionOedema = "NOT_ASSESSED";

    @Column(name = "admission_appetite_test")
    private String admissionAppetiteTest;

    @Column(name = "admission_complications")
    private String admissionComplications;

    @Column(name = "content_pack_id")
    private String contentPackId;

    @Column(name = "content_version")
    private String contentVersion;

    @Column(name = "approval_status")
    private String approvalStatus;

    @Column(name = "review_interval_days", nullable = false)
    private Integer reviewIntervalDays = 7;

    @Column(name = "attendance_grace_days", nullable = false)
    private Integer attendanceGraceDays = 0;

    @Column(name = "trace_after_missed_visits", nullable = false)
    private Integer traceAfterMissedVisits = 1;

    @Column(name = "defaulter_missed_visits", nullable = false)
    private Integer defaulterMissedVisits = 2;

    @Column(name = "last_contact_on")
    private LocalDate lastContactOn;

    @Column(name = "next_review_due")
    private LocalDate nextReviewDue;

    @Column(name = "outcome")
    private String outcome;

    @Column(name = "outcome_at")
    private OffsetDateTime outcomeAt;

    @Column(name = "outcome_by")
    private String outcomeBy;

    @Column(name = "outcome_note")
    private String outcomeNote;

    @Column(name = "discharge_muac_cm")
    private BigDecimal dischargeMuacCm;

    @Column(name = "discharge_weight_kg")
    private BigDecimal dischargeWeightKg;

    @Column(name = "discharge_oedema")
    private String dischargeOedema;

    /** {@code null} means the criteria could not be evaluated — it never means "not met". */
    @Column(name = "discharge_criteria_met")
    private Boolean dischargeCriteriaMet;

    @Column(name = "discharge_criteria_detail")
    private String dischargeCriteriaDetail;

    @Column(name = "discharge_override_reason")
    private String dischargeOverrideReason;

    @Column(name = "created_at")
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    public UUID getImamEpisodeId() {
        return imamEpisodeId;
    }

    public void setImamEpisodeId(UUID imamEpisodeId) {
        this.imamEpisodeId = imamEpisodeId;
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

    public String getJourneyId() {
        return journeyId;
    }

    public void setJourneyId(String journeyId) {
        this.journeyId = journeyId;
    }

    public String getEncounterId() {
        return encounterId;
    }

    public void setEncounterId(String encounterId) {
        this.encounterId = encounterId;
    }

    public UUID getFacilityId() {
        return facilityId;
    }

    public void setFacilityId(UUID facilityId) {
        this.facilityId = facilityId;
    }

    public String getProgramme() {
        return programme;
    }

    public void setProgramme(String programme) {
        this.programme = programme;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public OffsetDateTime getEnrolledAt() {
        return enrolledAt;
    }

    public void setEnrolledAt(OffsetDateTime enrolledAt) {
        this.enrolledAt = enrolledAt;
    }

    public String getEnrolledBy() {
        return enrolledBy;
    }

    public void setEnrolledBy(String enrolledBy) {
        this.enrolledBy = enrolledBy;
    }

    public String getEnrolmentSource() {
        return enrolmentSource;
    }

    public void setEnrolmentSource(String enrolmentSource) {
        this.enrolmentSource = enrolmentSource;
    }

    public String getSourceClassification() {
        return sourceClassification;
    }

    public void setSourceClassification(String sourceClassification) {
        this.sourceClassification = sourceClassification;
    }

    public String getSourceRef() {
        return sourceRef;
    }

    public void setSourceRef(String sourceRef) {
        this.sourceRef = sourceRef;
    }

    public String getAdmissionCriterion() {
        return admissionCriterion;
    }

    public void setAdmissionCriterion(String admissionCriterion) {
        this.admissionCriterion = admissionCriterion;
    }

    public Integer getAdmissionAgeDays() {
        return admissionAgeDays;
    }

    public void setAdmissionAgeDays(Integer admissionAgeDays) {
        this.admissionAgeDays = admissionAgeDays;
    }

    public BigDecimal getAdmissionMuacCm() {
        return admissionMuacCm;
    }

    public void setAdmissionMuacCm(BigDecimal admissionMuacCm) {
        this.admissionMuacCm = admissionMuacCm;
    }

    public BigDecimal getAdmissionWeightKg() {
        return admissionWeightKg;
    }

    public void setAdmissionWeightKg(BigDecimal admissionWeightKg) {
        this.admissionWeightKg = admissionWeightKg;
    }

    public BigDecimal getAdmissionHeightCm() {
        return admissionHeightCm;
    }

    public void setAdmissionHeightCm(BigDecimal admissionHeightCm) {
        this.admissionHeightCm = admissionHeightCm;
    }

    public BigDecimal getAdmissionWeightForHeightZ() {
        return admissionWeightForHeightZ;
    }

    public void setAdmissionWeightForHeightZ(BigDecimal admissionWeightForHeightZ) {
        this.admissionWeightForHeightZ = admissionWeightForHeightZ;
    }

    public String getAdmissionOedema() {
        return admissionOedema;
    }

    public void setAdmissionOedema(String admissionOedema) {
        this.admissionOedema = admissionOedema;
    }

    public String getAdmissionAppetiteTest() {
        return admissionAppetiteTest;
    }

    public void setAdmissionAppetiteTest(String admissionAppetiteTest) {
        this.admissionAppetiteTest = admissionAppetiteTest;
    }

    public String getAdmissionComplications() {
        return admissionComplications;
    }

    public void setAdmissionComplications(String admissionComplications) {
        this.admissionComplications = admissionComplications;
    }

    public String getContentPackId() {
        return contentPackId;
    }

    public void setContentPackId(String contentPackId) {
        this.contentPackId = contentPackId;
    }

    public String getContentVersion() {
        return contentVersion;
    }

    public void setContentVersion(String contentVersion) {
        this.contentVersion = contentVersion;
    }

    public String getApprovalStatus() {
        return approvalStatus;
    }

    public void setApprovalStatus(String approvalStatus) {
        this.approvalStatus = approvalStatus;
    }

    public Integer getReviewIntervalDays() {
        return reviewIntervalDays;
    }

    public void setReviewIntervalDays(Integer reviewIntervalDays) {
        this.reviewIntervalDays = reviewIntervalDays;
    }

    public Integer getAttendanceGraceDays() {
        return attendanceGraceDays;
    }

    public void setAttendanceGraceDays(Integer attendanceGraceDays) {
        this.attendanceGraceDays = attendanceGraceDays;
    }

    public Integer getTraceAfterMissedVisits() {
        return traceAfterMissedVisits;
    }

    public void setTraceAfterMissedVisits(Integer traceAfterMissedVisits) {
        this.traceAfterMissedVisits = traceAfterMissedVisits;
    }

    public Integer getDefaulterMissedVisits() {
        return defaulterMissedVisits;
    }

    public void setDefaulterMissedVisits(Integer defaulterMissedVisits) {
        this.defaulterMissedVisits = defaulterMissedVisits;
    }

    public LocalDate getLastContactOn() {
        return lastContactOn;
    }

    public void setLastContactOn(LocalDate lastContactOn) {
        this.lastContactOn = lastContactOn;
    }

    public LocalDate getNextReviewDue() {
        return nextReviewDue;
    }

    public void setNextReviewDue(LocalDate nextReviewDue) {
        this.nextReviewDue = nextReviewDue;
    }

    public String getOutcome() {
        return outcome;
    }

    public void setOutcome(String outcome) {
        this.outcome = outcome;
    }

    public OffsetDateTime getOutcomeAt() {
        return outcomeAt;
    }

    public void setOutcomeAt(OffsetDateTime outcomeAt) {
        this.outcomeAt = outcomeAt;
    }

    public String getOutcomeBy() {
        return outcomeBy;
    }

    public void setOutcomeBy(String outcomeBy) {
        this.outcomeBy = outcomeBy;
    }

    public String getOutcomeNote() {
        return outcomeNote;
    }

    public void setOutcomeNote(String outcomeNote) {
        this.outcomeNote = outcomeNote;
    }

    public BigDecimal getDischargeMuacCm() {
        return dischargeMuacCm;
    }

    public void setDischargeMuacCm(BigDecimal dischargeMuacCm) {
        this.dischargeMuacCm = dischargeMuacCm;
    }

    public BigDecimal getDischargeWeightKg() {
        return dischargeWeightKg;
    }

    public void setDischargeWeightKg(BigDecimal dischargeWeightKg) {
        this.dischargeWeightKg = dischargeWeightKg;
    }

    public String getDischargeOedema() {
        return dischargeOedema;
    }

    public void setDischargeOedema(String dischargeOedema) {
        this.dischargeOedema = dischargeOedema;
    }

    public Boolean getDischargeCriteriaMet() {
        return dischargeCriteriaMet;
    }

    public void setDischargeCriteriaMet(Boolean dischargeCriteriaMet) {
        this.dischargeCriteriaMet = dischargeCriteriaMet;
    }

    public String getDischargeCriteriaDetail() {
        return dischargeCriteriaDetail;
    }

    public void setDischargeCriteriaDetail(String dischargeCriteriaDetail) {
        this.dischargeCriteriaDetail = dischargeCriteriaDetail;
    }

    public String getDischargeOverrideReason() {
        return dischargeOverrideReason;
    }

    public void setDischargeOverrideReason(String dischargeOverrideReason) {
        this.dischargeOverrideReason = dischargeOverrideReason;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
