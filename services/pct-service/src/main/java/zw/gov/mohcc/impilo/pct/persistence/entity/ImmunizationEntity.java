package zw.gov.mohcc.impilo.pct.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * One immunisation dose fact: what was given, to whom, when, from which vial and by whom —
 * or, where a dose was not given, the clinically distinct reason why.
 *
 * <p>This is a record of administration, not a schedule. What is due is evaluated against
 * governed national schedule content elsewhere, so a policy change never rewrites a
 * historical dose.</p>
 */
@Entity
@Table(name = "pct_immunizations")
public class ImmunizationEntity {

    /** A dose that was actually administered. */
    public static final String STATUS_COMPLETED = "COMPLETED";
    /** Caregiver declined. Not the same as never offered, and not a contraindication. */
    public static final String STATUS_REFUSED = "NOT_DONE_REFUSED";
    /** Withheld for a clinical contraindication. */
    public static final String STATUS_CONTRAINDICATED = "NOT_DONE_CONTRAINDICATED";
    /** Postponed to a stated date, e.g. for acute illness or stock-out. */
    public static final String STATUS_DEFERRED = "NOT_DONE_DEFERRED";
    /** Reported as given elsewhere; counts for forecasting only once verified. */
    public static final String STATUS_PENDING_VERIFICATION = "RECORDED_ELSEWHERE_PENDING_VERIFICATION";
    /** Recorded in error; retained rather than deleted so the correction is auditable. */
    public static final String STATUS_ENTERED_IN_ERROR = "ENTERED_IN_ERROR";

    @Id
    @Column(name = "immunization_id")
    private UUID immunizationId;

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

    @Column(name = "vaccine_code")
    private String vaccineCode;

    @Column(name = "code_system")
    private String codeSystem;

    @Column(name = "vaccine_name", nullable = false)
    private String vaccineName;

    @Column(name = "dose_number")
    private Integer doseNumber;

    @Column(name = "series")
    private String series;

    @Column(name = "occurrence_at", nullable = false)
    private OffsetDateTime occurrenceAt;

    @Column(name = "lot_number")
    private String lotNumber;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @Column(name = "diluent_lot_number")
    private String diluentLotNumber;

    @Column(name = "dose_quantity")
    private String doseQuantity;

    @Column(name = "route")
    private String route;

    @Column(name = "body_site")
    private String bodySite;

    @Column(name = "administered_by")
    private String administeredBy;

    @Column(name = "delivery_context", nullable = false)
    private String deliveryContext = "ROUTINE";

    @Column(name = "campaign_ref")
    private String campaignRef;

    @Column(name = "outreach_location")
    private String outreachLocation;

    @Column(name = "status", nullable = false)
    private String status = STATUS_COMPLETED;

    @Column(name = "status_reason")
    private String statusReason;

    @Column(name = "deferred_until")
    private LocalDate deferredUntil;

    @Column(name = "source_reference")
    private String sourceReference;

    @Column(name = "verified_by")
    private String verifiedBy;

    @Column(name = "verified_at")
    private OffsetDateTime verifiedAt;

    @Column(name = "aefi_case_reference")
    private String aefiCaseReference;

    @Column(name = "notes")
    private String notes;

    @Column(name = "recorded_by", nullable = false)
    private String recordedBy;

    @Column(name = "created_at")
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    /** True only for a dose that can be counted towards a series. */
    public boolean countsTowardsSeries() {
        return STATUS_COMPLETED.equals(status);
    }

    public UUID getImmunizationId() {
        return immunizationId;
    }

    public void setImmunizationId(UUID immunizationId) {
        this.immunizationId = immunizationId;
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

    public String getVaccineCode() {
        return vaccineCode;
    }

    public void setVaccineCode(String vaccineCode) {
        this.vaccineCode = vaccineCode;
    }

    public String getCodeSystem() {
        return codeSystem;
    }

    public void setCodeSystem(String codeSystem) {
        this.codeSystem = codeSystem;
    }

    public String getVaccineName() {
        return vaccineName;
    }

    public void setVaccineName(String vaccineName) {
        this.vaccineName = vaccineName;
    }

    public Integer getDoseNumber() {
        return doseNumber;
    }

    public void setDoseNumber(Integer doseNumber) {
        this.doseNumber = doseNumber;
    }

    public String getSeries() {
        return series;
    }

    public void setSeries(String series) {
        this.series = series;
    }

    public OffsetDateTime getOccurrenceAt() {
        return occurrenceAt;
    }

    public void setOccurrenceAt(OffsetDateTime occurrenceAt) {
        this.occurrenceAt = occurrenceAt;
    }

    public String getLotNumber() {
        return lotNumber;
    }

    public void setLotNumber(String lotNumber) {
        this.lotNumber = lotNumber;
    }

    public LocalDate getExpiryDate() {
        return expiryDate;
    }

    public void setExpiryDate(LocalDate expiryDate) {
        this.expiryDate = expiryDate;
    }

    public String getDiluentLotNumber() {
        return diluentLotNumber;
    }

    public void setDiluentLotNumber(String diluentLotNumber) {
        this.diluentLotNumber = diluentLotNumber;
    }

    public String getDoseQuantity() {
        return doseQuantity;
    }

    public void setDoseQuantity(String doseQuantity) {
        this.doseQuantity = doseQuantity;
    }

    public String getRoute() {
        return route;
    }

    public void setRoute(String route) {
        this.route = route;
    }

    public String getBodySite() {
        return bodySite;
    }

    public void setBodySite(String bodySite) {
        this.bodySite = bodySite;
    }

    public String getAdministeredBy() {
        return administeredBy;
    }

    public void setAdministeredBy(String administeredBy) {
        this.administeredBy = administeredBy;
    }

    public String getDeliveryContext() {
        return deliveryContext;
    }

    public void setDeliveryContext(String deliveryContext) {
        this.deliveryContext = deliveryContext;
    }

    public String getCampaignRef() {
        return campaignRef;
    }

    public void setCampaignRef(String campaignRef) {
        this.campaignRef = campaignRef;
    }

    public String getOutreachLocation() {
        return outreachLocation;
    }

    public void setOutreachLocation(String outreachLocation) {
        this.outreachLocation = outreachLocation;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getStatusReason() {
        return statusReason;
    }

    public void setStatusReason(String statusReason) {
        this.statusReason = statusReason;
    }

    public LocalDate getDeferredUntil() {
        return deferredUntil;
    }

    public void setDeferredUntil(LocalDate deferredUntil) {
        this.deferredUntil = deferredUntil;
    }

    public String getSourceReference() {
        return sourceReference;
    }

    public void setSourceReference(String sourceReference) {
        this.sourceReference = sourceReference;
    }

    public String getVerifiedBy() {
        return verifiedBy;
    }

    public void setVerifiedBy(String verifiedBy) {
        this.verifiedBy = verifiedBy;
    }

    public OffsetDateTime getVerifiedAt() {
        return verifiedAt;
    }

    public void setVerifiedAt(OffsetDateTime verifiedAt) {
        this.verifiedAt = verifiedAt;
    }

    public String getAefiCaseReference() {
        return aefiCaseReference;
    }

    public void setAefiCaseReference(String aefiCaseReference) {
        this.aefiCaseReference = aefiCaseReference;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
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
