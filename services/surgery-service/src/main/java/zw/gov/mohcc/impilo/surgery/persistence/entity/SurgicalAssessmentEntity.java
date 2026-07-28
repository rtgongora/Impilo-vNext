package zw.gov.mohcc.impilo.surgery.persistence.entity;

import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * The general surgical assessment (S2, surgical domain pack §5) — one row per
 * {@code surgical_episode}, refined over time rather than versioned. See V003's own header for
 * which elements are new content versus a reviewed-flag-plus-reference into an existing
 * registry (allergies, medications, functional status, pregnancy, imaging, pathology, previous
 * surgery) this table deliberately does NOT duplicate.
 */
@Entity
@Table(name = "surgical_assessment", schema = "surgery")
public class SurgicalAssessmentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "surgical_episode_id", nullable = false)
    private UUID surgicalEpisodeId;

    @Column(name = "presenting_problem", columnDefinition = "text")
    private String presentingProblem;

    @Column(name = "symptom_timeline", columnDefinition = "text")
    private String symptomTimeline;

    @Column(name = "wound_healing_history", columnDefinition = "text")
    private String woundHealingHistory;

    @Column(name = "bleeding_thrombosis_history", columnDefinition = "text")
    private String bleedingThrombosisHistory;

    @Column(name = "infection_history", columnDefinition = "text")
    private String infectionHistory;

    @Column(name = "anaesthetic_history_notes", columnDefinition = "text")
    private String anaestheticHistoryNotes;

    @Column(name = "nutrition_assessment", columnDefinition = "text")
    private String nutritionAssessment;

    @Column(name = "frailty_notes", columnDefinition = "text")
    private String frailtyNotes;

    @Column(name = "social_support_notes", columnDefinition = "text")
    private String socialSupportNotes;

    @Column(name = "transport_plan", columnDefinition = "text")
    private String transportPlan;

    @Column(name = "work_livelihood_impact", columnDefinition = "text")
    private String workLivelihoodImpact;

    @Column(name = "patient_goals", columnDefinition = "text")
    private String patientGoals;

    @Column(name = "examination_findings", columnDefinition = "text")
    private String examinationFindings;

    @Column(name = "differential_diagnosis_notes", columnDefinition = "text")
    private String differentialDiagnosisNotes;

    @Column(name = "surgical_risk_assessment", columnDefinition = "text")
    private String surgicalRiskAssessment;

    @Column(name = "previous_surgery_reviewed", nullable = false)
    private boolean previousSurgeryReviewed;

    @Column(name = "previous_surgery_notes", columnDefinition = "text")
    private String previousSurgeryNotes;

    @Column(name = "allergies_reviewed", nullable = false)
    private boolean allergiesReviewed;

    @Column(name = "allergy_review_notes", columnDefinition = "text")
    private String allergyReviewNotes;

    @Column(name = "medications_reviewed", nullable = false)
    private boolean medicationsReviewed;

    @Column(name = "medication_reconciliation_notes", columnDefinition = "text")
    private String medicationReconciliationNotes;

    @Column(name = "functional_status_reviewed", nullable = false)
    private boolean functionalStatusReviewed;

    @Column(name = "functional_assessment_ref")
    private UUID functionalAssessmentRef;

    @Column(name = "pregnancy_status_confirmed", nullable = false)
    private boolean pregnancyStatusConfirmed;

    @Column(name = "pregnancy_episode_ref")
    private UUID pregnancyEpisodeRef;

    @Column(name = "imaging_reviewed", nullable = false)
    private boolean imagingReviewed;

    @Column(name = "imaging_ref")
    private String imagingRef;

    @Column(name = "pathology_reviewed", nullable = false)
    private boolean pathologyReviewed;

    @Column(name = "pathology_ref")
    private String pathologyRef;

    @Column(name = "assessed_by")
    private String assessedBy;

    @Column(name = "assessed_at")
    private OffsetDateTime assessedAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    void prePersist() {
        OffsetDateTime now = OffsetDateTime.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID v) { this.id = v; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID v) { this.tenantId = v; }
    public UUID getSurgicalEpisodeId() { return surgicalEpisodeId; }
    public void setSurgicalEpisodeId(UUID v) { this.surgicalEpisodeId = v; }
    public String getPresentingProblem() { return presentingProblem; }
    public void setPresentingProblem(String v) { this.presentingProblem = v; }
    public String getSymptomTimeline() { return symptomTimeline; }
    public void setSymptomTimeline(String v) { this.symptomTimeline = v; }
    public String getWoundHealingHistory() { return woundHealingHistory; }
    public void setWoundHealingHistory(String v) { this.woundHealingHistory = v; }
    public String getBleedingThrombosisHistory() { return bleedingThrombosisHistory; }
    public void setBleedingThrombosisHistory(String v) { this.bleedingThrombosisHistory = v; }
    public String getInfectionHistory() { return infectionHistory; }
    public void setInfectionHistory(String v) { this.infectionHistory = v; }
    public String getAnaestheticHistoryNotes() { return anaestheticHistoryNotes; }
    public void setAnaestheticHistoryNotes(String v) { this.anaestheticHistoryNotes = v; }
    public String getNutritionAssessment() { return nutritionAssessment; }
    public void setNutritionAssessment(String v) { this.nutritionAssessment = v; }
    public String getFrailtyNotes() { return frailtyNotes; }
    public void setFrailtyNotes(String v) { this.frailtyNotes = v; }
    public String getSocialSupportNotes() { return socialSupportNotes; }
    public void setSocialSupportNotes(String v) { this.socialSupportNotes = v; }
    public String getTransportPlan() { return transportPlan; }
    public void setTransportPlan(String v) { this.transportPlan = v; }
    public String getWorkLivelihoodImpact() { return workLivelihoodImpact; }
    public void setWorkLivelihoodImpact(String v) { this.workLivelihoodImpact = v; }
    public String getPatientGoals() { return patientGoals; }
    public void setPatientGoals(String v) { this.patientGoals = v; }
    public String getExaminationFindings() { return examinationFindings; }
    public void setExaminationFindings(String v) { this.examinationFindings = v; }
    public String getDifferentialDiagnosisNotes() { return differentialDiagnosisNotes; }
    public void setDifferentialDiagnosisNotes(String v) { this.differentialDiagnosisNotes = v; }
    public String getSurgicalRiskAssessment() { return surgicalRiskAssessment; }
    public void setSurgicalRiskAssessment(String v) { this.surgicalRiskAssessment = v; }
    public boolean isPreviousSurgeryReviewed() { return previousSurgeryReviewed; }
    public void setPreviousSurgeryReviewed(boolean v) { this.previousSurgeryReviewed = v; }
    public String getPreviousSurgeryNotes() { return previousSurgeryNotes; }
    public void setPreviousSurgeryNotes(String v) { this.previousSurgeryNotes = v; }
    public boolean isAllergiesReviewed() { return allergiesReviewed; }
    public void setAllergiesReviewed(boolean v) { this.allergiesReviewed = v; }
    public String getAllergyReviewNotes() { return allergyReviewNotes; }
    public void setAllergyReviewNotes(String v) { this.allergyReviewNotes = v; }
    public boolean isMedicationsReviewed() { return medicationsReviewed; }
    public void setMedicationsReviewed(boolean v) { this.medicationsReviewed = v; }
    public String getMedicationReconciliationNotes() { return medicationReconciliationNotes; }
    public void setMedicationReconciliationNotes(String v) { this.medicationReconciliationNotes = v; }
    public boolean isFunctionalStatusReviewed() { return functionalStatusReviewed; }
    public void setFunctionalStatusReviewed(boolean v) { this.functionalStatusReviewed = v; }
    public UUID getFunctionalAssessmentRef() { return functionalAssessmentRef; }
    public void setFunctionalAssessmentRef(UUID v) { this.functionalAssessmentRef = v; }
    public boolean isPregnancyStatusConfirmed() { return pregnancyStatusConfirmed; }
    public void setPregnancyStatusConfirmed(boolean v) { this.pregnancyStatusConfirmed = v; }
    public UUID getPregnancyEpisodeRef() { return pregnancyEpisodeRef; }
    public void setPregnancyEpisodeRef(UUID v) { this.pregnancyEpisodeRef = v; }
    public boolean isImagingReviewed() { return imagingReviewed; }
    public void setImagingReviewed(boolean v) { this.imagingReviewed = v; }
    public String getImagingRef() { return imagingRef; }
    public void setImagingRef(String v) { this.imagingRef = v; }
    public boolean isPathologyReviewed() { return pathologyReviewed; }
    public void setPathologyReviewed(boolean v) { this.pathologyReviewed = v; }
    public String getPathologyRef() { return pathologyRef; }
    public void setPathologyRef(String v) { this.pathologyRef = v; }
    public String getAssessedBy() { return assessedBy; }
    public void setAssessedBy(String v) { this.assessedBy = v; }
    public OffsetDateTime getAssessedAt() { return assessedAt; }
    public void setAssessedAt(OffsetDateTime v) { this.assessedAt = v; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
