package zw.gov.mohcc.impilo.madi.persistence.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "blood_issues", schema = "madi")
public class BloodIssueEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "issue_id", nullable = false, unique = true)
    private UUID issueId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

@Column(name = "order_id")
    private UUID orderId;

@Column(name = "unit_id")
    private UUID unitId;

@Column(name = "reservation_id")
    private UUID reservationId;

@Column(name = "status")
    private String status;

@Column(name = "issued_by")
    private String issuedBy;

@Column(name = "facility_id")
    private UUID facilityId;

@Column(name = "jurisdiction")
    private String jurisdiction;

@Column(name = "issued_at")
    private OffsetDateTime issuedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (issueId == null) {
            issueId = UUID.randomUUID();
        }
        createdAt = OffsetDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public UUID getIssueId() { return issueId; }
    public void setIssueId(UUID issueId) { this.issueId = issueId; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public UUID getOrderId() { return orderId; }
    public void setOrderId(UUID orderId) { this.orderId = orderId; }

    public UUID getUnitId() { return unitId; }
    public void setUnitId(UUID unitId) { this.unitId = unitId; }

    public UUID getReservationId() { return reservationId; }
    public void setReservationId(UUID reservationId) { this.reservationId = reservationId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getIssuedBy() { return issuedBy; }
    public void setIssuedBy(String issuedBy) { this.issuedBy = issuedBy; }

    public UUID getFacilityId() { return facilityId; }
    public void setFacilityId(UUID facilityId) { this.facilityId = facilityId; }

    public String getJurisdiction() { return jurisdiction; }
    public void setJurisdiction(String jurisdiction) { this.jurisdiction = jurisdiction; }

    public OffsetDateTime getIssuedAt() { return issuedAt; }
    public void setIssuedAt(OffsetDateTime issuedAt) { this.issuedAt = issuedAt; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

}