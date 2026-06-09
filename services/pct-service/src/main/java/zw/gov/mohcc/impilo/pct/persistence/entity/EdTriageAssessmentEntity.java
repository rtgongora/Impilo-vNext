package zw.gov.mohcc.impilo.pct.persistence.entity;

import jakarta.persistence.*;
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

    @Column(name = "vitals", columnDefinition = "jsonb")
    private String vitalsJson = "{}";

    @Column(name = "danger_signs", columnDefinition = "jsonb")
    private String dangerSignsJson = "[]";

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
}
