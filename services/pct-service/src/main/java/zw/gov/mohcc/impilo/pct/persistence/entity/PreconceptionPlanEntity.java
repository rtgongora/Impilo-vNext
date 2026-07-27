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
 * Care before conception.
 *
 * <p><b>Dates, not flags.</b> Almost everything of value in preconception care only works if it
 * happened before conception: folic acid taken after the neural tube has closed at about 28 days has
 * missed the thing it was for, and the same is true of stopping a teratogen or optimising diabetes.
 * A boolean "folic acid given" cannot tell you whether it was given in time, and a plan reporting
 * full completion while every action happened too late is worse than one that reports honestly.
 */
@Entity
@Table(name = "pct_preconception_plans")
public class PreconceptionPlanEntity {

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_COMPLETED = "COMPLETED";
    public static final String STATUS_SUPERSEDED = "SUPERSEDED";
    public static final String STATUS_CLOSED = "CLOSED";
    public static final String STATUS_ENTERED_IN_ERROR = "ENTERED_IN_ERROR";

    public static final String TERATOGEN_FOUND = "FOUND";
    public static final String TERATOGEN_NONE_FOUND = "NONE_FOUND";
    public static final String TERATOGEN_NOT_ASSESSED = "NOT_ASSESSED";

    @Id
    @Column(name = "preconception_plan_id")
    private UUID preconceptionPlanId;

    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(name = "subject_cpid")
    private String subjectCpid;

    @Column(name = "status")
    private String status;

    @Column(name = "opened_on")
    private LocalDate openedOn;

    @Column(name = "closed_on")
    private LocalDate closedOn;

    @Column(name = "closure_reason")
    private String closureReason;

    @Column(name = "folic_acid_started_on")
    private LocalDate folicAcidStartedOn;

    @Column(name = "folic_acid_dose_mg")
    private BigDecimal folicAcidDoseMg;

    @Column(name = "folic_acid_high_dose_reason")
    private String folicAcidHighDoseReason;

    @Column(name = "teratogenic_medication_review_on")
    private LocalDate teratogenicMedicationReviewOn;

    @Column(name = "teratogenic_medication_found")
    private String teratogenicMedicationFound;

    @Column(name = "teratogenic_medication_detail")
    private String teratogenicMedicationDetail;

    @Column(name = "teratogenic_medication_action")
    private String teratogenicMedicationAction;

    @Column(name = "chronic_conditions_reviewed_on")
    private LocalDate chronicConditionsReviewedOn;

    @Column(name = "diabetes_control_optimised")
    private String diabetesControlOptimised;

    @Column(name = "hypertension_control_optimised")
    private String hypertensionControlOptimised;

    @Column(name = "rubella_immunity_status")
    private String rubellaImmunityStatus;

    @Column(name = "rubella_vaccinated_on")
    private LocalDate rubellaVaccinatedOn;

    @Column(name = "tetanus_status")
    private String tetanusStatus;

    @Column(name = "hiv_status_known")
    private String hivStatusKnown;

    @Column(name = "hiv_art_optimised")
    private String hivArtOptimised;

    @Column(name = "partner_hiv_status_known")
    private String partnerHivStatusKnown;

    @Column(name = "substance_use_reviewed_on")
    private LocalDate substanceUseReviewedOn;

    @Column(name = "alcohol_use")
    private String alcoholUse;

    @Column(name = "tobacco_use")
    private String tobaccoUse;

    @Column(name = "bmi")
    private BigDecimal bmi;

    @Column(name = "anaemia_screened_on")
    private LocalDate anaemiaScreenedOn;

    @Column(name = "haemoglobin_g_dl")
    private BigDecimal haemoglobinGDl;

    @Column(name = "genetic_or_family_risk")
    private String geneticOrFamilyRisk;

    @Column(name = "genetic_risk_detail")
    private String geneticRiskDetail;

    @Column(name = "resulting_pregnancy_episode_id")
    private UUID resultingPregnancyEpisodeId;

    @Column(name = "journey_id")
    private UUID journeyId;

    @Column(name = "encounter_ref")
    private String encounterRef;

    @Column(name = "facility_id")
    private UUID facilityId;

    @Column(name = "recorded_by")
    private String recordedBy;

    @Column(name = "recorded_at")
    private OffsetDateTime recordedAt;

    @Column(name = "updated_by")
    private String updatedBy;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @Column(name = "client_offline_id")
    private String clientOfflineId;

    public UUID getPreconceptionPlanId() {
        return preconceptionPlanId;
    }

    public void setPreconceptionPlanId(UUID preconceptionPlanId) {
        this.preconceptionPlanId = preconceptionPlanId;
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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDate getOpenedOn() {
        return openedOn;
    }

    public void setOpenedOn(LocalDate openedOn) {
        this.openedOn = openedOn;
    }

    public LocalDate getClosedOn() {
        return closedOn;
    }

    public void setClosedOn(LocalDate closedOn) {
        this.closedOn = closedOn;
    }

    public String getClosureReason() {
        return closureReason;
    }

    public void setClosureReason(String closureReason) {
        this.closureReason = closureReason;
    }

    public LocalDate getFolicAcidStartedOn() {
        return folicAcidStartedOn;
    }

    public void setFolicAcidStartedOn(LocalDate folicAcidStartedOn) {
        this.folicAcidStartedOn = folicAcidStartedOn;
    }

    public BigDecimal getFolicAcidDoseMg() {
        return folicAcidDoseMg;
    }

    public void setFolicAcidDoseMg(BigDecimal folicAcidDoseMg) {
        this.folicAcidDoseMg = folicAcidDoseMg;
    }

    public String getFolicAcidHighDoseReason() {
        return folicAcidHighDoseReason;
    }

    public void setFolicAcidHighDoseReason(String folicAcidHighDoseReason) {
        this.folicAcidHighDoseReason = folicAcidHighDoseReason;
    }

    public LocalDate getTeratogenicMedicationReviewOn() {
        return teratogenicMedicationReviewOn;
    }

    public void setTeratogenicMedicationReviewOn(LocalDate teratogenicMedicationReviewOn) {
        this.teratogenicMedicationReviewOn = teratogenicMedicationReviewOn;
    }

    public String getTeratogenicMedicationFound() {
        return teratogenicMedicationFound;
    }

    public void setTeratogenicMedicationFound(String teratogenicMedicationFound) {
        this.teratogenicMedicationFound = teratogenicMedicationFound;
    }

    public String getTeratogenicMedicationDetail() {
        return teratogenicMedicationDetail;
    }

    public void setTeratogenicMedicationDetail(String teratogenicMedicationDetail) {
        this.teratogenicMedicationDetail = teratogenicMedicationDetail;
    }

    public String getTeratogenicMedicationAction() {
        return teratogenicMedicationAction;
    }

    public void setTeratogenicMedicationAction(String teratogenicMedicationAction) {
        this.teratogenicMedicationAction = teratogenicMedicationAction;
    }

    public LocalDate getChronicConditionsReviewedOn() {
        return chronicConditionsReviewedOn;
    }

    public void setChronicConditionsReviewedOn(LocalDate chronicConditionsReviewedOn) {
        this.chronicConditionsReviewedOn = chronicConditionsReviewedOn;
    }

    public String getDiabetesControlOptimised() {
        return diabetesControlOptimised;
    }

    public void setDiabetesControlOptimised(String diabetesControlOptimised) {
        this.diabetesControlOptimised = diabetesControlOptimised;
    }

    public String getHypertensionControlOptimised() {
        return hypertensionControlOptimised;
    }

    public void setHypertensionControlOptimised(String hypertensionControlOptimised) {
        this.hypertensionControlOptimised = hypertensionControlOptimised;
    }

    public String getRubellaImmunityStatus() {
        return rubellaImmunityStatus;
    }

    public void setRubellaImmunityStatus(String rubellaImmunityStatus) {
        this.rubellaImmunityStatus = rubellaImmunityStatus;
    }

    public LocalDate getRubellaVaccinatedOn() {
        return rubellaVaccinatedOn;
    }

    public void setRubellaVaccinatedOn(LocalDate rubellaVaccinatedOn) {
        this.rubellaVaccinatedOn = rubellaVaccinatedOn;
    }

    public String getTetanusStatus() {
        return tetanusStatus;
    }

    public void setTetanusStatus(String tetanusStatus) {
        this.tetanusStatus = tetanusStatus;
    }

    public String getHivStatusKnown() {
        return hivStatusKnown;
    }

    public void setHivStatusKnown(String hivStatusKnown) {
        this.hivStatusKnown = hivStatusKnown;
    }

    public String getHivArtOptimised() {
        return hivArtOptimised;
    }

    public void setHivArtOptimised(String hivArtOptimised) {
        this.hivArtOptimised = hivArtOptimised;
    }

    public String getPartnerHivStatusKnown() {
        return partnerHivStatusKnown;
    }

    public void setPartnerHivStatusKnown(String partnerHivStatusKnown) {
        this.partnerHivStatusKnown = partnerHivStatusKnown;
    }

    public LocalDate getSubstanceUseReviewedOn() {
        return substanceUseReviewedOn;
    }

    public void setSubstanceUseReviewedOn(LocalDate substanceUseReviewedOn) {
        this.substanceUseReviewedOn = substanceUseReviewedOn;
    }

    public String getAlcoholUse() {
        return alcoholUse;
    }

    public void setAlcoholUse(String alcoholUse) {
        this.alcoholUse = alcoholUse;
    }

    public String getTobaccoUse() {
        return tobaccoUse;
    }

    public void setTobaccoUse(String tobaccoUse) {
        this.tobaccoUse = tobaccoUse;
    }

    public BigDecimal getBmi() {
        return bmi;
    }

    public void setBmi(BigDecimal bmi) {
        this.bmi = bmi;
    }

    public LocalDate getAnaemiaScreenedOn() {
        return anaemiaScreenedOn;
    }

    public void setAnaemiaScreenedOn(LocalDate anaemiaScreenedOn) {
        this.anaemiaScreenedOn = anaemiaScreenedOn;
    }

    public BigDecimal getHaemoglobinGDl() {
        return haemoglobinGDl;
    }

    public void setHaemoglobinGDl(BigDecimal haemoglobinGDl) {
        this.haemoglobinGDl = haemoglobinGDl;
    }

    public String getGeneticOrFamilyRisk() {
        return geneticOrFamilyRisk;
    }

    public void setGeneticOrFamilyRisk(String geneticOrFamilyRisk) {
        this.geneticOrFamilyRisk = geneticOrFamilyRisk;
    }

    public String getGeneticRiskDetail() {
        return geneticRiskDetail;
    }

    public void setGeneticRiskDetail(String geneticRiskDetail) {
        this.geneticRiskDetail = geneticRiskDetail;
    }

    public UUID getResultingPregnancyEpisodeId() {
        return resultingPregnancyEpisodeId;
    }

    public void setResultingPregnancyEpisodeId(UUID resultingPregnancyEpisodeId) {
        this.resultingPregnancyEpisodeId = resultingPregnancyEpisodeId;
    }

    public UUID getJourneyId() {
        return journeyId;
    }

    public void setJourneyId(UUID journeyId) {
        this.journeyId = journeyId;
    }

    public String getEncounterRef() {
        return encounterRef;
    }

    public void setEncounterRef(String encounterRef) {
        this.encounterRef = encounterRef;
    }

    public UUID getFacilityId() {
        return facilityId;
    }

    public void setFacilityId(UUID facilityId) {
        this.facilityId = facilityId;
    }

    public String getRecordedBy() {
        return recordedBy;
    }

    public void setRecordedBy(String recordedBy) {
        this.recordedBy = recordedBy;
    }

    public OffsetDateTime getRecordedAt() {
        return recordedAt;
    }

    public void setRecordedAt(OffsetDateTime recordedAt) {
        this.recordedAt = recordedAt;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(String updatedBy) {
        this.updatedBy = updatedBy;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getClientOfflineId() {
        return clientOfflineId;
    }

    public void setClientOfflineId(String clientOfflineId) {
        this.clientOfflineId = clientOfflineId;
    }
}
