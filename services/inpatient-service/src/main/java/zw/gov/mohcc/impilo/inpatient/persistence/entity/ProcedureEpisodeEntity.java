package zw.gov.mohcc.impilo.inpatient.persistence.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "procedure_episode", schema = "inpatient")
public class ProcedureEpisodeEntity {

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

    @Column(name = "consent_verified", nullable = false)
    private boolean consentVerified;

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
    public boolean isConsentVerified() { return consentVerified; }
    public void setConsentVerified(boolean consentVerified) { this.consentVerified = consentVerified; }
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
}
