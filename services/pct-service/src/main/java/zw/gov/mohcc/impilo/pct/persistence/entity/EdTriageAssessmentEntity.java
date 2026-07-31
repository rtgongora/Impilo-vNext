package zw.gov.mohcc.impilo.pct.persistence.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "ed_triage_assessment")
public class EdTriageAssessmentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "visit_id", nullable = false)
    private UUID visitId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "acuity", nullable = false)
    private int acuity;

    @Column(name = "triage_system", nullable = false)
    private String triageSystem = "IMPILO_5";

    @Column(name = "chief_complaint", columnDefinition = "TEXT")
    private String chiefComplaint;

    @Column(name = "presenting_problem_code")
    private String presentingProblemCode;

    @Column(name = "pain_score")
    private Integer painScore;

    // These five columns are JSONB in V012 and V202. Postgres will not accept a varchar bind for
    // a jsonb column, so a triage row could not be written at all without this — see the same
    // fix on EmergencyEpisodeEntity.entryContextJson.
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "vitals", columnDefinition = "jsonb")
    private String vitalsJson = "{}";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "danger_signs", columnDefinition = "jsonb")
    private String dangerSignsJson = "[]";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "discriminators", columnDefinition = "jsonb")
    private String discriminatorsJson = "{}";

    @Column(name = "news2_score")
    private Integer news2Score;

    @Column(name = "fast_track", nullable = false)
    private boolean fastTrack;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "triaged_by", nullable = false)
    private String triagedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    // --- V202: WHO IITT authority of record (ESI/MTS demoted to advisory). ---

    /** WHO_IITT when IITT set the acuity of record; ESI/MTS/IMPILO_5 for a manually-entered acuity. */
    @Column(name = "triage_tool")
    private String triageTool;

    /** RED/YELLOW/GREEN — the IITT tier. Null on a manual row. NOT_TRIAGEABLE is never stored (it is a 422). */
    @Column(name = "iitt_priority")
    private String iittPriority;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "emergency_signs_json", columnDefinition = "jsonb")
    private String emergencySignsJson;

    /** IITT clinical-concern up-triage flag, or a raised cross-system discrepancy — a human decides, not a silent up-triage. */
    @Column(name = "requires_clinician_review", nullable = false)
    private boolean requiresClinicianReview;

    @Column(name = "review_reason", columnDefinition = "TEXT")
    private String reviewReason;

    /** ESI/MTS advisory scores, retained but never the acuity of record. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "advisory_scores_json", columnDefinition = "jsonb")
    private String advisoryScoresJson;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
    }

    public Long getId() { return id; }
    public UUID getVisitId() { return visitId; }
    public void setVisitId(UUID visitId) { this.visitId = visitId; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public int getAcuity() { return acuity; }
    public void setAcuity(int acuity) { this.acuity = acuity; }
    public String getTriageSystem() { return triageSystem; }
    public void setTriageSystem(String triageSystem) { this.triageSystem = triageSystem; }
    public String getChiefComplaint() { return chiefComplaint; }
    public void setChiefComplaint(String chiefComplaint) { this.chiefComplaint = chiefComplaint; }
    public String getPresentingProblemCode() { return presentingProblemCode; }
    public void setPresentingProblemCode(String presentingProblemCode) { this.presentingProblemCode = presentingProblemCode; }
    public Integer getPainScore() { return painScore; }
    public void setPainScore(Integer painScore) { this.painScore = painScore; }
    public String getVitalsJson() { return vitalsJson; }
    public void setVitalsJson(String vitalsJson) { this.vitalsJson = vitalsJson; }
    public String getDangerSignsJson() { return dangerSignsJson; }
    public void setDangerSignsJson(String dangerSignsJson) { this.dangerSignsJson = dangerSignsJson; }
    public String getDiscriminatorsJson() { return discriminatorsJson; }
    public void setDiscriminatorsJson(String discriminatorsJson) { this.discriminatorsJson = discriminatorsJson; }
    public Integer getNews2Score() { return news2Score; }
    public void setNews2Score(Integer news2Score) { this.news2Score = news2Score; }
    public boolean isFastTrack() { return fastTrack; }
    public void setFastTrack(boolean fastTrack) { this.fastTrack = fastTrack; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public String getTriagedBy() { return triagedBy; }
    public void setTriagedBy(String triagedBy) { this.triagedBy = triagedBy; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public String getTriageTool() { return triageTool; }
    public void setTriageTool(String triageTool) { this.triageTool = triageTool; }
    public String getIittPriority() { return iittPriority; }
    public void setIittPriority(String iittPriority) { this.iittPriority = iittPriority; }
    public String getEmergencySignsJson() { return emergencySignsJson; }
    public void setEmergencySignsJson(String emergencySignsJson) { this.emergencySignsJson = emergencySignsJson; }
    public boolean isRequiresClinicianReview() { return requiresClinicianReview; }
    public void setRequiresClinicianReview(boolean requiresClinicianReview) { this.requiresClinicianReview = requiresClinicianReview; }
    public String getReviewReason() { return reviewReason; }
    public void setReviewReason(String reviewReason) { this.reviewReason = reviewReason; }
    public String getAdvisoryScoresJson() { return advisoryScoresJson; }
    public void setAdvisoryScoresJson(String advisoryScoresJson) { this.advisoryScoresJson = advisoryScoresJson; }
}
