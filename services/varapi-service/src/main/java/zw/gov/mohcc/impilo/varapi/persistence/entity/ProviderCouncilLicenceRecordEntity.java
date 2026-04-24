package zw.gov.mohcc.impilo.varapi.persistence.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "provider_council_licence_records", schema = "varapi")
public class ProviderCouncilLicenceRecordEntity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "tenant_id", nullable = false) private UUID tenantId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "provider_id") private ProviderEntity provider;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "council_id") private CouncilEntity council;
    @Column(name = "licence_type", nullable = false, length = 80) private String licenceType;
    @Column(name = "issue_date", nullable = false) private LocalDate issueDate;
    @Column(name = "expiry_date") private LocalDate expiryDate;
    @Column(name = "status", nullable = false, length = 40) private String status = "ACTIVE";
    @Column(name = "restriction_summary", columnDefinition = "TEXT") private String restrictionSummary;
    @Column(name = "issued_under_authority", length = 255) private String issuedUnderAuthority;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "supersedes_licence_id") private ProviderCouncilLicenceRecordEntity supersedesLicence;
    @Column(name = "certificate_artifact_reference", length = 512) private String certificateArtifactReference;
    @Column(name = "metadata", columnDefinition = "JSONB") private String metadata;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    @PrePersist void pc() { Instant n = Instant.now(); createdAt = n; updatedAt = n; }
    @PreUpdate void pu() { updatedAt = Instant.now(); }

    public Long getId() { return id; } public void setId(Long id) { this.id = id; }
    public UUID getTenantId() { return tenantId; } public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public ProviderEntity getProvider() { return provider; } public void setProvider(ProviderEntity provider) { this.provider = provider; }
    public CouncilEntity getCouncil() { return council; } public void setCouncil(CouncilEntity council) { this.council = council; }
    public String getLicenceType() { return licenceType; } public void setLicenceType(String licenceType) { this.licenceType = licenceType; }
    public LocalDate getIssueDate() { return issueDate; } public void setIssueDate(LocalDate issueDate) { this.issueDate = issueDate; }
    public LocalDate getExpiryDate() { return expiryDate; } public void setExpiryDate(LocalDate expiryDate) { this.expiryDate = expiryDate; }
    public String getStatus() { return status; } public void setStatus(String status) { this.status = status; }
    public String getRestrictionSummary() { return restrictionSummary; } public void setRestrictionSummary(String restrictionSummary) { this.restrictionSummary = restrictionSummary; }
    public String getIssuedUnderAuthority() { return issuedUnderAuthority; } public void setIssuedUnderAuthority(String issuedUnderAuthority) { this.issuedUnderAuthority = issuedUnderAuthority; }
    public ProviderCouncilLicenceRecordEntity getSupersedesLicence() { return supersedesLicence; }
    public void setSupersedesLicence(ProviderCouncilLicenceRecordEntity supersedesLicence) { this.supersedesLicence = supersedesLicence; }
    public String getCertificateArtifactReference() { return certificateArtifactReference; }
    public void setCertificateArtifactReference(String certificateArtifactReference) { this.certificateArtifactReference = certificateArtifactReference; }
    public String getMetadata() { return metadata; } public void setMetadata(String metadata) { this.metadata = metadata; }
    public Instant getCreatedAt() { return createdAt; } public Instant getUpdatedAt() { return updatedAt; }
}
