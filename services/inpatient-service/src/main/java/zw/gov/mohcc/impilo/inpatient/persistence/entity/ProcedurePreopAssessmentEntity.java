package zw.gov.mohcc.impilo.inpatient.persistence.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "procedure_preop_assessment", schema = "inpatient")
public class ProcedurePreopAssessmentEntity {

    @Id
    @Column(name = "assessment_id")
    private UUID assessmentId;

    @Column(name = "episode_id", nullable = false)
    private UUID episodeId;

    @Column(name = "assessment_type", nullable = false)
    private String assessmentType;

    @Column(name = "asa_class")
    private String asaClass;

    @Column(name = "airway_assessment")
    private String airwayAssessment;

    @Column(name = "mallampati_class")
    private String mallampatiClass;

    @Column(name = "cormack_lehane_grade")
    private String cormackLehaneGrade;

    @Column(name = "fasting_verified")
    private Boolean fastingVerified;

    @Column(name = "allergies_reviewed")
    private Boolean allergiesReviewed;

    @Column(name = "nursing_notes")
    private String nursingNotes;

    @Column(name = "anaesthetist_notes")
    private String anaesthetistNotes;

    @Column(name = "cleared_for_theatre", nullable = false)
    private boolean clearedForTheatre;

    @Column(name = "assessed_by")
    private String assessedBy;

    @Column(name = "assessed_at")
    private OffsetDateTime assessedAt;

    @PrePersist
    void onCreate() {
        if (assessmentId == null) assessmentId = UUID.randomUUID();
        if (assessedAt == null) assessedAt = OffsetDateTime.now();
    }

    public UUID getAssessmentId() { return assessmentId; }
    public void setAssessmentId(UUID assessmentId) { this.assessmentId = assessmentId; }
    public UUID getEpisodeId() { return episodeId; }
    public void setEpisodeId(UUID episodeId) { this.episodeId = episodeId; }
    public String getAssessmentType() { return assessmentType; }
    public void setAssessmentType(String assessmentType) { this.assessmentType = assessmentType; }
    public String getAsaClass() { return asaClass; }
    public void setAsaClass(String asaClass) { this.asaClass = asaClass; }
    public String getAirwayAssessment() { return airwayAssessment; }
    public void setAirwayAssessment(String airwayAssessment) { this.airwayAssessment = airwayAssessment; }
    public String getMallampatiClass() { return mallampatiClass; }
    public void setMallampatiClass(String mallampatiClass) { this.mallampatiClass = mallampatiClass; }
    public String getCormackLehaneGrade() { return cormackLehaneGrade; }
    public void setCormackLehaneGrade(String cormackLehaneGrade) { this.cormackLehaneGrade = cormackLehaneGrade; }
    public Boolean getFastingVerified() { return fastingVerified; }
    public void setFastingVerified(Boolean fastingVerified) { this.fastingVerified = fastingVerified; }
    public Boolean getAllergiesReviewed() { return allergiesReviewed; }
    public void setAllergiesReviewed(Boolean allergiesReviewed) { this.allergiesReviewed = allergiesReviewed; }
    public String getNursingNotes() { return nursingNotes; }
    public void setNursingNotes(String nursingNotes) { this.nursingNotes = nursingNotes; }
    public String getAnaesthetistNotes() { return anaesthetistNotes; }
    public void setAnaesthetistNotes(String anaesthetistNotes) { this.anaesthetistNotes = anaesthetistNotes; }
    public boolean isClearedForTheatre() { return clearedForTheatre; }
    public void setClearedForTheatre(boolean clearedForTheatre) { this.clearedForTheatre = clearedForTheatre; }
    public String getAssessedBy() { return assessedBy; }
    public void setAssessedBy(String assessedBy) { this.assessedBy = assessedBy; }
    public OffsetDateTime getAssessedAt() { return assessedAt; }
    public void setAssessedAt(OffsetDateTime assessedAt) { this.assessedAt = assessedAt; }
}
