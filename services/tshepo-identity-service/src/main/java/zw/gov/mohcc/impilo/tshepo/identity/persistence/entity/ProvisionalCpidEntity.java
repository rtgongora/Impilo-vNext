package zw.gov.mohcc.impilo.tshepo.identity.persistence.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * Tracks offline-issued provisional CPIDs (O-CPIDs).
 *
 * <p>When a facility operates offline, it can generate O-CPIDs for patients.
 * Once connectivity is restored, each O-CPID is reconciled to a canonical CPID
 * via the reconciliation workflow. The O-CPID is a random UUID (not deterministic)
 * that is later mapped to the real CPID once the patient's Health ID is resolved.</p>
 */
@Entity
@Table(name = "provisional_cpid", schema = "tshepo_identity")
public class ProvisionalCpidEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "o_cpid", nullable = false, unique = true)
    private UUID originCpid;

    @Column(name = "facility_id", nullable = false)
    private UUID facilityId;

    @Column(name = "device_fingerprint", nullable = false, length = 255)
    private String deviceFingerprint;

    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt;

    @Column(name = "reconciled_at")
    private Instant reconciledAt;

    @Column(name = "canonical_cpid")
    private UUID canonicalCpid;

    @Column(name = "status", nullable = false, length = 16)
    private String status = "PROVISIONAL";

    @PrePersist
    protected void onCreate() {
        this.issuedAt = Instant.now();
    }

    // --- Getters and Setters ---

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public UUID getOriginCpid() { return originCpid; }
    public void setOriginCpid(UUID originCpid) { this.originCpid = originCpid; }

    public UUID getFacilityId() { return facilityId; }
    public void setFacilityId(UUID facilityId) { this.facilityId = facilityId; }

    public String getDeviceFingerprint() { return deviceFingerprint; }
    public void setDeviceFingerprint(String deviceFingerprint) { this.deviceFingerprint = deviceFingerprint; }

    public Instant getIssuedAt() { return issuedAt; }
    public void setIssuedAt(Instant issuedAt) { this.issuedAt = issuedAt; }

    public Instant getReconciledAt() { return reconciledAt; }
    public void setReconciledAt(Instant reconciledAt) { this.reconciledAt = reconciledAt; }

    public UUID getCanonicalCpid() { return canonicalCpid; }
    public void setCanonicalCpid(UUID canonicalCpid) { this.canonicalCpid = canonicalCpid; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
