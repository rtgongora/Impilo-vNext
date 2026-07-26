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
 * One contact in a treatment episode — attended or missed.
 *
 * <p>A missed review is a row, not the absence of one. Recording absence by writing nothing makes
 * "did not come" indistinguishable from "not due yet", and that difference is the whole of
 * defaulter tracing.</p>
 *
 * <p>{@code dischargeEligible} and {@code dangerSignsPresent} are nullable on purpose. Null means
 * the question was not answered — the knowledge platform was unreachable, or nobody assessed the
 * child — and is never read as a negative.</p>
 */
@Entity
@Table(name = "pct_imam_visits")
public class ImamVisitEntity {

    @Id
    @Column(name = "imam_visit_id")
    private UUID imamVisitId;

    @Column(name = "imam_episode_id", nullable = false)
    private UUID imamEpisodeId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "subject_cpid", nullable = false)
    private String subjectCpid;

    @Column(name = "encounter_id")
    private String encounterId;

    @Column(name = "facility_id")
    private UUID facilityId;

    @Column(name = "visit_number", nullable = false)
    private Integer visitNumber;

    @Column(name = "scheduled_for")
    private LocalDate scheduledFor;

    @Column(name = "attended", nullable = false)
    private boolean attended = true;

    @Column(name = "visit_date")
    private OffsetDateTime visitDate;

    @Column(name = "recorded_by", nullable = false)
    private String recordedBy;

    @Column(name = "muac_cm")
    private BigDecimal muacCm;

    @Column(name = "weight_kg")
    private BigDecimal weightKg;

    @Column(name = "height_cm")
    private BigDecimal heightCm;

    @Column(name = "weight_for_height_z")
    private BigDecimal weightForHeightZ;

    @Column(name = "oedema", nullable = false)
    private String oedema = "NOT_ASSESSED";

    @Column(name = "appetite_test")
    private String appetiteTest;

    @Column(name = "danger_signs_present")
    private Boolean dangerSignsPresent;

    @Column(name = "rutf_product_code")
    private String rutfProductCode;

    @Column(name = "rutf_sachets_issued")
    private BigDecimal rutfSachetsIssued;

    @Column(name = "rutf_sachets_recommended")
    private BigDecimal rutfSachetsRecommended;

    @Column(name = "clinical_note")
    private String clinicalNote;

    @Column(name = "next_review_due")
    private LocalDate nextReviewDue;

    @Column(name = "discharge_eligible")
    private Boolean dischargeEligible;

    @Column(name = "attendance_status")
    private String attendanceStatus;

    @Column(name = "response_status")
    private String responseStatus;

    @Column(name = "assessment_note")
    private String assessmentNote;

    @Column(name = "assessment_content_version")
    private String assessmentContentVersion;

    @Column(name = "created_at")
    private OffsetDateTime createdAt = OffsetDateTime.now();

    public UUID getImamVisitId() {
        return imamVisitId;
    }

    public void setImamVisitId(UUID imamVisitId) {
        this.imamVisitId = imamVisitId;
    }

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

    public Integer getVisitNumber() {
        return visitNumber;
    }

    public void setVisitNumber(Integer visitNumber) {
        this.visitNumber = visitNumber;
    }

    public LocalDate getScheduledFor() {
        return scheduledFor;
    }

    public void setScheduledFor(LocalDate scheduledFor) {
        this.scheduledFor = scheduledFor;
    }

    public boolean isAttended() {
        return attended;
    }

    public void setAttended(boolean attended) {
        this.attended = attended;
    }

    public OffsetDateTime getVisitDate() {
        return visitDate;
    }

    public void setVisitDate(OffsetDateTime visitDate) {
        this.visitDate = visitDate;
    }

    public String getRecordedBy() {
        return recordedBy;
    }

    public void setRecordedBy(String recordedBy) {
        this.recordedBy = recordedBy;
    }

    public BigDecimal getMuacCm() {
        return muacCm;
    }

    public void setMuacCm(BigDecimal muacCm) {
        this.muacCm = muacCm;
    }

    public BigDecimal getWeightKg() {
        return weightKg;
    }

    public void setWeightKg(BigDecimal weightKg) {
        this.weightKg = weightKg;
    }

    public BigDecimal getHeightCm() {
        return heightCm;
    }

    public void setHeightCm(BigDecimal heightCm) {
        this.heightCm = heightCm;
    }

    public BigDecimal getWeightForHeightZ() {
        return weightForHeightZ;
    }

    public void setWeightForHeightZ(BigDecimal weightForHeightZ) {
        this.weightForHeightZ = weightForHeightZ;
    }

    public String getOedema() {
        return oedema;
    }

    public void setOedema(String oedema) {
        this.oedema = oedema;
    }

    public String getAppetiteTest() {
        return appetiteTest;
    }

    public void setAppetiteTest(String appetiteTest) {
        this.appetiteTest = appetiteTest;
    }

    public Boolean getDangerSignsPresent() {
        return dangerSignsPresent;
    }

    public void setDangerSignsPresent(Boolean dangerSignsPresent) {
        this.dangerSignsPresent = dangerSignsPresent;
    }

    public String getRutfProductCode() {
        return rutfProductCode;
    }

    public void setRutfProductCode(String rutfProductCode) {
        this.rutfProductCode = rutfProductCode;
    }

    public BigDecimal getRutfSachetsIssued() {
        return rutfSachetsIssued;
    }

    public void setRutfSachetsIssued(BigDecimal rutfSachetsIssued) {
        this.rutfSachetsIssued = rutfSachetsIssued;
    }

    public BigDecimal getRutfSachetsRecommended() {
        return rutfSachetsRecommended;
    }

    public void setRutfSachetsRecommended(BigDecimal rutfSachetsRecommended) {
        this.rutfSachetsRecommended = rutfSachetsRecommended;
    }

    public String getClinicalNote() {
        return clinicalNote;
    }

    public void setClinicalNote(String clinicalNote) {
        this.clinicalNote = clinicalNote;
    }

    public LocalDate getNextReviewDue() {
        return nextReviewDue;
    }

    public void setNextReviewDue(LocalDate nextReviewDue) {
        this.nextReviewDue = nextReviewDue;
    }

    public Boolean getDischargeEligible() {
        return dischargeEligible;
    }

    public void setDischargeEligible(Boolean dischargeEligible) {
        this.dischargeEligible = dischargeEligible;
    }

    public String getAttendanceStatus() {
        return attendanceStatus;
    }

    public void setAttendanceStatus(String attendanceStatus) {
        this.attendanceStatus = attendanceStatus;
    }

    public String getResponseStatus() {
        return responseStatus;
    }

    public void setResponseStatus(String responseStatus) {
        this.responseStatus = responseStatus;
    }

    public String getAssessmentNote() {
        return assessmentNote;
    }

    public void setAssessmentNote(String assessmentNote) {
        this.assessmentNote = assessmentNote;
    }

    public String getAssessmentContentVersion() {
        return assessmentContentVersion;
    }

    public void setAssessmentContentVersion(String assessmentContentVersion) {
        this.assessmentContentVersion = assessmentContentVersion;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
