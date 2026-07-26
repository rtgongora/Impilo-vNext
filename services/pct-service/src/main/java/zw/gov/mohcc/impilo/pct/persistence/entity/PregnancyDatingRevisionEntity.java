package zw.gov.mohcc.impilo.pct.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * One dating decision on a pregnancy — including the ones that changed nothing.
 *
 * <p>An estimated delivery date decides what counts as preterm, when a woman is post-term, which
 * antenatal contacts are due and whether a growth measurement is reassuring. An EDD that moves
 * without anyone deciding it should is one of the more dangerous silent changes this record can
 * contain, so every decision is written down with both dates and a rationale.</p>
 *
 * <p>{@code redatingApplied == false} is the row that matters most: it records a basis that was
 * <em>considered and rejected</em>. "We saw the 26-week scan and kept the last-menstrual-period
 * dating" is clinical information, and without it the next clinician sees a scan that appears to
 * have been ignored.</p>
 */
@Entity
@Table(name = "pct_pregnancy_dating_revisions")
public class PregnancyDatingRevisionEntity {

    @Id
    @Column(name = "revision_id")
    private UUID revisionId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "pregnancy_episode_id", nullable = false)
    private UUID pregnancyEpisodeId;

    @Column(name = "subject_cpid", nullable = false)
    private String subjectCpid;

    @Column(name = "revision_number", nullable = false)
    private Integer revisionNumber;

    @Column(name = "dating_method", nullable = false)
    private String datingMethod;

    @Column(name = "basis_observed_on")
    private LocalDate basisObservedOn;

    @Column(name = "last_menstrual_period")
    private LocalDate lastMenstrualPeriod;

    @Column(name = "lmp_certainty")
    private String lmpCertainty;

    @Column(name = "measured_ga_days")
    private Integer measuredGaDays;

    @Column(name = "conception_date")
    private LocalDate conceptionDate;

    @Column(name = "embryo_age_days_at_transfer")
    private Integer embryoAgeDaysAtTransfer;

    @Column(name = "ultrasound_ref")
    private String ultrasoundRef;

    @Column(name = "estimated_delivery_date", nullable = false)
    private LocalDate estimatedDeliveryDate;

    @Column(name = "dating_confidence", nullable = false)
    private String datingConfidence;

    @Column(name = "plus_minus_days")
    private Integer plusMinusDays;

    @Column(name = "superseded_method")
    private String supersededMethod;

    @Column(name = "superseded_edd")
    private LocalDate supersededEdd;

    @Column(name = "discrepancy_days")
    private Integer discrepancyDays;

    @Column(name = "redating_tolerance_days")
    private Integer redatingToleranceDays;

    @Column(name = "redating_applied", nullable = false)
    private Boolean redatingApplied;

    @Column(name = "rationale", nullable = false)
    private String rationale;

    @Column(name = "content_version", nullable = false)
    private String contentVersion;

    @Column(name = "journey_id")
    private String journeyId;

    @Column(name = "encounter_id")
    private String encounterId;

    @Column(name = "recorded_by", nullable = false)
    private String recordedBy;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    public UUID getRevisionId() { return revisionId; }
    public void setRevisionId(UUID revisionId) { this.revisionId = revisionId; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public UUID getPregnancyEpisodeId() { return pregnancyEpisodeId; }
    public void setPregnancyEpisodeId(UUID id) { this.pregnancyEpisodeId = id; }
    public String getSubjectCpid() { return subjectCpid; }
    public void setSubjectCpid(String subjectCpid) { this.subjectCpid = subjectCpid; }
    public Integer getRevisionNumber() { return revisionNumber; }
    public void setRevisionNumber(Integer revisionNumber) { this.revisionNumber = revisionNumber; }
    public String getDatingMethod() { return datingMethod; }
    public void setDatingMethod(String datingMethod) { this.datingMethod = datingMethod; }
    public LocalDate getBasisObservedOn() { return basisObservedOn; }
    public void setBasisObservedOn(LocalDate d) { this.basisObservedOn = d; }
    public LocalDate getLastMenstrualPeriod() { return lastMenstrualPeriod; }
    public void setLastMenstrualPeriod(LocalDate d) { this.lastMenstrualPeriod = d; }
    public String getLmpCertainty() { return lmpCertainty; }
    public void setLmpCertainty(String v) { this.lmpCertainty = v; }
    public Integer getMeasuredGaDays() { return measuredGaDays; }
    public void setMeasuredGaDays(Integer v) { this.measuredGaDays = v; }
    public LocalDate getConceptionDate() { return conceptionDate; }
    public void setConceptionDate(LocalDate d) { this.conceptionDate = d; }
    public Integer getEmbryoAgeDaysAtTransfer() { return embryoAgeDaysAtTransfer; }
    public void setEmbryoAgeDaysAtTransfer(Integer v) { this.embryoAgeDaysAtTransfer = v; }
    public String getUltrasoundRef() { return ultrasoundRef; }
    public void setUltrasoundRef(String v) { this.ultrasoundRef = v; }
    public LocalDate getEstimatedDeliveryDate() { return estimatedDeliveryDate; }
    public void setEstimatedDeliveryDate(LocalDate d) { this.estimatedDeliveryDate = d; }
    public String getDatingConfidence() { return datingConfidence; }
    public void setDatingConfidence(String v) { this.datingConfidence = v; }
    public Integer getPlusMinusDays() { return plusMinusDays; }
    public void setPlusMinusDays(Integer v) { this.plusMinusDays = v; }
    public String getSupersededMethod() { return supersededMethod; }
    public void setSupersededMethod(String v) { this.supersededMethod = v; }
    public LocalDate getSupersededEdd() { return supersededEdd; }
    public void setSupersededEdd(LocalDate d) { this.supersededEdd = d; }
    public Integer getDiscrepancyDays() { return discrepancyDays; }
    public void setDiscrepancyDays(Integer v) { this.discrepancyDays = v; }
    public Integer getRedatingToleranceDays() { return redatingToleranceDays; }
    public void setRedatingToleranceDays(Integer v) { this.redatingToleranceDays = v; }
    public Boolean getRedatingApplied() { return redatingApplied; }
    public void setRedatingApplied(Boolean v) { this.redatingApplied = v; }
    public String getRationale() { return rationale; }
    public void setRationale(String v) { this.rationale = v; }
    public String getContentVersion() { return contentVersion; }
    public void setContentVersion(String v) { this.contentVersion = v; }
    public String getJourneyId() { return journeyId; }
    public void setJourneyId(String v) { this.journeyId = v; }
    public String getEncounterId() { return encounterId; }
    public void setEncounterId(String v) { this.encounterId = v; }
    public String getRecordedBy() { return recordedBy; }
    public void setRecordedBy(String v) { this.recordedBy = v; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime v) { this.createdAt = v; }
}
