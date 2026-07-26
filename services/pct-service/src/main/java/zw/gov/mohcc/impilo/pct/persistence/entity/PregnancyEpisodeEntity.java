package zw.gov.mohcc.impilo.pct.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * A pregnancy, as a longitudinal episode.
 *
 * <p>The spine every maternal record hangs from: the antenatal contacts, the labour, the delivery
 * and the postnatal period all attach here. Deliberately an <em>episode</em> and never a journey —
 * care-continuum-doctrine CC-0 reserves "journey" for one facility visit, and a pregnancy spans
 * many — while still carrying the opening visit so it satisfies the CC-5 anchoring rule.</p>
 *
 * <p><b>Dating is stamped here and consumed elsewhere.</b> This service writes the estimated
 * delivery date and its basis; the rules engine reads the stored gestational age and cannot
 * recompute it. An engine able to re-derive an EDD will eventually disagree with the chart about
 * how pregnant a woman is.</p>
 *
 * <p>{@code bookingWeek} is a generated column and is read-only here: it is computed from the
 * FIRST dating so that a later redating cannot move a pregnancy out from under its own duplicate
 * check.</p>
 */
@Entity
@Table(name = "pct_pregnancy_episodes")
public class PregnancyEpisodeEntity {

    public static final String STATUS_ONGOING = "ONGOING";
    public static final String STATUS_COMPLETED = "COMPLETED";
    public static final String STATUS_LOST = "LOST";
    public static final String STATUS_TERMINATED = "TERMINATED";
    public static final String STATUS_TRANSFERRED_OUT = "TRANSFERRED_OUT";
    public static final String STATUS_RESOLVED_NOT_PREGNANT = "RESOLVED_NOT_PREGNANT";
    public static final String STATUS_ENTERED_IN_ERROR = "ENTERED_IN_ERROR";

    @Id
    @Column(name = "pregnancy_episode_id")
    private UUID pregnancyEpisodeId;

    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(name = "subject_cpid")
    private String subjectCpid;

    @Column(name = "facility_id")
    private UUID facilityId;

    @Column(name = "opening_journey_id")
    private String openingJourneyId;

    @Column(name = "opening_encounter_id")
    private String openingEncounterId;

    @Column(name = "status")
    private String status;

    @Column(name = "confirmed_on")
    private LocalDate confirmedOn;

    @Column(name = "confirmation_basis")
    private String confirmationBasis;

    @Column(name = "pregnancy_start_date")
    private LocalDate pregnancyStartDate;

    @Column(name = "initial_pregnancy_start_date")
    private LocalDate initialPregnancyStartDate;

    @Column(name = "estimated_delivery_date")
    private LocalDate estimatedDeliveryDate;

    @Column(name = "dating_method")
    private String datingMethod;

    @Column(name = "dating_confidence")
    private String datingConfidence;

    @Column(name = "dating_plus_minus_days")
    private Integer datingPlusMinusDays;

    @Column(name = "dated_on")
    private LocalDate datedOn;

    @Column(name = "dating_content_version")
    private String datingContentVersion;

    @Column(name = "dating_revision_count")
    private Integer datingRevisionCount;

    @Column(name = "gravida_derived")
    private Integer gravidaDerived;

    @Column(name = "para_derived")
    private Integer paraDerived;

    @Column(name = "abortus_derived")
    private Integer abortusDerived;

    @Column(name = "term_derived")
    private Integer termDerived;

    @Column(name = "preterm_derived")
    private Integer pretermDerived;

    @Column(name = "living_children_derived")
    private Integer livingChildrenDerived;

    @Column(name = "gp_derivation_complete")
    private Boolean gpDerivationComplete;

    @Column(name = "gp_uncountable_count")
    private Integer gpUncountableCount;

    @Column(name = "gravida_recorded")
    private Integer gravidaRecorded;

    @Column(name = "para_recorded")
    private Integer paraRecorded;

    @Column(name = "gp_discrepancy")
    private Boolean gpDiscrepancy;

    @Column(name = "gp_content_version")
    private String gpContentVersion;

    @Column(name = "plurality")
    private Integer plurality;

    @Column(name = "plurality_basis")
    private String pluralityBasis;

    @Column(name = "chorionicity")
    private String chorionicity;

    @Column(name = "chorionicity_determined_on")
    private LocalDate chorionicityDeterminedOn;

    @Column(name = "previous_pregnancy_episode_id")
    private UUID previousPregnancyEpisodeId;

    @Column(name = "interpregnancy_interval_months")
    private Integer interpregnancyIntervalMonths;

    @Column(name = "interval_adequacy")
    private String intervalAdequacy;

    @Column(name = "intended_pregnancy")
    private String intendedPregnancy;

    @Column(name = "conception_mode")
    private String conceptionMode;

    @Column(name = "risk_status")
    private String riskStatus;

    @Column(name = "risk_content_version")
    private String riskContentVersion;

    @Column(name = "planned_place_of_birth")
    private String plannedPlaceOfBirth;

    @Column(name = "hiv_status_at_booking")
    private String hivStatusAtBooking;

    @Column(name = "on_art")
    private String onArt;

    @Column(name = "outcome")
    private String outcome;

    @Column(name = "ended_on")
    private LocalDate endedOn;

    @Column(name = "gestational_age_days_at_end")
    private Integer gestationalAgeDaysAtEnd;

    @Column(name = "birth_maturity_band")
    private String birthMaturityBand;

    @Column(name = "outcome_recorded_by")
    private String outcomeRecordedBy;

    @Column(name = "confidentiality")
    private String confidentiality;

    @Column(name = "client_offline_id")
    private String clientOfflineId;

    @Column(name = "package_checksum")
    private String packageChecksum;

    @Column(name = "captured_at")
    private OffsetDateTime capturedAt;

    @Column(name = "recorded_by")
    private String recordedBy;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    /** Generated by the database from the immutable first dating; never written by this entity. */
    @Column(name = "booking_week", insertable = false, updatable = false)
    private Integer bookingWeek;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "risk_factors")
    private String riskFactors;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "birth_plan")
    private String birthPlan;

    public UUID getPregnancyEpisodeId() { return pregnancyEpisodeId; }

    public void setPregnancyEpisodeId(UUID pregnancyEpisodeId) { this.pregnancyEpisodeId = pregnancyEpisodeId; }

    public UUID getTenantId() { return tenantId; }

    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public String getSubjectCpid() { return subjectCpid; }

    public void setSubjectCpid(String subjectCpid) { this.subjectCpid = subjectCpid; }

    public UUID getFacilityId() { return facilityId; }

    public void setFacilityId(UUID facilityId) { this.facilityId = facilityId; }

    public String getOpeningJourneyId() { return openingJourneyId; }

    public void setOpeningJourneyId(String openingJourneyId) { this.openingJourneyId = openingJourneyId; }

    public String getOpeningEncounterId() { return openingEncounterId; }

    public void setOpeningEncounterId(String openingEncounterId) { this.openingEncounterId = openingEncounterId; }

    public String getStatus() { return status; }

    public void setStatus(String status) { this.status = status; }

    public LocalDate getConfirmedOn() { return confirmedOn; }

    public void setConfirmedOn(LocalDate confirmedOn) { this.confirmedOn = confirmedOn; }

    public String getConfirmationBasis() { return confirmationBasis; }

    public void setConfirmationBasis(String confirmationBasis) { this.confirmationBasis = confirmationBasis; }

    public LocalDate getPregnancyStartDate() { return pregnancyStartDate; }

    public void setPregnancyStartDate(LocalDate pregnancyStartDate) { this.pregnancyStartDate = pregnancyStartDate; }

    public LocalDate getInitialPregnancyStartDate() { return initialPregnancyStartDate; }

    public void setInitialPregnancyStartDate(LocalDate initialPregnancyStartDate) { this.initialPregnancyStartDate = initialPregnancyStartDate; }

    public LocalDate getEstimatedDeliveryDate() { return estimatedDeliveryDate; }

    public void setEstimatedDeliveryDate(LocalDate estimatedDeliveryDate) { this.estimatedDeliveryDate = estimatedDeliveryDate; }

    public String getDatingMethod() { return datingMethod; }

    public void setDatingMethod(String datingMethod) { this.datingMethod = datingMethod; }

    public String getDatingConfidence() { return datingConfidence; }

    public void setDatingConfidence(String datingConfidence) { this.datingConfidence = datingConfidence; }

    public Integer getDatingPlusMinusDays() { return datingPlusMinusDays; }

    public void setDatingPlusMinusDays(Integer datingPlusMinusDays) { this.datingPlusMinusDays = datingPlusMinusDays; }

    public LocalDate getDatedOn() { return datedOn; }

    public void setDatedOn(LocalDate datedOn) { this.datedOn = datedOn; }

    public String getDatingContentVersion() { return datingContentVersion; }

    public void setDatingContentVersion(String datingContentVersion) { this.datingContentVersion = datingContentVersion; }

    public Integer getDatingRevisionCount() { return datingRevisionCount; }

    public void setDatingRevisionCount(Integer datingRevisionCount) { this.datingRevisionCount = datingRevisionCount; }

    public Integer getGravidaDerived() { return gravidaDerived; }

    public void setGravidaDerived(Integer gravidaDerived) { this.gravidaDerived = gravidaDerived; }

    public Integer getParaDerived() { return paraDerived; }

    public void setParaDerived(Integer paraDerived) { this.paraDerived = paraDerived; }

    public Integer getAbortusDerived() { return abortusDerived; }

    public void setAbortusDerived(Integer abortusDerived) { this.abortusDerived = abortusDerived; }

    public Integer getTermDerived() { return termDerived; }

    public void setTermDerived(Integer termDerived) { this.termDerived = termDerived; }

    public Integer getPretermDerived() { return pretermDerived; }

    public void setPretermDerived(Integer pretermDerived) { this.pretermDerived = pretermDerived; }

    public Integer getLivingChildrenDerived() { return livingChildrenDerived; }

    public void setLivingChildrenDerived(Integer livingChildrenDerived) { this.livingChildrenDerived = livingChildrenDerived; }

    public Boolean getGpDerivationComplete() { return gpDerivationComplete; }

    public void setGpDerivationComplete(Boolean gpDerivationComplete) { this.gpDerivationComplete = gpDerivationComplete; }

    public Integer getGpUncountableCount() { return gpUncountableCount; }

    public void setGpUncountableCount(Integer gpUncountableCount) { this.gpUncountableCount = gpUncountableCount; }

    public Integer getGravidaRecorded() { return gravidaRecorded; }

    public void setGravidaRecorded(Integer gravidaRecorded) { this.gravidaRecorded = gravidaRecorded; }

    public Integer getParaRecorded() { return paraRecorded; }

    public void setParaRecorded(Integer paraRecorded) { this.paraRecorded = paraRecorded; }

    public Boolean getGpDiscrepancy() { return gpDiscrepancy; }

    public void setGpDiscrepancy(Boolean gpDiscrepancy) { this.gpDiscrepancy = gpDiscrepancy; }

    public String getGpContentVersion() { return gpContentVersion; }

    public void setGpContentVersion(String gpContentVersion) { this.gpContentVersion = gpContentVersion; }

    public Integer getPlurality() { return plurality; }

    public void setPlurality(Integer plurality) { this.plurality = plurality; }

    public String getPluralityBasis() { return pluralityBasis; }

    public void setPluralityBasis(String pluralityBasis) { this.pluralityBasis = pluralityBasis; }

    public String getChorionicity() { return chorionicity; }

    public void setChorionicity(String chorionicity) { this.chorionicity = chorionicity; }

    public LocalDate getChorionicityDeterminedOn() { return chorionicityDeterminedOn; }

    public void setChorionicityDeterminedOn(LocalDate chorionicityDeterminedOn) { this.chorionicityDeterminedOn = chorionicityDeterminedOn; }

    public UUID getPreviousPregnancyEpisodeId() { return previousPregnancyEpisodeId; }

    public void setPreviousPregnancyEpisodeId(UUID previousPregnancyEpisodeId) { this.previousPregnancyEpisodeId = previousPregnancyEpisodeId; }

    public Integer getInterpregnancyIntervalMonths() { return interpregnancyIntervalMonths; }

    public void setInterpregnancyIntervalMonths(Integer interpregnancyIntervalMonths) { this.interpregnancyIntervalMonths = interpregnancyIntervalMonths; }

    public String getIntervalAdequacy() { return intervalAdequacy; }

    public void setIntervalAdequacy(String intervalAdequacy) { this.intervalAdequacy = intervalAdequacy; }

    public String getIntendedPregnancy() { return intendedPregnancy; }

    public void setIntendedPregnancy(String intendedPregnancy) { this.intendedPregnancy = intendedPregnancy; }

    public String getConceptionMode() { return conceptionMode; }

    public void setConceptionMode(String conceptionMode) { this.conceptionMode = conceptionMode; }

    public String getRiskStatus() { return riskStatus; }

    public void setRiskStatus(String riskStatus) { this.riskStatus = riskStatus; }

    public String getRiskContentVersion() { return riskContentVersion; }

    public void setRiskContentVersion(String riskContentVersion) { this.riskContentVersion = riskContentVersion; }

    public String getPlannedPlaceOfBirth() { return plannedPlaceOfBirth; }

    public void setPlannedPlaceOfBirth(String plannedPlaceOfBirth) { this.plannedPlaceOfBirth = plannedPlaceOfBirth; }

    public String getHivStatusAtBooking() { return hivStatusAtBooking; }

    public void setHivStatusAtBooking(String hivStatusAtBooking) { this.hivStatusAtBooking = hivStatusAtBooking; }

    public String getOnArt() { return onArt; }

    public void setOnArt(String onArt) { this.onArt = onArt; }

    public String getOutcome() { return outcome; }

    public void setOutcome(String outcome) { this.outcome = outcome; }

    public LocalDate getEndedOn() { return endedOn; }

    public void setEndedOn(LocalDate endedOn) { this.endedOn = endedOn; }

    public Integer getGestationalAgeDaysAtEnd() { return gestationalAgeDaysAtEnd; }

    public void setGestationalAgeDaysAtEnd(Integer gestationalAgeDaysAtEnd) { this.gestationalAgeDaysAtEnd = gestationalAgeDaysAtEnd; }

    public String getBirthMaturityBand() { return birthMaturityBand; }

    public void setBirthMaturityBand(String birthMaturityBand) { this.birthMaturityBand = birthMaturityBand; }

    public String getOutcomeRecordedBy() { return outcomeRecordedBy; }

    public void setOutcomeRecordedBy(String outcomeRecordedBy) { this.outcomeRecordedBy = outcomeRecordedBy; }

    public String getConfidentiality() { return confidentiality; }

    public void setConfidentiality(String confidentiality) { this.confidentiality = confidentiality; }

    public String getClientOfflineId() { return clientOfflineId; }

    public void setClientOfflineId(String clientOfflineId) { this.clientOfflineId = clientOfflineId; }

    public String getPackageChecksum() { return packageChecksum; }

    public void setPackageChecksum(String packageChecksum) { this.packageChecksum = packageChecksum; }

    public OffsetDateTime getCapturedAt() { return capturedAt; }

    public void setCapturedAt(OffsetDateTime capturedAt) { this.capturedAt = capturedAt; }

    public String getRecordedBy() { return recordedBy; }

    public void setRecordedBy(String recordedBy) { this.recordedBy = recordedBy; }

    public OffsetDateTime getCreatedAt() { return createdAt; }

    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    public OffsetDateTime getUpdatedAt() { return updatedAt; }

    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }

    public Integer getBookingWeek() { return bookingWeek; }


    public String getRiskFactors() { return riskFactors; }

    public void setRiskFactors(String riskFactors) { this.riskFactors = riskFactors; }


    public String getBirthPlan() { return birthPlan; }

    public void setBirthPlan(String birthPlan) { this.birthPlan = birthPlan; }

    /** True while this pregnancy is the woman's current one. */
    public boolean ongoing() {
        return STATUS_ONGOING.equals(status);
    }
}
