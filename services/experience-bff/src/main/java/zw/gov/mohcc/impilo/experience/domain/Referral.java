package zw.gov.mohcc.impilo.experience.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.Set;
import java.util.UUID;

/**
 * Referral domain entity with state transition guards.
 *
 * <p>State machine:</p>
 * <pre>
 *   PENDING → ACCEPTED → RESPONDED → COMPLETED
 *   PENDING → RESPONDED → COMPLETED
 *   PENDING → COMPLETED (direct close)
 *   Any → CANCELLED (terminal)
 * </pre>
 *
 * <p>Transition guards prevent invalid moves such as accepting an already
 * accepted referral, responding to a completed referral, or completing
 * a cancelled referral.</p>
 */
@Entity
@Table(name = "referrals")
public class Referral {

    private static final Set<String> ACCEPTABLE_FROM = Set.of("PENDING");
    private static final Set<String> RESPONDABLE_FROM = Set.of("PENDING", "ACCEPTED");
    private static final Set<String> COMPLETABLE_FROM = Set.of("PENDING", "ACCEPTED", "RESPONDED");

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(name = "patient_id", nullable = false)
    private UUID patientId;

    @Column(name = "encounter_id")
    private UUID encounterId;

    @Column(name = "referral_type")
    private String referralType;

    private String specialty;

    @Column(name = "referred_to")
    private String referredTo;

    @Column(name = "referred_to_facility")
    private String referredToFacility;

    private String reason;

    private String urgency;

    private String status;

    @Column(name = "clinical_summary")
    private String clinicalSummary;

    @Column(name = "referred_by")
    private String referredBy;

    @Column(name = "referred_by_name")
    private String referredByName;

    @Column(name = "scheduled_at")
    private OffsetDateTime scheduledAt;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    private String outcome;

    // V9 columns — cross-facility tracking
    @Column(name = "receiving_facility_id")
    private UUID receivingFacilityId;

    @Column(name = "receiving_facility_name")
    private String receivingFacilityName;

    @Column(name = "response_notes")
    private String responseNotes;

    @Column(name = "responded_at")
    private OffsetDateTime respondedAt;

    @Column(name = "accepted_at")
    private OffsetDateTime acceptedAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected Referral() {}

    // ── State transition methods with guards ──────────────────────

    /**
     * Accept this referral at a receiving facility.
     *
     * @throws IllegalStateException if referral is not in PENDING status
     */
    public void accept(UUID receivingFacilityId, String receivingFacilityName) {
        guardTransition("accept", ACCEPTABLE_FROM);
        this.status = "ACCEPTED";
        this.receivingFacilityId = receivingFacilityId;
        this.receivingFacilityName = receivingFacilityName;
        this.acceptedAt = OffsetDateTime.now();
        this.updatedAt = OffsetDateTime.now();
    }

    /**
     * Record a response from the receiving facility/specialist.
     *
     * @throws IllegalStateException if referral is not in PENDING or ACCEPTED status
     */
    public void respond(String responseNotes, String outcome) {
        guardTransition("respond", RESPONDABLE_FROM);
        this.status = "RESPONDED";
        this.responseNotes = responseNotes;
        if (outcome != null) {
            this.outcome = outcome;
        }
        this.respondedAt = OffsetDateTime.now();
        this.updatedAt = OffsetDateTime.now();
    }

    /**
     * Complete this referral with an optional outcome.
     *
     * @throws IllegalStateException if referral is already COMPLETED or CANCELLED
     */
    public void complete(String outcome) {
        guardTransition("complete", COMPLETABLE_FROM);
        this.status = "COMPLETED";
        this.completedAt = OffsetDateTime.now();
        if (outcome != null) {
            this.outcome = outcome;
        }
        this.updatedAt = OffsetDateTime.now();
    }

    /**
     * Check if a given transition is valid from the current status.
     */
    public boolean canTransitionTo(String action) {
        return switch (action) {
            case "accept" -> ACCEPTABLE_FROM.contains(this.status);
            case "respond" -> RESPONDABLE_FROM.contains(this.status);
            case "complete" -> COMPLETABLE_FROM.contains(this.status);
            default -> false;
        };
    }

    private void guardTransition(String action, Set<String> allowedFrom) {
        if (!allowedFrom.contains(this.status)) {
            throw new IllegalStateException(
                    "Cannot " + action + " referral " + id + ": current status is " + status
                    + ", allowed from " + allowedFrom);
        }
    }

    // ── Getters ──────────────────────────────────────────────────

    public UUID getId() { return id; }
    public String getTenantId() { return tenantId; }
    public UUID getPatientId() { return patientId; }
    public UUID getEncounterId() { return encounterId; }
    public String getReferralType() { return referralType; }
    public String getSpecialty() { return specialty; }
    public String getReferredTo() { return referredTo; }
    public String getReferredToFacility() { return referredToFacility; }
    public String getReason() { return reason; }
    public String getUrgency() { return urgency; }
    public String getStatus() { return status; }
    public String getClinicalSummary() { return clinicalSummary; }
    public String getReferredBy() { return referredBy; }
    public String getReferredByName() { return referredByName; }
    public OffsetDateTime getScheduledAt() { return scheduledAt; }
    public OffsetDateTime getCompletedAt() { return completedAt; }
    public String getOutcome() { return outcome; }
    public UUID getReceivingFacilityId() { return receivingFacilityId; }
    public String getReceivingFacilityName() { return receivingFacilityName; }
    public String getResponseNotes() { return responseNotes; }
    public OffsetDateTime getRespondedAt() { return respondedAt; }
    public OffsetDateTime getAcceptedAt() { return acceptedAt; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
