package zw.gov.mohcc.impilo.nhume.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "nhume_delivery_assignments")
public class DeliveryAssignmentEntity {

    @Id
    @Column(name = "assignment_id", nullable = false)
    private UUID assignmentId;

    @Column(name = "delivery_id", nullable = false)
    private UUID deliveryId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "courier_id")
    private UUID courierId;

    @Column(name = "asset_id")
    private UUID assetId;

    @Column(name = "integration_provider", length = 128)
    private String integrationProvider;

    @Column(name = "integration_ref", length = 255)
    private String integrationRef;

    @Column(name = "mode_category", nullable = false, length = 48)
    private String modeCategory;

    @Column(name = "assignment_kind", nullable = false, length = 32)
    private String assignmentKind = "MANUAL";

    @Column(name = "assigned_by", length = 255)
    private String assignedBy;

    @Column(name = "assigned_at", nullable = false)
    private OffsetDateTime assignedAt = OffsetDateTime.now();

    @Column(name = "accepted_at")
    private OffsetDateTime acceptedAt;

    @Column(name = "declined_at")
    private OffsetDateTime declinedAt;

    @Column(name = "decline_reason", length = 512)
    private String declineReason;

    @Column(name = "superseded_at")
    private OffsetDateTime supersededAt;

    @Column(name = "superseded_by")
    private UUID supersededBy;

    @Column(name = "status", nullable = false, length = 32)
    private String status = "ASSIGNED";

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    public DeliveryAssignmentEntity() {}

    public UUID getAssignmentId() { return assignmentId; }
    public void setAssignmentId(UUID v) { this.assignmentId = v; }
    public UUID getDeliveryId() { return deliveryId; }
    public void setDeliveryId(UUID v) { this.deliveryId = v; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID v) { this.tenantId = v; }
    public UUID getCourierId() { return courierId; }
    public void setCourierId(UUID v) { this.courierId = v; }
    public UUID getAssetId() { return assetId; }
    public void setAssetId(UUID v) { this.assetId = v; }
    public String getIntegrationProvider() { return integrationProvider; }
    public void setIntegrationProvider(String v) { this.integrationProvider = v; }
    public String getIntegrationRef() { return integrationRef; }
    public void setIntegrationRef(String v) { this.integrationRef = v; }
    public String getModeCategory() { return modeCategory; }
    public void setModeCategory(String v) { this.modeCategory = v; }
    public String getAssignmentKind() { return assignmentKind; }
    public void setAssignmentKind(String v) { this.assignmentKind = v; }
    public String getAssignedBy() { return assignedBy; }
    public void setAssignedBy(String v) { this.assignedBy = v; }
    public OffsetDateTime getAssignedAt() { return assignedAt; }
    public void setAssignedAt(OffsetDateTime v) { this.assignedAt = v; }
    public OffsetDateTime getAcceptedAt() { return acceptedAt; }
    public void setAcceptedAt(OffsetDateTime v) { this.acceptedAt = v; }
    public OffsetDateTime getDeclinedAt() { return declinedAt; }
    public void setDeclinedAt(OffsetDateTime v) { this.declinedAt = v; }
    public String getDeclineReason() { return declineReason; }
    public void setDeclineReason(String v) { this.declineReason = v; }
    public OffsetDateTime getSupersededAt() { return supersededAt; }
    public void setSupersededAt(OffsetDateTime v) { this.supersededAt = v; }
    public UUID getSupersededBy() { return supersededBy; }
    public void setSupersededBy(UUID v) { this.supersededBy = v; }
    public String getStatus() { return status; }
    public void setStatus(String v) { this.status = v; }
    public String getNotes() { return notes; }
    public void setNotes(String v) { this.notes = v; }
}
