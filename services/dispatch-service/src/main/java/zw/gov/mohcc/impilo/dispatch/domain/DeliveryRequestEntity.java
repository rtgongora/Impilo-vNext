package zw.gov.mohcc.impilo.dispatch.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "dsp_delivery_requests")
public class DeliveryRequestEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "external_ref")
    private String externalRef;

    @Column(name = "requester_actor_id")
    private String requesterActorId;

    @Column(name = "requester_actor_type")
    private String requesterActorType;

    @Column(name = "pickup_location", nullable = false)
    private String pickupLocation;

    @Column(name = "dropoff_location", nullable = false)
    private String dropoffLocation;

    @Column(name = "priority", nullable = false)
    private String priority = "NORMAL";

    @Column(name = "status", nullable = false)
    private String status = "REQUESTED";

    @Column(name = "payload_json", columnDefinition = "TEXT")
    private String payloadJson;

    @Column(name = "zone_id")
    private UUID zoneId;

    @Column(name = "policy_id")
    private UUID policyId;

    @Column(name = "integration_provider_id")
    private UUID integrationProviderId;

    @Column(name = "requested_window_start")
    private OffsetDateTime requestedWindowStart;

    @Column(name = "requested_window_end")
    private OffsetDateTime requestedWindowEnd;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected DeliveryRequestEntity() {}

    public DeliveryRequestEntity(UUID id, UUID tenantId, String pickupLocation, String dropoffLocation) {
        this.id = id;
        this.tenantId = tenantId;
        this.pickupLocation = pickupLocation;
        this.dropoffLocation = dropoffLocation;
        OffsetDateTime now = OffsetDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public String getExternalRef() { return externalRef; }
    public void setExternalRef(String externalRef) { this.externalRef = externalRef; }
    public String getRequesterActorId() { return requesterActorId; }
    public void setRequesterActorId(String requesterActorId) { this.requesterActorId = requesterActorId; }
    public String getRequesterActorType() { return requesterActorType; }
    public void setRequesterActorType(String requesterActorType) { this.requesterActorType = requesterActorType; }
    public String getPickupLocation() { return pickupLocation; }
    public void setPickupLocation(String pickupLocation) { this.pickupLocation = pickupLocation; }
    public String getDropoffLocation() { return dropoffLocation; }
    public void setDropoffLocation(String dropoffLocation) { this.dropoffLocation = dropoffLocation; }
    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getPayloadJson() { return payloadJson; }
    public void setPayloadJson(String payloadJson) { this.payloadJson = payloadJson; }
    public UUID getZoneId() { return zoneId; }
    public void setZoneId(UUID zoneId) { this.zoneId = zoneId; }
    public UUID getPolicyId() { return policyId; }
    public void setPolicyId(UUID policyId) { this.policyId = policyId; }
    public UUID getIntegrationProviderId() { return integrationProviderId; }
    public void setIntegrationProviderId(UUID integrationProviderId) { this.integrationProviderId = integrationProviderId; }
    public OffsetDateTime getRequestedWindowStart() { return requestedWindowStart; }
    public void setRequestedWindowStart(OffsetDateTime requestedWindowStart) { this.requestedWindowStart = requestedWindowStart; }
    public OffsetDateTime getRequestedWindowEnd() { return requestedWindowEnd; }
    public void setRequestedWindowEnd(OffsetDateTime requestedWindowEnd) { this.requestedWindowEnd = requestedWindowEnd; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
