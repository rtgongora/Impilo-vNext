package zw.gov.mohcc.impilo.madi.persistence.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "blood_dispatches", schema = "madi")
public class BloodDispatchEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "dispatch_id", nullable = false, unique = true)
    private UUID dispatchId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

@Column(name = "issue_id")
    private UUID issueId;

@Column(name = "destination_facility_id")
    private UUID destinationFacilityId;

@Column(name = "status")
    private String status;

@Column(name = "dispatched_by")
    private String dispatchedBy;

@Column(name = "facility_id")
    private UUID facilityId;

@Column(name = "jurisdiction")
    private String jurisdiction;

@Column(name = "dispatched_at")
    private OffsetDateTime dispatchedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (dispatchId == null) {
            dispatchId = UUID.randomUUID();
        }
        createdAt = OffsetDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public UUID getDispatchId() { return dispatchId; }
    public void setDispatchId(UUID dispatchId) { this.dispatchId = dispatchId; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public UUID getIssueId() { return issueId; }
    public void setIssueId(UUID issueId) { this.issueId = issueId; }

    public UUID getDestinationFacilityId() { return destinationFacilityId; }
    public void setDestinationFacilityId(UUID destinationFacilityId) { this.destinationFacilityId = destinationFacilityId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getDispatchedBy() { return dispatchedBy; }
    public void setDispatchedBy(String dispatchedBy) { this.dispatchedBy = dispatchedBy; }

    public UUID getFacilityId() { return facilityId; }
    public void setFacilityId(UUID facilityId) { this.facilityId = facilityId; }

    public String getJurisdiction() { return jurisdiction; }
    public void setJurisdiction(String jurisdiction) { this.jurisdiction = jurisdiction; }

    public OffsetDateTime getDispatchedAt() { return dispatchedAt; }
    public void setDispatchedAt(OffsetDateTime dispatchedAt) { this.dispatchedAt = dispatchedAt; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

}