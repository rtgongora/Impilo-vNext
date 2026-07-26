package zw.gov.mohcc.impilo.pct.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * A newborn's clinical birth summary, keyed to the baby's own CPID.
 *
 * <p>This is the child's anchor in the clinical record. VITO still owns identity and the
 * mother-baby relationship, UBOMI still owns the civil birth notification, and
 * inpatient-service still owns the theatre episode and APGAR — what this adds is the
 * child-anchored clinical view none of them holds, so that later writes about the baby
 * resolve to the baby.</p>
 */
@Entity
@Table(name = "pct_newborn_birth_records")
public class NewbornBirthRecordEntity {

    public static final String SOURCE_THEATRE = "THEATRE";
    public static final String SOURCE_MATERNITY_FORM = "MATERNITY_FORM";
    public static final String SOURCE_MANUAL = "MANUAL";

    @Id
    @Column(name = "birth_record_id")
    private UUID birthRecordId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "subject_cpid", nullable = false)
    private String subjectCpid;

    @Column(name = "mother_cpid")
    private String motherCpid;

    @Column(name = "facility_id")
    private UUID facilityId;

    @Column(name = "born_at")
    private OffsetDateTime bornAt;

    @Column(name = "sex_at_birth")
    private String sexAtBirth;

    @Column(name = "gestational_age_weeks")
    private Integer gestationalAgeWeeks;

    @Column(name = "gestational_age_basis")
    private String gestationalAgeBasis;

    @Column(name = "birth_weight_grams")
    private Integer birthWeightGrams;

    @Column(name = "birth_length_cm")
    private BigDecimal birthLengthCm;

    @Column(name = "head_circumference_cm")
    private BigDecimal headCircumferenceCm;

    @Column(name = "birth_order")
    private Integer birthOrder;

    @Column(name = "multiple_birth", nullable = false)
    private boolean multipleBirth;

    @Column(name = "delivery_mode")
    private String deliveryMode;

    @Column(name = "place_of_birth")
    private String placeOfBirth;

    @Column(name = "birth_attendant")
    private String birthAttendant;

    @Column(name = "birth_outcome", nullable = false)
    private String birthOutcome = "LIVE_BIRTH";

    @Column(name = "apgar_one_minute")
    private Integer apgarOneMinute;

    @Column(name = "apgar_five_minute")
    private Integer apgarFiveMinute;

    @Column(name = "apgar_ten_minute")
    private Integer apgarTenMinute;

    @Column(name = "resuscitation")
    private String resuscitation;

    @Column(name = "resuscitation_response")
    private String resuscitationResponse;

    @Column(name = "skin_to_skin", nullable = false)
    private String skinToSkin = "NOT_ASSESSED";

    @Column(name = "minutes_to_first_breastfeed")
    private Integer minutesToFirstBreastfeed;

    @Column(name = "vitamin_k_given", nullable = false)
    private String vitaminKGiven = "NOT_ASSESSED";

    @Column(name = "eye_prophylaxis_given", nullable = false)
    private String eyeProphylaxisGiven = "NOT_ASSESSED";

    @Column(name = "cord_care")
    private String cordCare;

    @Column(name = "temperature_celsius")
    private BigDecimal temperatureCelsius;

    @Column(name = "hiv_exposure", nullable = false)
    private String hivExposure = "UNKNOWN";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "maternal_risk_factors")
    private String maternalRiskFactors;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "newborn_examination")
    private String newbornExamination;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "congenital_anomalies")
    private String congenitalAnomalies;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "birth_trauma")
    private String birthTrauma;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "danger_signs")
    private String dangerSigns;

    @Column(name = "admission_destination")
    private String admissionDestination;

    @Column(name = "delivery_source", nullable = false)
    private String deliverySource = SOURCE_MANUAL;

    @Column(name = "delivery_source_ref")
    private String deliverySourceRef;

    @Column(name = "ubomi_notification_ref")
    private String ubomiNotificationRef;

    @Column(name = "journey_id")
    private String journeyId;

    @Column(name = "encounter_id")
    private String encounterId;

    @Column(name = "recorded_by", nullable = false)
    private String recordedBy;

    @Column(name = "created_at")
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    /** Below 37 completed weeks; drives corrected age for every later growth reading. */
    public boolean isPreterm() {
        return gestationalAgeWeeks != null && gestationalAgeWeeks > 0 && gestationalAgeWeeks < 37;
    }

    /** Below 2500 g, the threshold at which a newborn needs added thermal and feeding support. */
    public boolean isLowBirthWeight() {
        return birthWeightGrams != null && birthWeightGrams > 0 && birthWeightGrams < 2500;
    }

    public UUID getBirthRecordId() {
        return birthRecordId;
    }

    public void setBirthRecordId(UUID birthRecordId) {
        this.birthRecordId = birthRecordId;
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

    public String getMotherCpid() {
        return motherCpid;
    }

    public void setMotherCpid(String motherCpid) {
        this.motherCpid = motherCpid;
    }

    public UUID getFacilityId() {
        return facilityId;
    }

    public void setFacilityId(UUID facilityId) {
        this.facilityId = facilityId;
    }

    public OffsetDateTime getBornAt() {
        return bornAt;
    }

    public void setBornAt(OffsetDateTime bornAt) {
        this.bornAt = bornAt;
    }

    public String getSexAtBirth() {
        return sexAtBirth;
    }

    public void setSexAtBirth(String sexAtBirth) {
        this.sexAtBirth = sexAtBirth;
    }

    public Integer getGestationalAgeWeeks() {
        return gestationalAgeWeeks;
    }

    public void setGestationalAgeWeeks(Integer gestationalAgeWeeks) {
        this.gestationalAgeWeeks = gestationalAgeWeeks;
    }

    public String getGestationalAgeBasis() {
        return gestationalAgeBasis;
    }

    public void setGestationalAgeBasis(String gestationalAgeBasis) {
        this.gestationalAgeBasis = gestationalAgeBasis;
    }

    public Integer getBirthWeightGrams() {
        return birthWeightGrams;
    }

    public void setBirthWeightGrams(Integer birthWeightGrams) {
        this.birthWeightGrams = birthWeightGrams;
    }

    public BigDecimal getBirthLengthCm() {
        return birthLengthCm;
    }

    public void setBirthLengthCm(BigDecimal birthLengthCm) {
        this.birthLengthCm = birthLengthCm;
    }

    public BigDecimal getHeadCircumferenceCm() {
        return headCircumferenceCm;
    }

    public void setHeadCircumferenceCm(BigDecimal headCircumferenceCm) {
        this.headCircumferenceCm = headCircumferenceCm;
    }

    public Integer getBirthOrder() {
        return birthOrder;
    }

    public void setBirthOrder(Integer birthOrder) {
        this.birthOrder = birthOrder;
    }

    public boolean isMultipleBirth() {
        return multipleBirth;
    }

    public void setMultipleBirth(boolean multipleBirth) {
        this.multipleBirth = multipleBirth;
    }

    public String getDeliveryMode() {
        return deliveryMode;
    }

    public void setDeliveryMode(String deliveryMode) {
        this.deliveryMode = deliveryMode;
    }

    public String getPlaceOfBirth() {
        return placeOfBirth;
    }

    public void setPlaceOfBirth(String placeOfBirth) {
        this.placeOfBirth = placeOfBirth;
    }

    public String getBirthAttendant() {
        return birthAttendant;
    }

    public void setBirthAttendant(String birthAttendant) {
        this.birthAttendant = birthAttendant;
    }

    public String getBirthOutcome() {
        return birthOutcome;
    }

    public void setBirthOutcome(String birthOutcome) {
        this.birthOutcome = birthOutcome;
    }

    public Integer getApgarOneMinute() {
        return apgarOneMinute;
    }

    public void setApgarOneMinute(Integer apgarOneMinute) {
        this.apgarOneMinute = apgarOneMinute;
    }

    public Integer getApgarFiveMinute() {
        return apgarFiveMinute;
    }

    public void setApgarFiveMinute(Integer apgarFiveMinute) {
        this.apgarFiveMinute = apgarFiveMinute;
    }

    public Integer getApgarTenMinute() {
        return apgarTenMinute;
    }

    public void setApgarTenMinute(Integer apgarTenMinute) {
        this.apgarTenMinute = apgarTenMinute;
    }

    public String getResuscitation() {
        return resuscitation;
    }

    public void setResuscitation(String resuscitation) {
        this.resuscitation = resuscitation;
    }

    public String getResuscitationResponse() {
        return resuscitationResponse;
    }

    public void setResuscitationResponse(String resuscitationResponse) {
        this.resuscitationResponse = resuscitationResponse;
    }

    public String getSkinToSkin() {
        return skinToSkin;
    }

    public void setSkinToSkin(String skinToSkin) {
        this.skinToSkin = skinToSkin;
    }

    public Integer getMinutesToFirstBreastfeed() {
        return minutesToFirstBreastfeed;
    }

    public void setMinutesToFirstBreastfeed(Integer minutesToFirstBreastfeed) {
        this.minutesToFirstBreastfeed = minutesToFirstBreastfeed;
    }

    public String getVitaminKGiven() {
        return vitaminKGiven;
    }

    public void setVitaminKGiven(String vitaminKGiven) {
        this.vitaminKGiven = vitaminKGiven;
    }

    public String getEyeProphylaxisGiven() {
        return eyeProphylaxisGiven;
    }

    public void setEyeProphylaxisGiven(String eyeProphylaxisGiven) {
        this.eyeProphylaxisGiven = eyeProphylaxisGiven;
    }

    public String getCordCare() {
        return cordCare;
    }

    public void setCordCare(String cordCare) {
        this.cordCare = cordCare;
    }

    public BigDecimal getTemperatureCelsius() {
        return temperatureCelsius;
    }

    public void setTemperatureCelsius(BigDecimal temperatureCelsius) {
        this.temperatureCelsius = temperatureCelsius;
    }

    public String getHivExposure() {
        return hivExposure;
    }

    public void setHivExposure(String hivExposure) {
        this.hivExposure = hivExposure;
    }

    public String getMaternalRiskFactors() {
        return maternalRiskFactors;
    }

    public void setMaternalRiskFactors(String maternalRiskFactors) {
        this.maternalRiskFactors = maternalRiskFactors;
    }

    public String getNewbornExamination() {
        return newbornExamination;
    }

    public void setNewbornExamination(String newbornExamination) {
        this.newbornExamination = newbornExamination;
    }

    public String getCongenitalAnomalies() {
        return congenitalAnomalies;
    }

    public void setCongenitalAnomalies(String congenitalAnomalies) {
        this.congenitalAnomalies = congenitalAnomalies;
    }

    public String getBirthTrauma() {
        return birthTrauma;
    }

    public void setBirthTrauma(String birthTrauma) {
        this.birthTrauma = birthTrauma;
    }

    public String getDangerSigns() {
        return dangerSigns;
    }

    public void setDangerSigns(String dangerSigns) {
        this.dangerSigns = dangerSigns;
    }

    public String getAdmissionDestination() {
        return admissionDestination;
    }

    public void setAdmissionDestination(String admissionDestination) {
        this.admissionDestination = admissionDestination;
    }

    public String getDeliverySource() {
        return deliverySource;
    }

    public void setDeliverySource(String deliverySource) {
        this.deliverySource = deliverySource;
    }

    public String getDeliverySourceRef() {
        return deliverySourceRef;
    }

    public void setDeliverySourceRef(String deliverySourceRef) {
        this.deliverySourceRef = deliverySourceRef;
    }

    public String getUbomiNotificationRef() {
        return ubomiNotificationRef;
    }

    public void setUbomiNotificationRef(String ubomiNotificationRef) {
        this.ubomiNotificationRef = ubomiNotificationRef;
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

    public String getRecordedBy() {
        return recordedBy;
    }

    public void setRecordedBy(String recordedBy) {
        this.recordedBy = recordedBy;
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
