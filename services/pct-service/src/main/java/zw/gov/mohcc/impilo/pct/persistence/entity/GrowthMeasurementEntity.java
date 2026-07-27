package zw.gov.mohcc.impilo.pct.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * A single anthropometric contact: what was measured, under what conditions, and how it
 * scored against the growth standard at the time it was taken.
 *
 * <p>Scores are stored, not recomputed on read, so an offline measurement carries its own
 * interpretation and a later revision of the standard cannot silently restate history.
 * The standard identifier and engine version travel with the scores for that reason.</p>
 */
@Entity
@Table(name = "pct_growth_measurements")
public class GrowthMeasurementEntity {

    @Id
    @Column(name = "growth_id")
    private UUID growthId;

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

    @Column(name = "measured_at", nullable = false)
    private OffsetDateTime measuredAt;

    @Column(name = "recorded_by", nullable = false)
    private String recordedBy;

    @Column(name = "weight_kg")
    private BigDecimal weightKg;

    @Column(name = "length_cm")
    private BigDecimal lengthCm;

    @Column(name = "height_cm")
    private BigDecimal heightCm;

    @Column(name = "head_circumference_cm")
    private BigDecimal headCircumferenceCm;

    @Column(name = "muac_cm")
    private BigDecimal muacCm;

    @Column(name = "bmi")
    private BigDecimal bmi;

    @Column(name = "oedema", nullable = false)
    private String oedema = "NOT_ASSESSED";

    @Column(name = "measurement_mode")
    private String measurementMode;

    @Column(name = "posture")
    private String posture;

    @Column(name = "equipment_ref")
    private String equipmentRef;

    @Column(name = "clothing")
    private String clothing;

    @Column(name = "observer_cadre")
    private String observerCadre;

    @Column(name = "measurement_confidence", nullable = false)
    private String measurementConfidence = "MEASURED";

    @Column(name = "age_days")
    private Integer ageDays;

    @Column(name = "corrected_age_days")
    private Integer correctedAgeDays;

    @Column(name = "corrected_age_applied", nullable = false)
    private boolean correctedAgeApplied;

    @Column(name = "gestational_age_weeks")
    private Integer gestationalAgeWeeks;

    @Column(name = "gestational_age_source")
    private String gestationalAgeSource;

    @Column(name = "postmenstrual_age_weeks")
    private Integer postmenstrualAgeWeeks;

    @Column(name = "scoring_gaps", columnDefinition = "jsonb")
    private String scoringGaps;

    @Column(name = "weight_for_age_z")
    private BigDecimal weightForAgeZ;

    @Column(name = "length_height_for_age_z")
    private BigDecimal lengthHeightForAgeZ;

    @Column(name = "bmi_for_age_z")
    private BigDecimal bmiForAgeZ;

    @Column(name = "head_circumference_for_age_z")
    private BigDecimal headCircumferenceForAgeZ;

    @Column(name = "growth_standard")
    private String growthStandard;

    @Column(name = "growth_engine_version")
    private String growthEngineVersion;

    @Column(name = "scoring_note")
    private String scoringNote;

    @Column(name = "nutrition_status")
    private String nutritionStatus;

    @Column(name = "nutrition_content_version")
    private String nutritionContentVersion;

    @Column(name = "notes")
    private String notes;

    @Column(name = "created_at")
    private OffsetDateTime createdAt = OffsetDateTime.now();

    public UUID getGrowthId() {
        return growthId;
    }

    public void setGrowthId(UUID growthId) {
        this.growthId = growthId;
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

    public OffsetDateTime getMeasuredAt() {
        return measuredAt;
    }

    public void setMeasuredAt(OffsetDateTime measuredAt) {
        this.measuredAt = measuredAt;
    }

    public String getRecordedBy() {
        return recordedBy;
    }

    public void setRecordedBy(String recordedBy) {
        this.recordedBy = recordedBy;
    }

    public BigDecimal getWeightKg() {
        return weightKg;
    }

    public void setWeightKg(BigDecimal weightKg) {
        this.weightKg = weightKg;
    }

    public BigDecimal getLengthCm() {
        return lengthCm;
    }

    public void setLengthCm(BigDecimal lengthCm) {
        this.lengthCm = lengthCm;
    }

    public BigDecimal getHeightCm() {
        return heightCm;
    }

    public void setHeightCm(BigDecimal heightCm) {
        this.heightCm = heightCm;
    }

    public BigDecimal getHeadCircumferenceCm() {
        return headCircumferenceCm;
    }

    public void setHeadCircumferenceCm(BigDecimal headCircumferenceCm) {
        this.headCircumferenceCm = headCircumferenceCm;
    }

    public BigDecimal getMuacCm() {
        return muacCm;
    }

    public void setMuacCm(BigDecimal muacCm) {
        this.muacCm = muacCm;
    }

    public BigDecimal getBmi() {
        return bmi;
    }

    public void setBmi(BigDecimal bmi) {
        this.bmi = bmi;
    }

    public String getOedema() {
        return oedema;
    }

    public void setOedema(String oedema) {
        this.oedema = oedema;
    }

    public String getMeasurementMode() {
        return measurementMode;
    }

    public void setMeasurementMode(String measurementMode) {
        this.measurementMode = measurementMode;
    }

    public String getPosture() {
        return posture;
    }

    public void setPosture(String posture) {
        this.posture = posture;
    }

    public String getEquipmentRef() {
        return equipmentRef;
    }

    public void setEquipmentRef(String equipmentRef) {
        this.equipmentRef = equipmentRef;
    }

    public String getClothing() {
        return clothing;
    }

    public void setClothing(String clothing) {
        this.clothing = clothing;
    }

    public String getObserverCadre() {
        return observerCadre;
    }

    public void setObserverCadre(String observerCadre) {
        this.observerCadre = observerCadre;
    }

    public String getMeasurementConfidence() {
        return measurementConfidence;
    }

    public void setMeasurementConfidence(String measurementConfidence) {
        this.measurementConfidence = measurementConfidence;
    }

    public Integer getAgeDays() {
        return ageDays;
    }

    public void setAgeDays(Integer ageDays) {
        this.ageDays = ageDays;
    }

    public Integer getCorrectedAgeDays() {
        return correctedAgeDays;
    }

    public void setCorrectedAgeDays(Integer correctedAgeDays) {
        this.correctedAgeDays = correctedAgeDays;
    }

    public boolean isCorrectedAgeApplied() {
        return correctedAgeApplied;
    }

    public void setCorrectedAgeApplied(boolean correctedAgeApplied) {
        this.correctedAgeApplied = correctedAgeApplied;
    }

    public Integer getGestationalAgeWeeks() {
        return gestationalAgeWeeks;
    }

    public void setGestationalAgeWeeks(Integer gestationalAgeWeeks) {
        this.gestationalAgeWeeks = gestationalAgeWeeks;
    }

    public String getGestationalAgeSource() {
        return gestationalAgeSource;
    }

    public void setGestationalAgeSource(String gestationalAgeSource) {
        this.gestationalAgeSource = gestationalAgeSource;
    }

    public Integer getPostmenstrualAgeWeeks() {
        return postmenstrualAgeWeeks;
    }

    public void setPostmenstrualAgeWeeks(Integer postmenstrualAgeWeeks) {
        this.postmenstrualAgeWeeks = postmenstrualAgeWeeks;
    }

    public String getScoringGaps() {
        return scoringGaps;
    }

    public void setScoringGaps(String scoringGaps) {
        this.scoringGaps = scoringGaps;
    }

    public BigDecimal getWeightForAgeZ() {
        return weightForAgeZ;
    }

    public void setWeightForAgeZ(BigDecimal weightForAgeZ) {
        this.weightForAgeZ = weightForAgeZ;
    }

    public BigDecimal getLengthHeightForAgeZ() {
        return lengthHeightForAgeZ;
    }

    public void setLengthHeightForAgeZ(BigDecimal lengthHeightForAgeZ) {
        this.lengthHeightForAgeZ = lengthHeightForAgeZ;
    }

    public BigDecimal getBmiForAgeZ() {
        return bmiForAgeZ;
    }

    public void setBmiForAgeZ(BigDecimal bmiForAgeZ) {
        this.bmiForAgeZ = bmiForAgeZ;
    }

    public BigDecimal getHeadCircumferenceForAgeZ() {
        return headCircumferenceForAgeZ;
    }

    public void setHeadCircumferenceForAgeZ(BigDecimal headCircumferenceForAgeZ) {
        this.headCircumferenceForAgeZ = headCircumferenceForAgeZ;
    }

    public String getGrowthStandard() {
        return growthStandard;
    }

    public void setGrowthStandard(String growthStandard) {
        this.growthStandard = growthStandard;
    }

    public String getGrowthEngineVersion() {
        return growthEngineVersion;
    }

    public void setGrowthEngineVersion(String growthEngineVersion) {
        this.growthEngineVersion = growthEngineVersion;
    }

    public String getScoringNote() {
        return scoringNote;
    }

    public void setScoringNote(String scoringNote) {
        this.scoringNote = scoringNote;
    }

    public String getNutritionStatus() {
        return nutritionStatus;
    }

    public void setNutritionStatus(String nutritionStatus) {
        this.nutritionStatus = nutritionStatus;
    }

    public String getNutritionContentVersion() {
        return nutritionContentVersion;
    }

    public void setNutritionContentVersion(String nutritionContentVersion) {
        this.nutritionContentVersion = nutritionContentVersion;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
