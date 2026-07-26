package zw.gov.mohcc.impilo.pct.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * A single discrete clinical observation: one coded fact about one person, taken at one moment,
 * inside one care context.
 *
 * <p>The row carries either a value or a {@code dataAbsentReason}, never neither. That is the
 * distinction between "the child's temperature is normal" and "nobody took the child's
 * temperature" — a schema that can only store values forces the absence to be encoded as a
 * missing row, and a missing row is indistinguishable from a question never asked. The database
 * enforces this with a CHECK constraint; the service rejects it earlier with a 400.</p>
 *
 * <p>The interpretation and the reference range used to reach it are both stored, so a later
 * revision of the range cannot silently reinterpret history. Provenance
 * ({@code derivedFromType}/{@code derivedFromId}) makes an observation extracted from a form
 * traceable back to the answer it came from, so a correction to the form can be reconciled with
 * the derived row rather than accumulating a duplicate that disagrees with it.</p>
 *
 * <p>CC-5: every observation carries {@code subjectCpid} plus the journey/encounter it was taken
 * in, so it resolves to the person's care continuum rather than floating beside it.</p>
 */
@Entity
@Table(name = "pct_observations", schema = "pct")
public class ObservationEntity {

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

    /** The identity of the fact. Matched on; the display never is. */
    @Column(name = "code", nullable = false)
    private String code;

    @Column(name = "code_system", nullable = false)
    private String codeSystem = "impilo-clinical-observation";

    @Column(name = "display")
    private String display;

    /** VITAL_SIGNS | ANTHROPOMETRY | EXAM | SURVEY | LABORATORY | SOCIAL_HISTORY | THERAPY */
    @Column(name = "category", nullable = false)
    private String category = "EXAM";

    @Column(name = "effective_at", nullable = false)
    private OffsetDateTime effectiveAt;

    @Column(name = "recorded_by", nullable = false)
    private String recordedBy;

    @Column(name = "recorded_at", nullable = false)
    private OffsetDateTime recordedAt = OffsetDateTime.now();

    @Column(name = "value_quantity")
    private BigDecimal valueQuantity;

    @Column(name = "value_unit")
    private String valueUnit;

    @Column(name = "value_code")
    private String valueCode;

    @Column(name = "value_code_system")
    private String valueCodeSystem;

    @Column(name = "value_boolean")
    private Boolean valueBoolean;

    @Column(name = "value_text")
    private String valueText;

    /** NOT_ASSESSED | ASKED_BUT_UNKNOWN | REFUSED | UNABLE_TO_OBTAIN | NOT_PERFORMED | ERROR */
    @Column(name = "data_absent_reason")
    private String dataAbsentReason;

    /** NORMAL | LOW | HIGH | CRITICAL_LOW | CRITICAL_HIGH | ABNORMAL */
    @Column(name = "interpretation")
    private String interpretation;

    @Column(name = "reference_range_low")
    private BigDecimal referenceRangeLow;

    @Column(name = "reference_range_high")
    private BigDecimal referenceRangeHigh;

    @Column(name = "reference_range_source")
    private String referenceRangeSource;

    /** MANUAL | FORM | DEVICE | DERIVED | IMPORTED */
    @Column(name = "source", nullable = false)
    private String source = "MANUAL";

    /** FORM_RESPONSE | GROWTH_MEASUREMENT | LABOUR_OBSERVATION */
    @Column(name = "derived_from_type")
    private String derivedFromType;

    @Column(name = "derived_from_id")
    private String derivedFromId;

    @Column(name = "device_ref")
    private String deviceRef;

    /** FINAL | PRELIMINARY | AMENDED | ENTERED_IN_ERROR */
    @Column(name = "status", nullable = false)
    private String status = "FINAL";

    @Column(name = "notes")
    private String notes;

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

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getCodeSystem() {
        return codeSystem;
    }

    public void setCodeSystem(String codeSystem) {
        this.codeSystem = codeSystem;
    }

    public String getDisplay() {
        return display;
    }

    public void setDisplay(String display) {
        this.display = display;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public OffsetDateTime getEffectiveAt() {
        return effectiveAt;
    }

    public void setEffectiveAt(OffsetDateTime effectiveAt) {
        this.effectiveAt = effectiveAt;
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

    public BigDecimal getValueQuantity() {
        return valueQuantity;
    }

    public void setValueQuantity(BigDecimal valueQuantity) {
        this.valueQuantity = valueQuantity;
    }

    public String getValueUnit() {
        return valueUnit;
    }

    public void setValueUnit(String valueUnit) {
        this.valueUnit = valueUnit;
    }

    public String getValueCode() {
        return valueCode;
    }

    public void setValueCode(String valueCode) {
        this.valueCode = valueCode;
    }

    public String getValueCodeSystem() {
        return valueCodeSystem;
    }

    public void setValueCodeSystem(String valueCodeSystem) {
        this.valueCodeSystem = valueCodeSystem;
    }

    public Boolean getValueBoolean() {
        return valueBoolean;
    }

    public void setValueBoolean(Boolean valueBoolean) {
        this.valueBoolean = valueBoolean;
    }

    public String getValueText() {
        return valueText;
    }

    public void setValueText(String valueText) {
        this.valueText = valueText;
    }

    public String getDataAbsentReason() {
        return dataAbsentReason;
    }

    public void setDataAbsentReason(String dataAbsentReason) {
        this.dataAbsentReason = dataAbsentReason;
    }

    public String getInterpretation() {
        return interpretation;
    }

    public void setInterpretation(String interpretation) {
        this.interpretation = interpretation;
    }

    public BigDecimal getReferenceRangeLow() {
        return referenceRangeLow;
    }

    public void setReferenceRangeLow(BigDecimal referenceRangeLow) {
        this.referenceRangeLow = referenceRangeLow;
    }

    public BigDecimal getReferenceRangeHigh() {
        return referenceRangeHigh;
    }

    public void setReferenceRangeHigh(BigDecimal referenceRangeHigh) {
        this.referenceRangeHigh = referenceRangeHigh;
    }

    public String getReferenceRangeSource() {
        return referenceRangeSource;
    }

    public void setReferenceRangeSource(String referenceRangeSource) {
        this.referenceRangeSource = referenceRangeSource;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getDerivedFromType() {
        return derivedFromType;
    }

    public void setDerivedFromType(String derivedFromType) {
        this.derivedFromType = derivedFromType;
    }

    public String getDerivedFromId() {
        return derivedFromId;
    }

    public void setDerivedFromId(String derivedFromId) {
        this.derivedFromId = derivedFromId;
    }

    public String getDeviceRef() {
        return deviceRef;
    }

    public void setDeviceRef(String deviceRef) {
        this.deviceRef = deviceRef;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }
}
