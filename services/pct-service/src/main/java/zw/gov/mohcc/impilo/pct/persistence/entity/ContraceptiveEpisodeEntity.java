package zw.gov.mohcc.impilo.pct.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * A woman's use of one contraceptive method over time.
 *
 * <p>One episode per method, not per woman: dual method use is normal and often the correct answer,
 * and a model that allowed only one active method would force the second to go unrecorded.
 *
 * <p><b>The eligibility verdict is stamped, not recomputed.</b> The category, the recommendation and
 * the content version that produced them are stored as they stood when she started. The criteria will
 * change; the question a reviewer asks afterwards is what was known at the time, not what today's
 * matrix would say. A record able to re-derive its own category will eventually disagree with the
 * counselling that actually happened.
 */
@Entity
@Table(name = "pct_contraceptive_episodes")
public class ContraceptiveEpisodeEntity {

    public static final String STATUS_PLANNED = "PLANNED";
    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_DISCONTINUED = "DISCONTINUED";
    public static final String STATUS_COMPLETED = "COMPLETED";
    public static final String STATUS_ENTERED_IN_ERROR = "ENTERED_IN_ERROR";

    @Id
    @Column(name = "contraceptive_episode_id")
    private UUID contraceptiveEpisodeId;

    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(name = "subject_cpid")
    private String subjectCpid;

    @Column(name = "method_code")
    private String methodCode;

    @Column(name = "method_class")
    private String methodClass;

    @Column(name = "status")
    private String status;

    @Column(name = "started_on")
    private LocalDate startedOn;

    @Column(name = "expected_end_on")
    private LocalDate expectedEndOn;

    @Column(name = "ended_on")
    private LocalDate endedOn;

    @Column(name = "discontinuation_reason")
    private String discontinuationReason;

    @Column(name = "discontinuation_detail")
    private String discontinuationDetail;

    @Column(name = "eligibility_category")
    private Short eligibilityCategory;

    @Column(name = "eligibility_recommendation")
    private String eligibilityRecommendation;

    @Column(name = "eligibility_content_version")
    private String eligibilityContentVersion;

    @Column(name = "eligibility_assessed_at")
    private OffsetDateTime eligibilityAssessedAt;

    @Column(name = "eligibility_overridden")
    private Boolean eligibilityOverridden;

    @Column(name = "eligibility_override_reason")
    private String eligibilityOverrideReason;

    @Column(name = "pregnancy_certainty")
    private String pregnancyCertainty;

    @Column(name = "backup_advice_code")
    private String backupAdviceCode;

    @Column(name = "backup_days")
    private Short backupDays;

    @Column(name = "backup_supplied")
    private String backupSupplied;

    @Column(name = "deferred_insertion_owed")
    private Boolean deferredInsertionOwed;

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

    /** True while this method is or will be protecting her. */
    public boolean current() {
        return STATUS_ACTIVE.equals(status) || STATUS_PLANNED.equals(status);
    }

    public UUID getContraceptiveEpisodeId() {
        return contraceptiveEpisodeId;
    }

    public void setContraceptiveEpisodeId(UUID contraceptiveEpisodeId) {
        this.contraceptiveEpisodeId = contraceptiveEpisodeId;
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

    public String getMethodCode() {
        return methodCode;
    }

    public void setMethodCode(String methodCode) {
        this.methodCode = methodCode;
    }

    public String getMethodClass() {
        return methodClass;
    }

    public void setMethodClass(String methodClass) {
        this.methodClass = methodClass;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDate getStartedOn() {
        return startedOn;
    }

    public void setStartedOn(LocalDate startedOn) {
        this.startedOn = startedOn;
    }

    public LocalDate getExpectedEndOn() {
        return expectedEndOn;
    }

    public void setExpectedEndOn(LocalDate expectedEndOn) {
        this.expectedEndOn = expectedEndOn;
    }

    public LocalDate getEndedOn() {
        return endedOn;
    }

    public void setEndedOn(LocalDate endedOn) {
        this.endedOn = endedOn;
    }

    public String getDiscontinuationReason() {
        return discontinuationReason;
    }

    public void setDiscontinuationReason(String discontinuationReason) {
        this.discontinuationReason = discontinuationReason;
    }

    public String getDiscontinuationDetail() {
        return discontinuationDetail;
    }

    public void setDiscontinuationDetail(String discontinuationDetail) {
        this.discontinuationDetail = discontinuationDetail;
    }

    public Short getEligibilityCategory() {
        return eligibilityCategory;
    }

    public void setEligibilityCategory(Short eligibilityCategory) {
        this.eligibilityCategory = eligibilityCategory;
    }

    public String getEligibilityRecommendation() {
        return eligibilityRecommendation;
    }

    public void setEligibilityRecommendation(String eligibilityRecommendation) {
        this.eligibilityRecommendation = eligibilityRecommendation;
    }

    public String getEligibilityContentVersion() {
        return eligibilityContentVersion;
    }

    public void setEligibilityContentVersion(String eligibilityContentVersion) {
        this.eligibilityContentVersion = eligibilityContentVersion;
    }

    public OffsetDateTime getEligibilityAssessedAt() {
        return eligibilityAssessedAt;
    }

    public void setEligibilityAssessedAt(OffsetDateTime eligibilityAssessedAt) {
        this.eligibilityAssessedAt = eligibilityAssessedAt;
    }

    public Boolean getEligibilityOverridden() {
        return eligibilityOverridden;
    }

    public void setEligibilityOverridden(Boolean eligibilityOverridden) {
        this.eligibilityOverridden = eligibilityOverridden;
    }

    public String getEligibilityOverrideReason() {
        return eligibilityOverrideReason;
    }

    public void setEligibilityOverrideReason(String eligibilityOverrideReason) {
        this.eligibilityOverrideReason = eligibilityOverrideReason;
    }

    public String getPregnancyCertainty() {
        return pregnancyCertainty;
    }

    public void setPregnancyCertainty(String pregnancyCertainty) {
        this.pregnancyCertainty = pregnancyCertainty;
    }

    public String getBackupAdviceCode() {
        return backupAdviceCode;
    }

    public void setBackupAdviceCode(String backupAdviceCode) {
        this.backupAdviceCode = backupAdviceCode;
    }

    public Short getBackupDays() {
        return backupDays;
    }

    public void setBackupDays(Short backupDays) {
        this.backupDays = backupDays;
    }

    public String getBackupSupplied() {
        return backupSupplied;
    }

    public void setBackupSupplied(String backupSupplied) {
        this.backupSupplied = backupSupplied;
    }

    public Boolean getDeferredInsertionOwed() {
        return deferredInsertionOwed;
    }

    public void setDeferredInsertionOwed(Boolean deferredInsertionOwed) {
        this.deferredInsertionOwed = deferredInsertionOwed;
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
