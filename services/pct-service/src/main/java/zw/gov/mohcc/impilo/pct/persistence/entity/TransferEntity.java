package zw.gov.mohcc.impilo.pct.persistence.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Represents a patient transfer between wards or beds.
 * Supports both internal transfers within a facility and external transfers between facilities.
 */
@Entity
@Table(name = "pct_transfers")
public class TransferEntity {

    @Id
    @Column(name = "transfer_id", nullable = false)
    private UUID transferId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "journey_id", nullable = false)
    private String journeyId;

    @Column(name = "from_ward_id")
    private UUID fromWardId;

    @Column(name = "from_bed_id")
    private UUID fromBedId;

    @Column(name = "to_ward_id")
    private UUID toWardId;

    @Column(name = "to_bed_id")
    private UUID toBedId;

    @Column(name = "transfer_type", nullable = false)
    private String transferType = "INTERNAL";

    @Column(name = "status", nullable = false)
    private String status = "REQUESTED";

    @Column(name = "requested_by")
    private String requestedBy;

    @Column(name = "approved_by")
    private String approvedBy;

    @Column(name = "admission_id")
    private UUID admissionId;

    @Column(name = "completed_by")
    private String completedBy;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
    }

    // Getters and setters

    public UUID getId() { return transferId; }
    public void setId(UUID id) { this.transferId = id; }

    public UUID getTransferId() { return transferId; }
    public void setTransferId(UUID transferId) { this.transferId = transferId; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public String getJourneyId() { return journeyId; }
    public void setJourneyId(String journeyId) { this.journeyId = journeyId; }

    public UUID getFromWardId() { return fromWardId; }
    public void setFromWardId(UUID fromWardId) { this.fromWardId = fromWardId; }

    public UUID getFromBedId() { return fromBedId; }
    public void setFromBedId(UUID fromBedId) { this.fromBedId = fromBedId; }

    public UUID getToWardId() { return toWardId; }
    public void setToWardId(UUID toWardId) { this.toWardId = toWardId; }

    public UUID getToBedId() { return toBedId; }
    public void setToBedId(UUID toBedId) { this.toBedId = toBedId; }

    public String getTransferType() { return transferType; }
    public void setTransferType(String transferType) { this.transferType = transferType; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getRequestedBy() { return requestedBy; }
    public void setRequestedBy(String requestedBy) { this.requestedBy = requestedBy; }

    public String getApprovedBy() { return approvedBy; }
    public void setApprovedBy(String approvedBy) { this.approvedBy = approvedBy; }

    public OffsetDateTime getCreatedAt() { return createdAt; }

    public UUID getAdmissionId() { return admissionId; }
    public void setAdmissionId(UUID admissionId) { this.admissionId = admissionId; }

    public String getCompletedBy() { return completedBy; }
    public void setCompletedBy(String completedBy) { this.completedBy = completedBy; }

    public OffsetDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(OffsetDateTime completedAt) { this.completedAt = completedAt; }
}
