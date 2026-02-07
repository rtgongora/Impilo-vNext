package zw.gov.mohcc.impilo.inventory.persistence.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;
import zw.gov.mohcc.impilo.inventory.domain.HandoverStatus;

/**
 * Represents a stock handover between two actors (e.g. shift change, custody transfer).
 * Both outgoing and incoming parties must sign to complete the handover.
 */
@Entity
@Table(name = "inv_handovers")
public class HandoverEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "handover_id", nullable = false)
    private UUID handoverId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "facility_id", nullable = false)
    private UUID facilityId;

    @Column(name = "store_id", nullable = false)
    private UUID storeId;

    @Column(name = "from_actor", nullable = false)
    private String fromActor;

    @Column(name = "to_actor", nullable = false)
    private String toActor;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private HandoverStatus status = HandoverStatus.INITIATED;

    @Column(name = "outgoing_signed_at")
    private OffsetDateTime outgoingSignedAt;

    @Column(name = "incoming_signed_at")
    private OffsetDateTime incomingSignedAt;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
    }

    // Getters and setters

    public UUID getHandoverId() { return handoverId; }
    public void setHandoverId(UUID handoverId) { this.handoverId = handoverId; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public UUID getFacilityId() { return facilityId; }
    public void setFacilityId(UUID facilityId) { this.facilityId = facilityId; }

    public UUID getStoreId() { return storeId; }
    public void setStoreId(UUID storeId) { this.storeId = storeId; }

    public String getFromActor() { return fromActor; }
    public void setFromActor(String fromActor) { this.fromActor = fromActor; }

    public String getToActor() { return toActor; }
    public void setToActor(String toActor) { this.toActor = toActor; }

    public HandoverStatus getStatus() { return status; }
    public void setStatus(HandoverStatus status) { this.status = status; }

    public OffsetDateTime getOutgoingSignedAt() { return outgoingSignedAt; }
    public void setOutgoingSignedAt(OffsetDateTime outgoingSignedAt) { this.outgoingSignedAt = outgoingSignedAt; }

    public OffsetDateTime getIncomingSignedAt() { return incomingSignedAt; }
    public void setIncomingSignedAt(OffsetDateTime incomingSignedAt) { this.incomingSignedAt = incomingSignedAt; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
