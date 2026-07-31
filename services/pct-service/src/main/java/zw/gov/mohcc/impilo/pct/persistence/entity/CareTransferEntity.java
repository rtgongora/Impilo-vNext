package zw.gov.mohcc.impilo.pct.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Transfer of care — the act that moves ownership (brief.md §14, §23.10).
 *
 * <p>A consultation may recommend a takeover; this record is what performs one. Ownership of the
 * patient moves only when status becomes {@code ACCEPTED} with a non-blank {@link #acceptingRef}.
 * See {@code V116__transfer_of_care.sql}.</p>
 */
@Entity
@Table(name = "pct_care_transfers")
public class CareTransferEntity {

    @Id
    @Column(name = "transfer_id")
    private UUID transferId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "subject_cpid", nullable = false)
    private String subjectCpid;

    @Column(name = "journey_id")
    private UUID journeyId;

    @Column(name = "encounter_id")
    private UUID encounterId;

    @Column(name = "consultation_id")
    private UUID consultationId;

    @Column(name = "from_service", nullable = false)
    private String fromService;

    @Column(name = "to_service", nullable = false)
    private String toService;

    @Column(name = "requested_by", nullable = false)
    private String requestedBy;

    @Column(name = "requested_at", nullable = false)
    private OffsetDateTime requestedAt;

    @Column(name = "reason", nullable = false)
    private String reason;

    @Column(name = "status", nullable = false)
    private String status = "PENDING";

    @Column(name = "accepted_by")
    private String acceptedBy;

    @Column(name = "accepted_at")
    private OffsetDateTime acceptedAt;

    /** Receiving service's own record id — the handshake. */
    @Column(name = "accepting_ref")
    private String acceptingRef;

    @Column(name = "declined_by")
    private String declinedBy;

    @Column(name = "declined_at")
    private OffsetDateTime declinedAt;

    @Column(name = "decline_reason")
    private String declineReason;

    @Column(name = "withdrawn_by")
    private String withdrawnBy;

    @Column(name = "withdrawn_at")
    private OffsetDateTime withdrawnAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (transferId == null) transferId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        if (createdAt == null) createdAt = now;
        if (requestedAt == null) requestedAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() { updatedAt = OffsetDateTime.now(); }

    public UUID getTransferId() { return transferId; }
    public void setTransferId(UUID v) { this.transferId = v; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID v) { this.tenantId = v; }
    public String getSubjectCpid() { return subjectCpid; }
    public void setSubjectCpid(String v) { this.subjectCpid = v; }
    public UUID getJourneyId() { return journeyId; }
    public void setJourneyId(UUID v) { this.journeyId = v; }
    public UUID getEncounterId() { return encounterId; }
    public void setEncounterId(UUID v) { this.encounterId = v; }
    public UUID getConsultationId() { return consultationId; }
    public void setConsultationId(UUID v) { this.consultationId = v; }
    public String getFromService() { return fromService; }
    public void setFromService(String v) { this.fromService = v; }
    public String getToService() { return toService; }
    public void setToService(String v) { this.toService = v; }
    public String getRequestedBy() { return requestedBy; }
    public void setRequestedBy(String v) { this.requestedBy = v; }
    public OffsetDateTime getRequestedAt() { return requestedAt; }
    public void setRequestedAt(OffsetDateTime v) { this.requestedAt = v; }
    public String getReason() { return reason; }
    public void setReason(String v) { this.reason = v; }
    public String getStatus() { return status; }
    public void setStatus(String v) { this.status = v; }
    public String getAcceptedBy() { return acceptedBy; }
    public void setAcceptedBy(String v) { this.acceptedBy = v; }
    public OffsetDateTime getAcceptedAt() { return acceptedAt; }
    public void setAcceptedAt(OffsetDateTime v) { this.acceptedAt = v; }
    public String getAcceptingRef() { return acceptingRef; }
    public void setAcceptingRef(String v) { this.acceptingRef = v; }
    public String getDeclinedBy() { return declinedBy; }
    public void setDeclinedBy(String v) { this.declinedBy = v; }
    public OffsetDateTime getDeclinedAt() { return declinedAt; }
    public void setDeclinedAt(OffsetDateTime v) { this.declinedAt = v; }
    public String getDeclineReason() { return declineReason; }
    public void setDeclineReason(String v) { this.declineReason = v; }
    public String getWithdrawnBy() { return withdrawnBy; }
    public void setWithdrawnBy(String v) { this.withdrawnBy = v; }
    public OffsetDateTime getWithdrawnAt() { return withdrawnAt; }
    public void setWithdrawnAt(OffsetDateTime v) { this.withdrawnAt = v; }
}
