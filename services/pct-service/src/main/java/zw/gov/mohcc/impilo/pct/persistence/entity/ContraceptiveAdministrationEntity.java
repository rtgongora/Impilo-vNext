package zw.gov.mohcc.impilo.pct.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * One act on a contraceptive episode: an insertion, an injection, a resupply, a removal.
 *
 * <p>Separate from the episode because the timing of these acts is the clinical content. An
 * injection given six weeks late and one given on time produce the same episode row and completely
 * different protection, and {@code nextDueOn} is what makes a defaulter list possible at all.
 *
 * <p><b>A removal must state whether everything came out.</b> The database refuses a removal marked
 * complete where a fragment was retained or the counts disagree. A two-rod implant with one rod left
 * behind, recorded as complete, leaves a woman carrying a rod nobody knows about while her record
 * says she has no method — she is then counted as needing contraception and offered one on top of
 * the implant still in her arm.
 */
@Entity
@Table(name = "pct_contraceptive_administrations")
public class ContraceptiveAdministrationEntity {

    public static final String TYPE_INSERTION = "INSERTION";
    public static final String TYPE_INJECTION = "INJECTION";
    public static final String TYPE_RESUPPLY = "RESUPPLY";
    public static final String TYPE_REMOVAL = "REMOVAL";
    public static final String TYPE_REPLACEMENT = "REPLACEMENT";

    public static final String REMOVAL_COMPLETE = "COMPLETE";
    public static final String REMOVAL_PARTIAL = "PARTIAL";
    public static final String REMOVAL_FAILED = "FAILED";

    @Id
    @Column(name = "administration_id")
    private UUID administrationId;

    @Column(name = "contraceptive_episode_id")
    private UUID contraceptiveEpisodeId;

    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(name = "subject_cpid")
    private String subjectCpid;

    @Column(name = "administration_type")
    private String administrationType;

    @Column(name = "administered_on")
    private LocalDate administeredOn;

    @Column(name = "next_due_on")
    private LocalDate nextDueOn;

    @Column(name = "units_placed")
    private Short unitsPlaced;

    @Column(name = "units_expected")
    private Short unitsExpected;

    @Column(name = "units_removed")
    private Short unitsRemoved;

    @Column(name = "removal_completeness")
    private String removalCompleteness;

    @Column(name = "retained_fragment")
    private Boolean retainedFragment;

    @Column(name = "retained_fragment_detail")
    private String retainedFragmentDetail;

    @Column(name = "site")
    private String site;

    @Column(name = "lot_number")
    private String lotNumber;

    @Column(name = "notes")
    private String notes;

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

    @Column(name = "client_offline_id")
    private String clientOfflineId;

    /** True when this act ends the method's protection — a removal that actually got everything out. */
    public boolean endsProtection() {
        return TYPE_REMOVAL.equals(administrationType) && REMOVAL_COMPLETE.equals(removalCompleteness);
    }

    public UUID getAdministrationId() {
        return administrationId;
    }

    public void setAdministrationId(UUID administrationId) {
        this.administrationId = administrationId;
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

    public String getAdministrationType() {
        return administrationType;
    }

    public void setAdministrationType(String administrationType) {
        this.administrationType = administrationType;
    }

    public LocalDate getAdministeredOn() {
        return administeredOn;
    }

    public void setAdministeredOn(LocalDate administeredOn) {
        this.administeredOn = administeredOn;
    }

    public LocalDate getNextDueOn() {
        return nextDueOn;
    }

    public void setNextDueOn(LocalDate nextDueOn) {
        this.nextDueOn = nextDueOn;
    }

    public Short getUnitsPlaced() {
        return unitsPlaced;
    }

    public void setUnitsPlaced(Short unitsPlaced) {
        this.unitsPlaced = unitsPlaced;
    }

    public Short getUnitsExpected() {
        return unitsExpected;
    }

    public void setUnitsExpected(Short unitsExpected) {
        this.unitsExpected = unitsExpected;
    }

    public Short getUnitsRemoved() {
        return unitsRemoved;
    }

    public void setUnitsRemoved(Short unitsRemoved) {
        this.unitsRemoved = unitsRemoved;
    }

    public String getRemovalCompleteness() {
        return removalCompleteness;
    }

    public void setRemovalCompleteness(String removalCompleteness) {
        this.removalCompleteness = removalCompleteness;
    }

    public Boolean getRetainedFragment() {
        return retainedFragment;
    }

    public void setRetainedFragment(Boolean retainedFragment) {
        this.retainedFragment = retainedFragment;
    }

    public String getRetainedFragmentDetail() {
        return retainedFragmentDetail;
    }

    public void setRetainedFragmentDetail(String retainedFragmentDetail) {
        this.retainedFragmentDetail = retainedFragmentDetail;
    }

    public String getSite() {
        return site;
    }

    public void setSite(String site) {
        this.site = site;
    }

    public String getLotNumber() {
        return lotNumber;
    }

    public void setLotNumber(String lotNumber) {
        this.lotNumber = lotNumber;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
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

    public String getClientOfflineId() {
        return clientOfflineId;
    }

    public void setClientOfflineId(String clientOfflineId) {
        this.clientOfflineId = clientOfflineId;
    }
}
