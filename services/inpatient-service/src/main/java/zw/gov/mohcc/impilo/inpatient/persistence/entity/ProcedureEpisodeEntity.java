package zw.gov.mohcc.impilo.inpatient.persistence.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "procedure_episode", schema = "inpatient")
public class ProcedureEpisodeEntity {

    /** Default execution setting, matching the V300 column default. */
    public static final String SETTING_THEATRE = "THEATRE";

    @Id
    @Column(name = "episode_id")
    private UUID episodeId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "subject_cpid", nullable = false)
    private String subjectCpid;

    @Column(name = "encounter_id")
    private UUID encounterId;

    @Column(name = "admission_ref")
    private UUID admissionRef;

    /** daidzai-minted trauma episode this surgery originates from (null for elective/non-trauma). */
    @Column(name = "trauma_episode_id")
    private UUID traumaEpisodeId;

    @Column(name = "booking_id")
    private String bookingId;

    @Column(name = "theatre_id")
    private UUID theatreId;

    @Column(name = "procedure_name", nullable = false)
    private String procedureName;

    @Column(name = "procedure_code")
    private String procedureCode;

    @Column(name = "scheduled_at")
    private OffsetDateTime scheduledAt;

    @Column(name = "surgeon_id")
    private String surgeonId;

    @Column(name = "anaesthetist_id")
    private String anaesthetistId;

    @Column(name = "status", nullable = false)
    private String status = "BOOKED";

    /**
     * Optimistic-lock version (Wave 6 §18). Guards against a lost update when two
     * clinicians edit the SAME episode concurrently: a stale write throws an
     * optimistic-lock failure that {@code GlobalExceptionHandler} surfaces as HTTP 409
     * STALE_WRITE, rather than silently clobbering the concurrent change.
     */
    @Version
    @Column(name = "version", nullable = false)
    private long version;

    // ── Surgery + Procedures Pipeline P4: structured site and side, and the setting
    //    discriminator that lets a non-theatre procedure execute on this engine. Additive:
    //    `status` is untouched and every existing consumer is unaffected.
    @Column(name = "anatomical_site", length = 128)
    private String anatomicalSite;

    /** Closed vocabulary. A wrong-side check that compares free text fails open. */
    @Column(name = "laterality", length = 24)
    private String laterality;

    @Column(name = "site_side_confirmed_by", length = 128)
    private String siteSideConfirmedBy;

    @Column(name = "site_side_confirmed_at")
    private java.time.OffsetDateTime siteSideConfirmedAt;

    /**
     * The column is NOT NULL DEFAULT 'THEATRE' (V300). A database default only applies when the
     * column is omitted from the INSERT, and Hibernate always names every mapped column, so the
     * default never reaches a JPA-created row — it must be mirrored here.
     */
    @Column(name = "setting", length = 48, nullable = false)
    private String setting = SETTING_THEATRE;

    @Column(name = "catalogue_ref")
    private java.util.UUID catalogueRef;

    @Column(name = "request_ref", length = 64)
    private String requestRef;

    @Column(name = "lifecycle_state", length = 32)
    private String lifecycleState;

    @Column(name = "consent_verified", nullable = false)
    private boolean consentVerified;

    // ── Wave P12 (pipeline §23) — financial clearance projection. COSTA is asked fresh on
    // every start attempt (ProcedureEpisodeService.requireFinancialClearance); these columns
    // are the audit trail, not the source of truth. ──
    @Column(name = "financial_clearance_status", length = 48)
    private String financialClearanceStatus;

    @Column(name = "financial_clearance_checked_by", length = 128)
    private String financialClearanceCheckedBy;

    @Column(name = "financial_clearance_checked_at")
    private java.time.OffsetDateTime financialClearanceCheckedAt;

    @Column(name = "mvumo_consent_request_id")
    private UUID mvumoConsentRequestId;

    @Column(name = "tshepo_consent_id")
    private UUID tshepoConsentId;

    @Column(name = "consent_proof_ref")
    private String consentProofRef;

    @Column(name = "consent_status", nullable = false)
    private String consentStatus = "PENDING";

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    // -- Theatre & perioperative depth (V018) --
    @Column(name = "oros_order_id")
    private String orosOrderId;

    @Column(name = "triage_priority", nullable = false)
    private String triagePriority = "ELECTIVE";

    @Column(name = "theatre_room_id")
    private UUID theatreRoomId;

    @Column(name = "scrub_nurse_id")
    private String scrubNurseId;

    @Column(name = "cancellation_reason", columnDefinition = "TEXT")
    private String cancellationReason;

    @Column(name = "cancelled_by")
    private String cancelledBy;

    @Column(name = "cancelled_at")
    private OffsetDateTime cancelledAt;

    @Column(name = "emergency_override", nullable = false)
    private boolean emergencyOverride;

    @Column(name = "emergency_override_reason", columnDefinition = "TEXT")
    private String emergencyOverrideReason;

    @Column(name = "death_case_ref")
    private String deathCaseRef;

    /**
     * V305 — the episode this one re-operates, when a return to theatre warranted a NEW episode
     * rather than reopening this one in place. A return that keeps the same episode (the ordinary
     * case, see {@code procedure_return_to_theatre}) leaves this null.
     */
    @Column(name = "reoperation_of_episode_id")
    private UUID reoperationOfEpisodeId;

    @PrePersist
    void onCreate() {
        if (episodeId == null) episodeId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    public UUID getEpisodeId() { return episodeId; }
    public void setEpisodeId(UUID episodeId) { this.episodeId = episodeId; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public String getSubjectCpid() { return subjectCpid; }
    public void setSubjectCpid(String subjectCpid) { this.subjectCpid = subjectCpid; }
    public UUID getEncounterId() { return encounterId; }
    public void setEncounterId(UUID encounterId) { this.encounterId = encounterId; }
    public UUID getAdmissionRef() { return admissionRef; }
    public void setAdmissionRef(UUID admissionRef) { this.admissionRef = admissionRef; }
    public UUID getTraumaEpisodeId() { return traumaEpisodeId; }
    public void setTraumaEpisodeId(UUID traumaEpisodeId) { this.traumaEpisodeId = traumaEpisodeId; }
    public String getBookingId() { return bookingId; }
    public void setBookingId(String bookingId) { this.bookingId = bookingId; }
    public UUID getTheatreId() { return theatreId; }
    public void setTheatreId(UUID theatreId) { this.theatreId = theatreId; }
    public String getProcedureName() { return procedureName; }
    public void setProcedureName(String procedureName) { this.procedureName = procedureName; }
    public String getProcedureCode() { return procedureCode; }
    public void setProcedureCode(String procedureCode) { this.procedureCode = procedureCode; }
    public OffsetDateTime getScheduledAt() { return scheduledAt; }
    public void setScheduledAt(OffsetDateTime scheduledAt) { this.scheduledAt = scheduledAt; }
    public String getSurgeonId() { return surgeonId; }
    public void setSurgeonId(String surgeonId) { this.surgeonId = surgeonId; }
    public String getAnaesthetistId() { return anaesthetistId; }
    public void setAnaesthetistId(String anaesthetistId) { this.anaesthetistId = anaesthetistId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public long getVersion() { return version; }
    public void setVersion(long version) { this.version = version; }
    public boolean isConsentVerified() { return consentVerified; }
    public void setConsentVerified(boolean consentVerified) { this.consentVerified = consentVerified; }
    public String getFinancialClearanceStatus() { return financialClearanceStatus; }
    public void setFinancialClearanceStatus(String v) { this.financialClearanceStatus = v; }
    public String getFinancialClearanceCheckedBy() { return financialClearanceCheckedBy; }
    public void setFinancialClearanceCheckedBy(String v) { this.financialClearanceCheckedBy = v; }
    public java.time.OffsetDateTime getFinancialClearanceCheckedAt() { return financialClearanceCheckedAt; }
    public void setFinancialClearanceCheckedAt(java.time.OffsetDateTime v) { this.financialClearanceCheckedAt = v; }
    public UUID getMvumoConsentRequestId() { return mvumoConsentRequestId; }
    public void setMvumoConsentRequestId(UUID mvumoConsentRequestId) { this.mvumoConsentRequestId = mvumoConsentRequestId; }
    public UUID getTshepoConsentId() { return tshepoConsentId; }
    public void setTshepoConsentId(UUID tshepoConsentId) { this.tshepoConsentId = tshepoConsentId; }
    public String getConsentProofRef() { return consentProofRef; }
    public void setConsentProofRef(String consentProofRef) { this.consentProofRef = consentProofRef; }
    public String getConsentStatus() { return consentStatus; }
    public void setConsentStatus(String consentStatus) { this.consentStatus = consentStatus; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
    public OffsetDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(OffsetDateTime completedAt) { this.completedAt = completedAt; }
    public String getOrosOrderId() { return orosOrderId; }
    public void setOrosOrderId(String orosOrderId) { this.orosOrderId = orosOrderId; }
    public String getTriagePriority() { return triagePriority; }
    public void setTriagePriority(String triagePriority) { this.triagePriority = triagePriority; }
    public UUID getTheatreRoomId() { return theatreRoomId; }
    public void setTheatreRoomId(UUID theatreRoomId) { this.theatreRoomId = theatreRoomId; }
    public String getScrubNurseId() { return scrubNurseId; }
    public void setScrubNurseId(String scrubNurseId) { this.scrubNurseId = scrubNurseId; }
    public String getCancellationReason() { return cancellationReason; }
    public void setCancellationReason(String cancellationReason) { this.cancellationReason = cancellationReason; }
    public String getCancelledBy() { return cancelledBy; }
    public void setCancelledBy(String cancelledBy) { this.cancelledBy = cancelledBy; }
    public OffsetDateTime getCancelledAt() { return cancelledAt; }
    public void setCancelledAt(OffsetDateTime cancelledAt) { this.cancelledAt = cancelledAt; }
    public boolean isEmergencyOverride() { return emergencyOverride; }
    public void setEmergencyOverride(boolean emergencyOverride) { this.emergencyOverride = emergencyOverride; }
    public String getEmergencyOverrideReason() { return emergencyOverrideReason; }
    public void setEmergencyOverrideReason(String emergencyOverrideReason) { this.emergencyOverrideReason = emergencyOverrideReason; }
    public String getDeathCaseRef() { return deathCaseRef; }
    public void setDeathCaseRef(String deathCaseRef) { this.deathCaseRef = deathCaseRef; }

    public String getAnatomicalSite() { return anatomicalSite; }
    public void setAnatomicalSite(String v) { this.anatomicalSite = v; }
    public String getLaterality() { return laterality; }
    public void setLaterality(String v) { this.laterality = v; }
    public String getSiteSideConfirmedBy() { return siteSideConfirmedBy; }
    public void setSiteSideConfirmedBy(String v) { this.siteSideConfirmedBy = v; }
    public java.time.OffsetDateTime getSiteSideConfirmedAt() { return siteSideConfirmedAt; }
    public void setSiteSideConfirmedAt(java.time.OffsetDateTime v) { this.siteSideConfirmedAt = v; }
    public UUID getReoperationOfEpisodeId() { return reoperationOfEpisodeId; }
    public void setReoperationOfEpisodeId(UUID v) { this.reoperationOfEpisodeId = v; }
    public String getSetting() { return setting; }
    public void setSetting(String v) { this.setting = (v == null || v.isBlank()) ? SETTING_THEATRE : v; }
    public java.util.UUID getCatalogueRef() { return catalogueRef; }
    public void setCatalogueRef(java.util.UUID v) { this.catalogueRef = v; }
    public String getRequestRef() { return requestRef; }
    public void setRequestRef(String v) { this.requestRef = v; }
    public String getLifecycleState() { return lifecycleState; }
    public void setLifecycleState(String v) { this.lifecycleState = v; }
}
