package zw.gov.mohcc.impilo.tuso.persistence.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "facility_certificate", schema = "tuso")
public class FacilityCertificateEntity {

    @Id
    @Column(name = "certificate_id", nullable = false, updatable = false)
    private UUID certificateId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "facility_id", nullable = false)
    private FacilityEntity facility;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "application_id")
    private FacilityApplicationEntity application;

    @Column(name = "certificate_type", nullable = false, length = 64)
    private String certificateType;

    @Column(name = "certificate_number", nullable = false, length = 128)
    private String certificateNumber;

    @Column(name = "issue_date", nullable = false)
    private LocalDate issueDate;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 64)
    private FacilityCertificateStatus status = FacilityCertificateStatus.ACTIVE;

    @Column(name = "issued_under_authority", nullable = false, length = 128)
    private String issuedUnderAuthority;

    @Column(name = "digital_artifact_reference", length = 512)
    private String digitalArtifactReference;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supersedes_certificate_id")
    private FacilityCertificateEntity supersedesCertificate;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private Map<String, Object> metadata;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "issued_by", nullable = false, length = 255)
    private String issuedBy;

    @PrePersist
    void onCreate() {
        if (certificateId == null) {
            certificateId = UUID.randomUUID();
        }
        createdAt = Instant.now();
    }

    public UUID getCertificateId() { return certificateId; }
    public void setCertificateId(UUID certificateId) { this.certificateId = certificateId; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public FacilityEntity getFacility() { return facility; }
    public void setFacility(FacilityEntity facility) { this.facility = facility; }
    public FacilityApplicationEntity getApplication() { return application; }
    public void setApplication(FacilityApplicationEntity application) { this.application = application; }
    public String getCertificateType() { return certificateType; }
    public void setCertificateType(String certificateType) { this.certificateType = certificateType; }
    public String getCertificateNumber() { return certificateNumber; }
    public void setCertificateNumber(String certificateNumber) { this.certificateNumber = certificateNumber; }
    public LocalDate getIssueDate() { return issueDate; }
    public void setIssueDate(LocalDate issueDate) { this.issueDate = issueDate; }
    public LocalDate getExpiryDate() { return expiryDate; }
    public void setExpiryDate(LocalDate expiryDate) { this.expiryDate = expiryDate; }
    public FacilityCertificateStatus getStatus() { return status; }
    public void setStatus(FacilityCertificateStatus status) { this.status = status; }
    public String getIssuedUnderAuthority() { return issuedUnderAuthority; }
    public void setIssuedUnderAuthority(String issuedUnderAuthority) { this.issuedUnderAuthority = issuedUnderAuthority; }
    public String getDigitalArtifactReference() { return digitalArtifactReference; }
    public void setDigitalArtifactReference(String digitalArtifactReference) { this.digitalArtifactReference = digitalArtifactReference; }
    public FacilityCertificateEntity getSupersedesCertificate() { return supersedesCertificate; }
    public void setSupersedesCertificate(FacilityCertificateEntity supersedesCertificate) { this.supersedesCertificate = supersedesCertificate; }
    public Map<String, Object> getMetadata() { return metadata; }
    public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }
    public Instant getCreatedAt() { return createdAt; }
    public String getIssuedBy() { return issuedBy; }
    public void setIssuedBy(String issuedBy) { this.issuedBy = issuedBy; }
}
