package zw.gov.mohcc.impilo.pct.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * A single labour observation: the fetal condition, the progress of labour, and the maternal
 * condition as they stood at one moment, together with the interventions running at the time.
 *
 * <p>A partograph point IS a labour observation. The only difference is whether a partograph
 * session was open when it was taken, which is why {@code partographSessionId} is nullable and
 * there is one table rather than two. Two tables would mean two systems of record for the same
 * fact, and a graph drawn from one while the labour record listed the other would disagree
 * about the same woman.</p>
 */
@Entity
@Table(name = "pct_labour_observations", schema = "pct")
public class LabourObservationEntity {

    @Id
    @Column(name = "observation_id")
    private UUID observationId;

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

    @Column(name = "partograph_session_id")
    private UUID partographSessionId;

    @Column(name = "observed_at", nullable = false)
    private OffsetDateTime observedAt;

    @Column(name = "recorded_by", nullable = false)
    private String recordedBy;

    @Column(name = "phase", nullable = false)
    private String phase = "ACTIVE_LABOUR";

    @Column(name = "fetal_heart_rate_bpm")
    private Integer fetalHeartRateBpm;

    @Column(name = "liquor")
    private String liquor;

    @Column(name = "moulding")
    private String moulding;

    @Column(name = "caput")
    private String caput;

    @Column(name = "cervical_dilation_cm")
    private BigDecimal cervicalDilationCm;

    @Column(name = "fetal_descent_fifths")
    private Integer fetalDescentFifths;

    @Column(name = "contraction_frequency_10min")
    private Integer contractionFrequency10min;

    @Column(name = "contraction_duration_sec")
    private Integer contractionDurationSec;

    @Column(name = "maternal_pulse_bpm")
    private Integer maternalPulseBpm;

    @Column(name = "systolic_bp")
    private Integer systolicBp;

    @Column(name = "diastolic_bp")
    private Integer diastolicBp;

    @Column(name = "temperature_c")
    private BigDecimal temperatureC;

    @Column(name = "urine_volume_ml")
    private Integer urineVolumeMl;

    @Column(name = "urine_protein")
    private String urineProtein;

    @Column(name = "urine_acetone")
    private String urineAcetone;

    @Column(name = "maternal_condition")
    private String maternalCondition;

    @Column(name = "oxytocin_rate_miu_min")
    private BigDecimal oxytocinRateMiuMin;

    @Column(name = "notes")
    private String notes;

    @Column(name = "created_at")
    private OffsetDateTime createdAt = OffsetDateTime.now();

    public UUID getObservationId() {
        return observationId;
    }

    public void setObservationId(UUID observationId) {
        this.observationId = observationId;
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

    public UUID getPartographSessionId() {
        return partographSessionId;
    }

    public void setPartographSessionId(UUID partographSessionId) {
        this.partographSessionId = partographSessionId;
    }

    public OffsetDateTime getObservedAt() {
        return observedAt;
    }

    public void setObservedAt(OffsetDateTime observedAt) {
        this.observedAt = observedAt;
    }

    public String getRecordedBy() {
        return recordedBy;
    }

    public void setRecordedBy(String recordedBy) {
        this.recordedBy = recordedBy;
    }

    public String getPhase() {
        return phase;
    }

    public void setPhase(String phase) {
        this.phase = phase;
    }

    public Integer getFetalHeartRateBpm() {
        return fetalHeartRateBpm;
    }

    public void setFetalHeartRateBpm(Integer fetalHeartRateBpm) {
        this.fetalHeartRateBpm = fetalHeartRateBpm;
    }

    public String getLiquor() {
        return liquor;
    }

    public void setLiquor(String liquor) {
        this.liquor = liquor;
    }

    public String getMoulding() {
        return moulding;
    }

    public void setMoulding(String moulding) {
        this.moulding = moulding;
    }

    public String getCaput() {
        return caput;
    }

    public void setCaput(String caput) {
        this.caput = caput;
    }

    public BigDecimal getCervicalDilationCm() {
        return cervicalDilationCm;
    }

    public void setCervicalDilationCm(BigDecimal cervicalDilationCm) {
        this.cervicalDilationCm = cervicalDilationCm;
    }

    public Integer getFetalDescentFifths() {
        return fetalDescentFifths;
    }

    public void setFetalDescentFifths(Integer fetalDescentFifths) {
        this.fetalDescentFifths = fetalDescentFifths;
    }

    public Integer getContractionFrequency10min() {
        return contractionFrequency10min;
    }

    public void setContractionFrequency10min(Integer contractionFrequency10min) {
        this.contractionFrequency10min = contractionFrequency10min;
    }

    public Integer getContractionDurationSec() {
        return contractionDurationSec;
    }

    public void setContractionDurationSec(Integer contractionDurationSec) {
        this.contractionDurationSec = contractionDurationSec;
    }

    public Integer getMaternalPulseBpm() {
        return maternalPulseBpm;
    }

    public void setMaternalPulseBpm(Integer maternalPulseBpm) {
        this.maternalPulseBpm = maternalPulseBpm;
    }

    public Integer getSystolicBp() {
        return systolicBp;
    }

    public void setSystolicBp(Integer systolicBp) {
        this.systolicBp = systolicBp;
    }

    public Integer getDiastolicBp() {
        return diastolicBp;
    }

    public void setDiastolicBp(Integer diastolicBp) {
        this.diastolicBp = diastolicBp;
    }

    public BigDecimal getTemperatureC() {
        return temperatureC;
    }

    public void setTemperatureC(BigDecimal temperatureC) {
        this.temperatureC = temperatureC;
    }

    public Integer getUrineVolumeMl() {
        return urineVolumeMl;
    }

    public void setUrineVolumeMl(Integer urineVolumeMl) {
        this.urineVolumeMl = urineVolumeMl;
    }

    public String getUrineProtein() {
        return urineProtein;
    }

    public void setUrineProtein(String urineProtein) {
        this.urineProtein = urineProtein;
    }

    public String getUrineAcetone() {
        return urineAcetone;
    }

    public void setUrineAcetone(String urineAcetone) {
        this.urineAcetone = urineAcetone;
    }

    public String getMaternalCondition() {
        return maternalCondition;
    }

    public void setMaternalCondition(String maternalCondition) {
        this.maternalCondition = maternalCondition;
    }

    public BigDecimal getOxytocinRateMiuMin() {
        return oxytocinRateMiuMin;
    }

    public void setOxytocinRateMiuMin(BigDecimal oxytocinRateMiuMin) {
        this.oxytocinRateMiuMin = oxytocinRateMiuMin;
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
