package zw.gov.mohcc.impilo.madi.persistence.entity;

import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "blood_emergency_redistributions", schema = "madi")
public class BloodEmergencyRedistributionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "redistribution_id", nullable = false, unique = true)
    private UUID redistributionId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "source_facility_id", nullable = false)
    private UUID sourceFacilityId;

    @Column(name = "target_facility_id", nullable = false)
    private UUID targetFacilityId;

    @Column(name = "blood_group", nullable = false)
    private String bloodGroup;

    @Column(name = "component_type", nullable = false)
    private String componentType;

    @Column(name = "units_requested", nullable = false)
    private Integer unitsRequested;

    @Column(name = "units_allocated", nullable = false)
    private Integer unitsAllocated;

    @Column(name = "priority", nullable = false)
    private String priority;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "reason", columnDefinition = "TEXT")
    private String reason;

    @Column(name = "requested_by")
    private String requestedBy;

    @Column(name = "approved_by")
    private String approvedBy;

    @Column(name = "requested_at", nullable = false)
    private OffsetDateTime requestedAt;

    @Column(name = "approved_at")
    private OffsetDateTime approvedAt;

    @Column(name = "fulfilled_at")
    private OffsetDateTime fulfilledAt;

    @Column(name = "nhume_delivery_id")
    private UUID nhumeDeliveryId;

    @Column(name = "handoff_status")
    private String handoffStatus;

    @Column(name = "handoff_at")
    private OffsetDateTime handoffAt;

    @Column(name = "handoff_error", columnDefinition = "TEXT")
    private String handoffError;

    @Column(name = "facility_id")
    private UUID facilityId;

    @Column(name = "jurisdiction")
    private String jurisdiction;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (redistributionId == null) {
            redistributionId = UUID.randomUUID();
        }
        if (jurisdiction == null) {
            jurisdiction = "ZW";
        }
        if (priority == null) {
            priority = "EMERGENCY";
        }
        if (status == null) {
            status = "REQUESTED";
        }
        if (unitsAllocated == null) {
            unitsAllocated = 0;
        }
        if (handoffStatus == null) {
            handoffStatus = "PENDING";
        }
        if (requestedAt == null) {
            requestedAt = OffsetDateTime.now();
        }
        createdAt = OffsetDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public UUID getRedistributionId() { return redistributionId; }
    public void setRedistributionId(UUID redistributionId) { this.redistributionId = redistributionId; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public UUID getSourceFacilityId() { return sourceFacilityId; }
    public void setSourceFacilityId(UUID sourceFacilityId) { this.sourceFacilityId = sourceFacilityId; }

    public UUID getTargetFacilityId() { return targetFacilityId; }
    public void setTargetFacilityId(UUID targetFacilityId) { this.targetFacilityId = targetFacilityId; }

    public String getBloodGroup() { return bloodGroup; }
    public void setBloodGroup(String bloodGroup) { this.bloodGroup = bloodGroup; }

    public String getComponentType() { return componentType; }
    public void setComponentType(String componentType) { this.componentType = componentType; }

    public Integer getUnitsRequested() { return unitsRequested; }
    public void setUnitsRequested(Integer unitsRequested) { this.unitsRequested = unitsRequested; }

    public Integer getUnitsAllocated() { return unitsAllocated; }
    public void setUnitsAllocated(Integer unitsAllocated) { this.unitsAllocated = unitsAllocated; }

    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public String getRequestedBy() { return requestedBy; }
    public void setRequestedBy(String requestedBy) { this.requestedBy = requestedBy; }

    public String getApprovedBy() { return approvedBy; }
    public void setApprovedBy(String approvedBy) { this.approvedBy = approvedBy; }

    public OffsetDateTime getRequestedAt() { return requestedAt; }
    public void setRequestedAt(OffsetDateTime requestedAt) { this.requestedAt = requestedAt; }

    public OffsetDateTime getApprovedAt() { return approvedAt; }
    public void setApprovedAt(OffsetDateTime approvedAt) { this.approvedAt = approvedAt; }

    public OffsetDateTime getFulfilledAt() { return fulfilledAt; }
    public void setFulfilledAt(OffsetDateTime fulfilledAt) { this.fulfilledAt = fulfilledAt; }

    public UUID getNhumeDeliveryId() { return nhumeDeliveryId; }
    public void setNhumeDeliveryId(UUID nhumeDeliveryId) { this.nhumeDeliveryId = nhumeDeliveryId; }

    public String getHandoffStatus() { return handoffStatus; }
    public void setHandoffStatus(String handoffStatus) { this.handoffStatus = handoffStatus; }

    public OffsetDateTime getHandoffAt() { return handoffAt; }
    public void setHandoffAt(OffsetDateTime handoffAt) { this.handoffAt = handoffAt; }

    public String getHandoffError() { return handoffError; }
    public void setHandoffError(String handoffError) { this.handoffError = handoffError; }

    public UUID getFacilityId() { return facilityId; }
    public void setFacilityId(UUID facilityId) { this.facilityId = facilityId; }

    public String getJurisdiction() { return jurisdiction; }
    public void setJurisdiction(String jurisdiction) { this.jurisdiction = jurisdiction; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
