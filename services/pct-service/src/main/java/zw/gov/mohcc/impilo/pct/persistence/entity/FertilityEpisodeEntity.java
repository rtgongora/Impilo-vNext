package zw.gov.mohcc.impilo.pct.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Fertility care for whoever presented, of either sex.
 *
 * <p><b>Infertility is a couple's diagnosis, not a woman's.</b> Roughly half of infertility involves
 * a male factor, and the investigation still routinely begins and ends with the woman. Three things
 * here push against that: the episode is anchored on {@code subjectCpid} rather than a maternal
 * column, {@code maleFactorAssessed} is a first-class field so "was he investigated at all" is a
 * question the data can answer, and a partner's record is reachable only with the partner's recorded
 * consent — a couple attending together is not consent, and a partner is a person with their own say.
 */
@Entity
@Table(name = "pct_fertility_episodes")
public class FertilityEpisodeEntity {

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_REFERRED = "REFERRED";
    public static final String STATUS_CLOSED = "CLOSED";
    public static final String STATUS_ENTERED_IN_ERROR = "ENTERED_IN_ERROR";

    public static final String ASSESSED = "ASSESSED";
    public static final String NOT_ASSESSED = "NOT_ASSESSED";
    public static final String DECLINED = "DECLINED";

    public static final String CONSENT_GIVEN = "GIVEN";
    public static final String CONSENT_DECLINED = "DECLINED";
    public static final String CONSENT_NOT_ASKED = "NOT_ASKED";

    @Id
    @Column(name = "fertility_episode_id")
    private UUID fertilityEpisodeId;

    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(name = "subject_cpid")
    private String subjectCpid;

    @Column(name = "partner_cpid")
    private String partnerCpid;

    @Column(name = "partner_linkage_consent")
    private String partnerLinkageConsent;

    @Column(name = "status")
    private String status;

    @Column(name = "opened_on")
    private LocalDate openedOn;

    @Column(name = "closed_on")
    private LocalDate closedOn;

    @Column(name = "outcome")
    private String outcome;

    @Column(name = "months_trying")
    private Short monthsTrying;

    @Column(name = "investigation_threshold_met")
    private String investigationThresholdMet;

    @Column(name = "primary_or_secondary")
    private String primaryOrSecondary;

    @Column(name = "female_factor_assessed")
    private String femaleFactorAssessed;

    @Column(name = "female_factor_findings")
    private String femaleFactorFindings;

    @Column(name = "male_factor_assessed")
    private String maleFactorAssessed;

    @Column(name = "male_factor_findings")
    private String maleFactorFindings;

    @Column(name = "semen_analysis_done_on")
    private LocalDate semenAnalysisDoneOn;

    @Column(name = "tubal_assessment_done_on")
    private LocalDate tubalAssessmentDoneOn;

    @Column(name = "ovulation_assessment_done_on")
    private LocalDate ovulationAssessmentDoneOn;

    @Column(name = "referred_on")
    private LocalDate referredOn;

    @Column(name = "referred_to_facility_id")
    private UUID referredToFacilityId;

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

    public UUID getFertilityEpisodeId() {
        return fertilityEpisodeId;
    }

    public void setFertilityEpisodeId(UUID fertilityEpisodeId) {
        this.fertilityEpisodeId = fertilityEpisodeId;
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

    public String getPartnerCpid() {
        return partnerCpid;
    }

    public void setPartnerCpid(String partnerCpid) {
        this.partnerCpid = partnerCpid;
    }

    public String getPartnerLinkageConsent() {
        return partnerLinkageConsent;
    }

    public void setPartnerLinkageConsent(String partnerLinkageConsent) {
        this.partnerLinkageConsent = partnerLinkageConsent;
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

    public String getOutcome() {
        return outcome;
    }

    public void setOutcome(String outcome) {
        this.outcome = outcome;
    }

    public Short getMonthsTrying() {
        return monthsTrying;
    }

    public void setMonthsTrying(Short monthsTrying) {
        this.monthsTrying = monthsTrying;
    }

    public String getInvestigationThresholdMet() {
        return investigationThresholdMet;
    }

    public void setInvestigationThresholdMet(String investigationThresholdMet) {
        this.investigationThresholdMet = investigationThresholdMet;
    }

    public String getPrimaryOrSecondary() {
        return primaryOrSecondary;
    }

    public void setPrimaryOrSecondary(String primaryOrSecondary) {
        this.primaryOrSecondary = primaryOrSecondary;
    }

    public String getFemaleFactorAssessed() {
        return femaleFactorAssessed;
    }

    public void setFemaleFactorAssessed(String femaleFactorAssessed) {
        this.femaleFactorAssessed = femaleFactorAssessed;
    }

    public String getFemaleFactorFindings() {
        return femaleFactorFindings;
    }

    public void setFemaleFactorFindings(String femaleFactorFindings) {
        this.femaleFactorFindings = femaleFactorFindings;
    }

    public String getMaleFactorAssessed() {
        return maleFactorAssessed;
    }

    public void setMaleFactorAssessed(String maleFactorAssessed) {
        this.maleFactorAssessed = maleFactorAssessed;
    }

    public String getMaleFactorFindings() {
        return maleFactorFindings;
    }

    public void setMaleFactorFindings(String maleFactorFindings) {
        this.maleFactorFindings = maleFactorFindings;
    }

    public LocalDate getSemenAnalysisDoneOn() {
        return semenAnalysisDoneOn;
    }

    public void setSemenAnalysisDoneOn(LocalDate semenAnalysisDoneOn) {
        this.semenAnalysisDoneOn = semenAnalysisDoneOn;
    }

    public LocalDate getTubalAssessmentDoneOn() {
        return tubalAssessmentDoneOn;
    }

    public void setTubalAssessmentDoneOn(LocalDate tubalAssessmentDoneOn) {
        this.tubalAssessmentDoneOn = tubalAssessmentDoneOn;
    }

    public LocalDate getOvulationAssessmentDoneOn() {
        return ovulationAssessmentDoneOn;
    }

    public void setOvulationAssessmentDoneOn(LocalDate ovulationAssessmentDoneOn) {
        this.ovulationAssessmentDoneOn = ovulationAssessmentDoneOn;
    }

    public LocalDate getReferredOn() {
        return referredOn;
    }

    public void setReferredOn(LocalDate referredOn) {
        this.referredOn = referredOn;
    }

    public UUID getReferredToFacilityId() {
        return referredToFacilityId;
    }

    public void setReferredToFacilityId(UUID referredToFacilityId) {
        this.referredToFacilityId = referredToFacilityId;
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
