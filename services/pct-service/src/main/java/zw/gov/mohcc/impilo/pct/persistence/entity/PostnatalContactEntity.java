package zw.gov.mohcc.impilo.pct.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * A postnatal maternal contact — the mother's care after the birth (WHO PNC contacts, PNC.S.01).
 *
 * <p>Mother-anchored: the subject is {@code motherCpid}. The newborn's postnatal care belongs to the
 * paediatric pack and is reached through the shared pregnancy episode, never duplicated here.
 *
 * <p>Two safety invariants live in the schema (V436), not merely in the service:
 * {@code dangerSignsPresent} may be non-null only when {@code screeningComplete} is true, so an
 * unscreened contact can never render as a reassuring "no danger signs"; and a referral carries its
 * reason. This entity mirrors those, but the database is the enforcer.
 */
@Entity
@Table(name = "pct_postnatal_contacts")
public class PostnatalContactEntity {

    public static final String TIMING_WITHIN_24H = "WITHIN_24H";
    public static final String TIMING_DAY_3 = "DAY_3";
    public static final String TIMING_DAY_7_14 = "DAY_7_14";
    public static final String TIMING_WEEK_6 = "WEEK_6";
    public static final String TIMING_ADDITIONAL = "ADDITIONAL";

    public static final String SETTING_FACILITY = "FACILITY";
    public static final String SETTING_HOME = "HOME";
    public static final String SETTING_COMMUNITY = "COMMUNITY";
    public static final String SETTING_VIRTUAL = "VIRTUAL";

    @Id @Column(name = "postnatal_contact_id") private UUID postnatalContactId;
    @Column(name = "tenant_id") private UUID tenantId;
    @Column(name = "mother_cpid") private String motherCpid;
    @Column(name = "pregnancy_episode_id") private UUID pregnancyEpisodeId;

    @Column(name = "status") private String status;
    @Column(name = "contact_timing") private String contactTiming;
    @Column(name = "contact_number") private Short contactNumber;
    @Column(name = "contacted_at") private OffsetDateTime contactedAt;
    @Column(name = "contact_setting") private String contactSetting;
    @Column(name = "facility_id") private UUID facilityId;
    @Column(name = "attended_by") private String attendedBy;
    @Column(name = "attendant_cadre") private String attendantCadre;

    @Column(name = "days_since_birth") private Short daysSinceBirth;
    @Column(name = "maternal_condition") private String maternalCondition;
    @Column(name = "bleeding_assessment") private String bleedingAssessment;
    @Column(name = "mood_screen") private String moodScreen;

    @Column(name = "screening_complete") private Boolean screeningComplete;
    @Column(name = "danger_signs_present") private Boolean dangerSignsPresent;

    @Column(name = "breastfeeding_status") private String breastfeedingStatus;
    @Column(name = "breastfeeding_difficulty") private String breastfeedingDifficulty;
    @Column(name = "lactation_support_given") private Boolean lactationSupportGiven;

    @Column(name = "postpartum_contraception_episode_id") private UUID postpartumContraceptionEpisodeId;
    @Column(name = "contraception_discussed") private Boolean contraceptionDiscussed;

    @Column(name = "referral_made") private Boolean referralMade;
    @Column(name = "referral_reason") private String referralReason;
    @Column(name = "referral_facility_id") private UUID referralFacilityId;

    @Column(name = "sensitivity_class") private String sensitivityClass;

    @Column(name = "client_offline_id") private String clientOfflineId;
    @Column(name = "recorded_by") private String recordedBy;
    @Column(name = "recorded_at") private OffsetDateTime recordedAt;

    public UUID getPostnatalContactId() { return postnatalContactId; }
    public void setPostnatalContactId(UUID v) { this.postnatalContactId = v; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID v) { this.tenantId = v; }
    public String getMotherCpid() { return motherCpid; }
    public void setMotherCpid(String v) { this.motherCpid = v; }
    public UUID getPregnancyEpisodeId() { return pregnancyEpisodeId; }
    public void setPregnancyEpisodeId(UUID v) { this.pregnancyEpisodeId = v; }
    public String getStatus() { return status; }
    public void setStatus(String v) { this.status = v; }
    public String getContactTiming() { return contactTiming; }
    public void setContactTiming(String v) { this.contactTiming = v; }
    public Short getContactNumber() { return contactNumber; }
    public void setContactNumber(Short v) { this.contactNumber = v; }
    public OffsetDateTime getContactedAt() { return contactedAt; }
    public void setContactedAt(OffsetDateTime v) { this.contactedAt = v; }
    public String getContactSetting() { return contactSetting; }
    public void setContactSetting(String v) { this.contactSetting = v; }
    public UUID getFacilityId() { return facilityId; }
    public void setFacilityId(UUID v) { this.facilityId = v; }
    public String getAttendedBy() { return attendedBy; }
    public void setAttendedBy(String v) { this.attendedBy = v; }
    public String getAttendantCadre() { return attendantCadre; }
    public void setAttendantCadre(String v) { this.attendantCadre = v; }
    public Short getDaysSinceBirth() { return daysSinceBirth; }
    public void setDaysSinceBirth(Short v) { this.daysSinceBirth = v; }
    public String getMaternalCondition() { return maternalCondition; }
    public void setMaternalCondition(String v) { this.maternalCondition = v; }
    public String getBleedingAssessment() { return bleedingAssessment; }
    public void setBleedingAssessment(String v) { this.bleedingAssessment = v; }
    public String getMoodScreen() { return moodScreen; }
    public void setMoodScreen(String v) { this.moodScreen = v; }
    public Boolean getScreeningComplete() { return screeningComplete; }
    public void setScreeningComplete(Boolean v) { this.screeningComplete = v; }
    public Boolean getDangerSignsPresent() { return dangerSignsPresent; }
    public void setDangerSignsPresent(Boolean v) { this.dangerSignsPresent = v; }
    public String getBreastfeedingStatus() { return breastfeedingStatus; }
    public void setBreastfeedingStatus(String v) { this.breastfeedingStatus = v; }
    public String getBreastfeedingDifficulty() { return breastfeedingDifficulty; }
    public void setBreastfeedingDifficulty(String v) { this.breastfeedingDifficulty = v; }
    public Boolean getLactationSupportGiven() { return lactationSupportGiven; }
    public void setLactationSupportGiven(Boolean v) { this.lactationSupportGiven = v; }
    public UUID getPostpartumContraceptionEpisodeId() { return postpartumContraceptionEpisodeId; }
    public void setPostpartumContraceptionEpisodeId(UUID v) { this.postpartumContraceptionEpisodeId = v; }
    public Boolean getContraceptionDiscussed() { return contraceptionDiscussed; }
    public void setContraceptionDiscussed(Boolean v) { this.contraceptionDiscussed = v; }
    public Boolean getReferralMade() { return referralMade; }
    public void setReferralMade(Boolean v) { this.referralMade = v; }
    public String getReferralReason() { return referralReason; }
    public void setReferralReason(String v) { this.referralReason = v; }
    public UUID getReferralFacilityId() { return referralFacilityId; }
    public void setReferralFacilityId(UUID v) { this.referralFacilityId = v; }
    public String getSensitivityClass() { return sensitivityClass; }
    public void setSensitivityClass(String v) { this.sensitivityClass = v; }
    public String getClientOfflineId() { return clientOfflineId; }
    public void setClientOfflineId(String v) { this.clientOfflineId = v; }
    public String getRecordedBy() { return recordedBy; }
    public void setRecordedBy(String v) { this.recordedBy = v; }
    public OffsetDateTime getRecordedAt() { return recordedAt; }
    public void setRecordedAt(OffsetDateTime v) { this.recordedAt = v; }

    // Confidentiality stamp (V437). Three values travel with the class above: the governed CATEGORY a
    // grant is matched against, WHY the stamp was applied, and WHICH policy version decided it.
    @Column(name = "confidentiality_category") private String confidentialityCategory;
    @Column(name = "confidentiality_basis") private String confidentialityBasis;
    @Column(name = "confidentiality_policy_version") private String confidentialityPolicyVersion;

    public String getConfidentialityCategory() { return confidentialityCategory; }
    public void setConfidentialityCategory(String v) { this.confidentialityCategory = v; }
    public String getConfidentialityBasis() { return confidentialityBasis; }
    public void setConfidentialityBasis(String v) { this.confidentialityBasis = v; }
    public String getConfidentialityPolicyVersion() { return confidentialityPolicyVersion; }
    public void setConfidentialityPolicyVersion(String v) { this.confidentialityPolicyVersion = v; }

}
