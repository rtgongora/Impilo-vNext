package zw.gov.mohcc.impilo.vito.persistence.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "provisional_id", schema = "vito")
public class ProvisionalIdEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "provisional_ref", nullable = false, unique = true)
    private String provisionalRef;

    @Column(name = "facility_id", nullable = false)
    private UUID facilityId;

    @Column(name = "issued_by", nullable = false)
    private String issuedBy;

    @Column(name = "status", nullable = false)
    private String status = "PENDING";

    @Column(name = "reconciled_to")
    private UUID reconciledTo;

    @Column(name = "issued_at", nullable = false, updatable = false)
    private OffsetDateTime issuedAt;

    @Column(name = "reconciled_at")
    private OffsetDateTime reconciledAt;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    @PrePersist
    protected void onCreate() {
        issuedAt = OffsetDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public String getProvisionalRef() { return provisionalRef; }
    public void setProvisionalRef(String provisionalRef) { this.provisionalRef = provisionalRef; }
    public UUID getFacilityId() { return facilityId; }
    public void setFacilityId(UUID facilityId) { this.facilityId = facilityId; }
    public String getIssuedBy() { return issuedBy; }
    public void setIssuedBy(String issuedBy) { this.issuedBy = issuedBy; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public UUID getReconciledTo() { return reconciledTo; }
    public void setReconciledTo(UUID reconciledTo) { this.reconciledTo = reconciledTo; }
    public OffsetDateTime getIssuedAt() { return issuedAt; }
    public OffsetDateTime getReconciledAt() { return reconciledAt; }
    public void setReconciledAt(OffsetDateTime reconciledAt) { this.reconciledAt = reconciledAt; }
    public OffsetDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(OffsetDateTime expiresAt) { this.expiresAt = expiresAt; }
}
