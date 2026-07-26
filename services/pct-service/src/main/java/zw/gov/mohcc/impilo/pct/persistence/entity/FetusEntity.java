package zw.gov.mohcc.impilo.pct.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * A fetus within a pregnancy.
 *
 * <p><b>This class deliberately has no identity field for the fetus.</b> No CPID, no provisional
 * identifier, not even a nullable one — a field capable of holding an identity is eventually filled
 * by something, and a fetus is not a registered person. The only person this row is about is the
 * mother, and the care-relationship guard is called with {@code motherCpid} for every read and
 * write. Identity begins at {@link NewbornBirthRecordEntity}, keyed on a real CPID minted after
 * birth.</p>
 *
 * <p>Outcome is per-fetus so that a twin pregnancy with one live birth and one stillbirth is
 * recordable. A pregnancy-level outcome column cannot express that, and a system that cannot
 * express it records whichever outcome was entered last — which is how a stillborn twin disappears
 * from a register.</p>
 */
@Entity
@Table(name = "pct_fetuses")
public class FetusEntity {

    public static final String OUTCOME_ONGOING = "ONGOING";
    public static final String OUTCOME_LIVE_BIRTH = "LIVE_BIRTH";
    public static final String OUTCOME_STILLBIRTH_ANTEPARTUM = "STILLBIRTH_ANTEPARTUM";
    public static final String OUTCOME_STILLBIRTH_INTRAPARTUM = "STILLBIRTH_INTRAPARTUM";
    public static final String OUTCOME_VANISHED = "VANISHED";

    public static final String MATCH_SINGLETON_UNAMBIGUOUS = "SINGLETON_UNAMBIGUOUS";
    public static final String MATCH_DELIVERY_ORDER = "DELIVERY_ORDER";
    public static final String MATCH_ATTENDANT_ASSERTION = "ATTENDANT_ASSERTION";

    @Id
    @Column(name = "fetus_id")
    private UUID fetusId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "pregnancy_episode_id", nullable = false)
    private UUID pregnancyEpisodeId;

    @Column(name = "mother_cpid", nullable = false)
    private String motherCpid;

    @Column(name = "fetus_label", nullable = false)
    private String fetusLabel;

    @Column(name = "presentation")
    private String presentation;

    @Column(name = "presentation_assessed_on")
    private LocalDate presentationAssessedOn;

    @Column(name = "lie")
    private String lie;

    @Column(name = "placental_site")
    private String placentalSite;

    @Column(name = "amniotic_sac_ref")
    private String amnioticSacRef;

    @Column(name = "sex_on_ultrasound")
    private String sexOnUltrasound;

    @Column(name = "anomaly_screening_status")
    private String anomalyScreeningStatus;

    @Column(name = "estimated_fetal_weight_grams")
    private Integer estimatedFetalWeightGrams;

    @Column(name = "efw_assessed_on")
    private LocalDate efwAssessedOn;

    @Column(name = "viability_status")
    private String viabilityStatus;

    @Column(name = "demise_confirmed_on")
    private LocalDate demiseConfirmedOn;

    @Column(name = "demise_gestational_age_days")
    private Integer demiseGestationalAgeDays;

    @Column(name = "outcome")
    private String outcome;

    @Column(name = "outcome_recorded_at")
    private OffsetDateTime outcomeRecordedAt;

    @Column(name = "delivered_at")
    private OffsetDateTime deliveredAt;

    @Column(name = "birth_order")
    private Integer birthOrder;

    @Column(name = "gestational_age_days_at_outcome")
    private Integer gestationalAgeDaysAtOutcome;

    @Column(name = "birth_weight_grams")
    private Integer birthWeightGrams;

    @Column(name = "newborn_birth_record_id")
    private UUID newbornBirthRecordId;

    @Column(name = "match_basis")
    private String matchBasis;

    @Column(name = "match_confidence")
    private String matchConfidence;

    @Column(name = "matched_at")
    private OffsetDateTime matchedAt;

    @Column(name = "matched_by")
    private String matchedBy;

    @Column(name = "match_notes")
    private String matchNotes;

    @Column(name = "client_offline_id")
    private String clientOfflineId;

    @Column(name = "recorded_by", nullable = false)
    private String recordedBy;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    /** True when this fetus has been matched to the baby it became. */
    public boolean matched() {
        return newbornBirthRecordId != null;
    }

    public UUID getFetusId() { return fetusId; }
    public void setFetusId(UUID v) { this.fetusId = v; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID v) { this.tenantId = v; }
    public UUID getPregnancyEpisodeId() { return pregnancyEpisodeId; }
    public void setPregnancyEpisodeId(UUID v) { this.pregnancyEpisodeId = v; }
    public String getMotherCpid() { return motherCpid; }
    public void setMotherCpid(String v) { this.motherCpid = v; }
    public String getFetusLabel() { return fetusLabel; }
    public void setFetusLabel(String v) { this.fetusLabel = v; }
    public String getPresentation() { return presentation; }
    public void setPresentation(String v) { this.presentation = v; }
    public LocalDate getPresentationAssessedOn() { return presentationAssessedOn; }
    public void setPresentationAssessedOn(LocalDate v) { this.presentationAssessedOn = v; }
    public String getLie() { return lie; }
    public void setLie(String v) { this.lie = v; }
    public String getPlacentalSite() { return placentalSite; }
    public void setPlacentalSite(String v) { this.placentalSite = v; }
    public String getAmnioticSacRef() { return amnioticSacRef; }
    public void setAmnioticSacRef(String v) { this.amnioticSacRef = v; }
    public String getSexOnUltrasound() { return sexOnUltrasound; }
    public void setSexOnUltrasound(String v) { this.sexOnUltrasound = v; }
    public String getAnomalyScreeningStatus() { return anomalyScreeningStatus; }
    public void setAnomalyScreeningStatus(String v) { this.anomalyScreeningStatus = v; }
    public Integer getEstimatedFetalWeightGrams() { return estimatedFetalWeightGrams; }
    public void setEstimatedFetalWeightGrams(Integer v) { this.estimatedFetalWeightGrams = v; }
    public LocalDate getEfwAssessedOn() { return efwAssessedOn; }
    public void setEfwAssessedOn(LocalDate v) { this.efwAssessedOn = v; }
    public String getViabilityStatus() { return viabilityStatus; }
    public void setViabilityStatus(String v) { this.viabilityStatus = v; }
    public LocalDate getDemiseConfirmedOn() { return demiseConfirmedOn; }
    public void setDemiseConfirmedOn(LocalDate v) { this.demiseConfirmedOn = v; }
    public Integer getDemiseGestationalAgeDays() { return demiseGestationalAgeDays; }
    public void setDemiseGestationalAgeDays(Integer v) { this.demiseGestationalAgeDays = v; }
    public String getOutcome() { return outcome; }
    public void setOutcome(String v) { this.outcome = v; }
    public OffsetDateTime getOutcomeRecordedAt() { return outcomeRecordedAt; }
    public void setOutcomeRecordedAt(OffsetDateTime v) { this.outcomeRecordedAt = v; }
    public OffsetDateTime getDeliveredAt() { return deliveredAt; }
    public void setDeliveredAt(OffsetDateTime v) { this.deliveredAt = v; }
    public Integer getBirthOrder() { return birthOrder; }
    public void setBirthOrder(Integer v) { this.birthOrder = v; }
    public Integer getGestationalAgeDaysAtOutcome() { return gestationalAgeDaysAtOutcome; }
    public void setGestationalAgeDaysAtOutcome(Integer v) { this.gestationalAgeDaysAtOutcome = v; }
    public Integer getBirthWeightGrams() { return birthWeightGrams; }
    public void setBirthWeightGrams(Integer v) { this.birthWeightGrams = v; }
    public UUID getNewbornBirthRecordId() { return newbornBirthRecordId; }
    public void setNewbornBirthRecordId(UUID v) { this.newbornBirthRecordId = v; }
    public String getMatchBasis() { return matchBasis; }
    public void setMatchBasis(String v) { this.matchBasis = v; }
    public String getMatchConfidence() { return matchConfidence; }
    public void setMatchConfidence(String v) { this.matchConfidence = v; }
    public OffsetDateTime getMatchedAt() { return matchedAt; }
    public void setMatchedAt(OffsetDateTime v) { this.matchedAt = v; }
    public String getMatchedBy() { return matchedBy; }
    public void setMatchedBy(String v) { this.matchedBy = v; }
    public String getMatchNotes() { return matchNotes; }
    public void setMatchNotes(String v) { this.matchNotes = v; }
    public String getClientOfflineId() { return clientOfflineId; }
    public void setClientOfflineId(String v) { this.clientOfflineId = v; }
    public String getRecordedBy() { return recordedBy; }
    public void setRecordedBy(String v) { this.recordedBy = v; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime v) { this.createdAt = v; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime v) { this.updatedAt = v; }
}
