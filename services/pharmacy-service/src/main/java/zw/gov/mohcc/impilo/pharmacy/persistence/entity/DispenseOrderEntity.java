package zw.gov.mohcc.impilo.pharmacy.persistence.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;
import zw.gov.mohcc.impilo.pharmacy.domain.DispenseStatus;
import zw.gov.mohcc.impilo.pharmacy.domain.OrderPriority;

/**
 * Represents a pharmacy dispense order linked to an OROS order.
 * Tracks the full lifecycle from acceptance through dispensing and pickup.
 */
@Entity
@Table(name = "rx_dispense_orders")
public class DispenseOrderEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "facility_id", nullable = false)
    private UUID facilityId;

    @Column(name = "workspace_id")
    private UUID workspaceId;

    @Column(name = "store_id")
    private String storeId;

    @Column(name = "patient_cpid", nullable = false)
    private String patientCpid;

    @Column(name = "oros_order_id")
    private String orosOrderId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private DispenseStatus status = DispenseStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(name = "priority", nullable = false)
    private OrderPriority priority = OrderPriority.ROUTINE;

    @Column(name = "prescriber_id")
    private String prescriberId;

    @Column(name = "prescriber_notes", columnDefinition = "TEXT")
    private String prescriberNotes;

    @Column(name = "clinical_notes", columnDefinition = "TEXT")
    private String clinicalNotes;

    @Column(name = "accepted_by")
    private String acceptedBy;

    @Column(name = "accepted_at")
    private OffsetDateTime acceptedAt;

    @Column(name = "completed_by")
    private String completedBy;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    // ── OF-B12 prescription-claim linkage (§13.3.1) ────────────────────
    /** The OROS prescription claim this episode traces to (medication profile). */
    @Column(name = "prescription_claim_id")
    private UUID prescriptionClaimId;

    /** Set when OROS confirmed the episode-ref bind on the claim. */
    @Column(name = "claim_bound_at")
    private OffsetDateTime claimBoundAt;

    /** Set when the claim was released (pre-dispense cancellation compensation). */
    @Column(name = "claim_released_at")
    private OffsetDateTime claimReleasedAt;

    /** Set once a §13.3.1 anomaly event has been emitted for this row (emit-once marker). */
    @Column(name = "claim_anomaly_flagged_at")
    private OffsetDateTime claimAnomalyFlaggedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
        updatedAt = OffsetDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    // Getters and setters

    public UUID getOrderId() { return orderId; }
    public void setOrderId(UUID orderId) { this.orderId = orderId; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public UUID getFacilityId() { return facilityId; }
    public void setFacilityId(UUID facilityId) { this.facilityId = facilityId; }

    public UUID getWorkspaceId() { return workspaceId; }
    public void setWorkspaceId(UUID workspaceId) { this.workspaceId = workspaceId; }

    public String getStoreId() { return storeId; }
    public void setStoreId(String storeId) { this.storeId = storeId; }

    public String getPatientCpid() { return patientCpid; }
    public void setPatientCpid(String patientCpid) { this.patientCpid = patientCpid; }

    public String getOrosOrderId() { return orosOrderId; }
    public void setOrosOrderId(String orosOrderId) { this.orosOrderId = orosOrderId; }

    public DispenseStatus getStatus() { return status; }
    public void setStatus(DispenseStatus status) { this.status = status; }

    public OrderPriority getPriority() { return priority; }
    public void setPriority(OrderPriority priority) { this.priority = priority; }

    public String getPrescriberId() { return prescriberId; }
    public void setPrescriberId(String prescriberId) { this.prescriberId = prescriberId; }

    public String getPrescriberNotes() { return prescriberNotes; }
    public void setPrescriberNotes(String prescriberNotes) { this.prescriberNotes = prescriberNotes; }

    public String getClinicalNotes() { return clinicalNotes; }
    public void setClinicalNotes(String clinicalNotes) { this.clinicalNotes = clinicalNotes; }

    public String getAcceptedBy() { return acceptedBy; }
    public void setAcceptedBy(String acceptedBy) { this.acceptedBy = acceptedBy; }

    public OffsetDateTime getAcceptedAt() { return acceptedAt; }
    public void setAcceptedAt(OffsetDateTime acceptedAt) { this.acceptedAt = acceptedAt; }

    public String getCompletedBy() { return completedBy; }
    public void setCompletedBy(String completedBy) { this.completedBy = completedBy; }

    public OffsetDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(OffsetDateTime completedAt) { this.completedAt = completedAt; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }

    public UUID getPrescriptionClaimId() { return prescriptionClaimId; }
    public void setPrescriptionClaimId(UUID prescriptionClaimId) { this.prescriptionClaimId = prescriptionClaimId; }

    public OffsetDateTime getClaimBoundAt() { return claimBoundAt; }
    public void setClaimBoundAt(OffsetDateTime claimBoundAt) { this.claimBoundAt = claimBoundAt; }

    public OffsetDateTime getClaimReleasedAt() { return claimReleasedAt; }
    public void setClaimReleasedAt(OffsetDateTime claimReleasedAt) { this.claimReleasedAt = claimReleasedAt; }

    public OffsetDateTime getClaimAnomalyFlaggedAt() { return claimAnomalyFlaggedAt; }
    public void setClaimAnomalyFlaggedAt(OffsetDateTime claimAnomalyFlaggedAt) { this.claimAnomalyFlaggedAt = claimAnomalyFlaggedAt; }
}
