package zw.gov.mohcc.impilo.tshepo.keys.persistence.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity for trusted CA certificates used in mTLS verification.
 *
 * <p>Stores X.509 certificate metadata and the full PEM-encoded certificate.
 * Status can be TRUSTED or REVOKED.</p>
 */
@Entity
@Table(name = "certificate_trust", schema = "tshepo_keys")
public class CertificateTrustEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "subject_dn", nullable = false, columnDefinition = "TEXT")
    private String subjectDn;

    @Column(name = "issuer_dn", nullable = false, columnDefinition = "TEXT")
    private String issuerDn;

    @Column(name = "serial_number", nullable = false, length = 128)
    private String serialNumber;

    @Column(name = "fingerprint_sha256", nullable = false, unique = true, length = 64)
    private String fingerprintSha256;

    @Column(name = "certificate_pem", nullable = false, columnDefinition = "TEXT")
    private String certificatePem;

    @Column(name = "status", nullable = false, length = 16)
    private String status = "TRUSTED";

    @Column(name = "imported_at", nullable = false)
    private Instant importedAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @PrePersist
    protected void onCreate() {
        if (importedAt == null) {
            importedAt = Instant.now();
        }
    }

    // --- Getters and Setters ---

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public String getSubjectDn() { return subjectDn; }
    public void setSubjectDn(String subjectDn) { this.subjectDn = subjectDn; }

    public String getIssuerDn() { return issuerDn; }
    public void setIssuerDn(String issuerDn) { this.issuerDn = issuerDn; }

    public String getSerialNumber() { return serialNumber; }
    public void setSerialNumber(String serialNumber) { this.serialNumber = serialNumber; }

    public String getFingerprintSha256() { return fingerprintSha256; }
    public void setFingerprintSha256(String fingerprintSha256) { this.fingerprintSha256 = fingerprintSha256; }

    public String getCertificatePem() { return certificatePem; }
    public void setCertificatePem(String certificatePem) { this.certificatePem = certificatePem; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Instant getImportedAt() { return importedAt; }
    public void setImportedAt(Instant importedAt) { this.importedAt = importedAt; }

    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
}
