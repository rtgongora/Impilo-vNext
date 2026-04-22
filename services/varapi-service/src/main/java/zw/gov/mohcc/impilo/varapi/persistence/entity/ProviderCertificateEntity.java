package zw.gov.mohcc.impilo.varapi.persistence.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Provider certificate entity tracking issued certificates.
 */
@Entity
@Table(name = "provider_certificates", schema = "varapi")
public class ProviderCertificateEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "provider_id", nullable = false)
    private ProviderEntity provider;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "certificate_type", nullable = false, length = 50)
    private String certificateType;

    @Column(name = "issue_date", nullable = false)
    private LocalDate issueDate;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @Column(name = "status", nullable = false, length = 20)
    private String status = "ACTIVE";

    @Column(name = "issued_under_authority", length = 255)
    private String issuedUnderAuthority;

    @Column(name = "digital_artifact_ref", length = 500)
    private String digitalArtifactRef;

    @Column(name = "supersedes_certificate_id")
    private Long supersedesCertificateId;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "certificate_number", length = 50)
    private String certificateNumber;

    @Column(name = "renewal_reference", length = 100)
    private String renewalReference;

    @Column(name = "suspension_date")
    private LocalDate suspensionDate;

    @Column(name = "revocation_date")
    private LocalDate revocationDate;

    @Column(name = "version")
    private Integer version = 1;

    @Column(name = "created_by", length = 255)
    private String createdBy;

    @Column(name = "updated_by", length = 255)
    private String updatedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    public boolean isActive() {
        return "ACTIVE".equals(status);
    }

    public boolean isExpired() {
        return expiryDate != null && LocalDate.now().isAfter(expiryDate);
    }

    // Getters and setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public ProviderEntity getProvider() { return provider; }
    public void setProvider(ProviderEntity provider) { this.provider = provider; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public String getCertificateType() { return certificateType; }
    public void setCertificateType(String certificateType) { this.certificateType = certificateType; }

    public LocalDate getIssueDate() { return issueDate; }
    public void setIssueDate(LocalDate issueDate) { this.issueDate = issueDate; }

    public LocalDate getExpiryDate() { return expiryDate; }
    public void setExpiryDate(LocalDate expiryDate) { this.expiryDate = expiryDate; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getIssuedUnderAuthority() { return issuedUnderAuthority; }
    public void setIssuedUnderAuthority(String issuedUnderAuthority) { this.issuedUnderAuthority = issuedUnderAuthority; }

    public String getDigitalArtifactRef() { return digitalArtifactRef; }
    public void setDigitalArtifactRef(String digitalArtifactRef) { this.digitalArtifactRef = digitalArtifactRef; }

    public Long getSupersedesCertificateId() { return supersedesCertificateId; }
    public void setSupersedesCertificateId(Long supersedesCertificateId) { this.supersedesCertificateId = supersedesCertificateId; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public String getCertificateNumber() { return certificateNumber; }
    public void setCertificateNumber(String certificateNumber) { this.certificateNumber = certificateNumber; }

    public String getRenewalReference() { return renewalReference; }
    public void setRenewalReference(String renewalReference) { this.renewalReference = renewalReference; }

    public LocalDate getSuspensionDate() { return suspensionDate; }
    public void setSuspensionDate(LocalDate suspensionDate) { this.suspensionDate = suspensionDate; }

    public LocalDate getRevocationDate() { return revocationDate; }
    public void setRevocationDate(LocalDate revocationDate) { this.revocationDate = revocationDate; }

    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }

    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }

    public String getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }

    public Instant getCreatedAt() { return createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
}
