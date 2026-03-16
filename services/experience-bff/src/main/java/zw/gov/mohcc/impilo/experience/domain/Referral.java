package zw.gov.mohcc.impilo.experience.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "referrals")
public class Referral {

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

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected Referral() {}

    public void complete(String outcome) {
        this.status = "COMPLETED";
        this.completedAt = OffsetDateTime.now();
        this.outcome = outcome;
    }

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
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
