package zw.gov.mohcc.impilo.inpatient.persistence.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * JPA entity mapping to the {@code inpatient.transfer} table.
 */
@Entity
@Table(name = "transfer", schema = "inpatient")
public class TransferEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "admission_ref", nullable = false)
    private UUID admissionRef;

    @Column(name = "from_ward_id")
    private UUID fromWardId;

    @Column(name = "to_ward_id", nullable = false)
    private UUID toWardId;

    @Column(name = "to_bed_id")
    private UUID toBedId;

    @Column(name = "transferred_at")
    private OffsetDateTime transferredAt;

    @Column(name = "status", nullable = false)
    private String status = "COMPLETED";

    @Column(name = "reason")
    private String reason;

    @Column(name = "clinical_notes")
    private String clinicalNotes;

    @Column(name = "requested_by")
    private String requestedBy;

    @Column(name = "accepted_by")
    private String acceptedBy;

    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
        if (transferredAt == null) {
            transferredAt = OffsetDateTime.now();
        }
    }

    // Getters and setters

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public UUID getAdmissionRef() { return admissionRef; }
    public void setAdmissionRef(UUID admissionRef) { this.admissionRef = admissionRef; }

    public UUID getFromWardId() { return fromWardId; }
    public void setFromWardId(UUID fromWardId) { this.fromWardId = fromWardId; }

    public UUID getToWardId() { return toWardId; }
    public void setToWardId(UUID toWardId) { this.toWardId = toWardId; }

    public UUID getToBedId() { return toBedId; }
    public void setToBedId(UUID toBedId) { this.toBedId = toBedId; }

    public OffsetDateTime getTransferredAt() { return transferredAt; }
    public void setTransferredAt(OffsetDateTime transferredAt) { this.transferredAt = transferredAt; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public String getClinicalNotes() { return clinicalNotes; }
    public void setClinicalNotes(String clinicalNotes) { this.clinicalNotes = clinicalNotes; }

    public String getRequestedBy() { return requestedBy; }
    public void setRequestedBy(String requestedBy) { this.requestedBy = requestedBy; }

    public String getAcceptedBy() { return acceptedBy; }
    public void setAcceptedBy(String acceptedBy) { this.acceptedBy = acceptedBy; }
}
