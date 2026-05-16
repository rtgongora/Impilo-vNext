package zw.gov.mohcc.impilo.dispatch.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "dsp_delivery_assignments")
public class DeliveryAssignmentEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "delivery_id", nullable = false)
    private UUID deliveryId;

    @Column(name = "fleet_asset_id")
    private UUID fleetAssetId;

    @Column(name = "courier_profile_id")
    private UUID courierProfileId;

    @Column(name = "assignment_status", nullable = false)
    private String assignmentStatus = "ASSIGNED";

    @Column(name = "notes_json", columnDefinition = "TEXT")
    private String notesJson;

    @Column(name = "assigned_at", nullable = false)
    private OffsetDateTime assignedAt;

    @Column(name = "accepted_at")
    private OffsetDateTime acceptedAt;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected DeliveryAssignmentEntity() {}

    public DeliveryAssignmentEntity(UUID id, UUID deliveryId) {
        this.id = id;
        this.deliveryId = deliveryId;
        OffsetDateTime now = OffsetDateTime.now();
        this.assignedAt = now;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public UUID getId() { return id; }
    public UUID getDeliveryId() { return deliveryId; }
    public UUID getFleetAssetId() { return fleetAssetId; }
    public void setFleetAssetId(UUID fleetAssetId) { this.fleetAssetId = fleetAssetId; }
    public UUID getCourierProfileId() { return courierProfileId; }
    public void setCourierProfileId(UUID courierProfileId) { this.courierProfileId = courierProfileId; }
    public String getAssignmentStatus() { return assignmentStatus; }
    public void setAssignmentStatus(String assignmentStatus) { this.assignmentStatus = assignmentStatus; }
    public String getNotesJson() { return notesJson; }
    public void setNotesJson(String notesJson) { this.notesJson = notesJson; }
    public OffsetDateTime getAssignedAt() { return assignedAt; }
    public OffsetDateTime getAcceptedAt() { return acceptedAt; }
    public void setAcceptedAt(OffsetDateTime acceptedAt) { this.acceptedAt = acceptedAt; }
    public OffsetDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(OffsetDateTime completedAt) { this.completedAt = completedAt; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
