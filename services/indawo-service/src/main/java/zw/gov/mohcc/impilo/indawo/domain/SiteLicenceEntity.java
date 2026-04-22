package zw.gov.mohcc.impilo.indawo.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "ind_site_licences")
public class SiteLicenceEntity {

    @Id
    @Column(name = "licence_id", nullable = false)
    private UUID licenceId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "site_id", nullable = false)
    private UUID siteId;

    @Column(name = "licence_type", nullable = false, length = 64)
    private String licenceType;

    @Column(name = "issue_date", nullable = false)
    private LocalDate issueDate;

    @Column(name = "expiry_date", nullable = false)
    private LocalDate expiryDate;

    @Column(name = "status", nullable = false, length = 32)
    private String status = "ACTIVE";

    @Column(name = "restriction_summary", columnDefinition = "TEXT")
    private String restrictionSummary;

    @Column(name = "issued_under_authority", length = 255)
    private String issuedUnderAuthority;

    @Column(name = "supersedes_licence_id")
    private UUID supersedesLicenceId;

    @Column(name = "digital_artifact_ref", length = 512)
    private String digitalArtifactRef;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected SiteLicenceEntity() {}

    public SiteLicenceEntity(UUID licenceId, UUID tenantId, UUID siteId, String licenceType, LocalDate issueDate, LocalDate expiryDate) {
        this.licenceId = licenceId;
        this.tenantId = tenantId;
        this.siteId = siteId;
        this.licenceType = licenceType;
        this.issueDate = issueDate;
        this.expiryDate = expiryDate;
        this.createdAt = OffsetDateTime.now();
    }

    public UUID getLicenceId() { return licenceId; }
    public UUID getTenantId() { return tenantId; }
    public UUID getSiteId() { return siteId; }
    public String getLicenceType() { return licenceType; }
    public LocalDate getIssueDate() { return issueDate; }
    public LocalDate getExpiryDate() { return expiryDate; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getRestrictionSummary() { return restrictionSummary; }
    public void setRestrictionSummary(String restrictionSummary) { this.restrictionSummary = restrictionSummary; }
    public String getIssuedUnderAuthority() { return issuedUnderAuthority; }
    public void setIssuedUnderAuthority(String issuedUnderAuthority) { this.issuedUnderAuthority = issuedUnderAuthority; }
    public UUID getSupersedesLicenceId() { return supersedesLicenceId; }
    public void setSupersedesLicenceId(UUID supersedesLicenceId) { this.supersedesLicenceId = supersedesLicenceId; }
    public String getDigitalArtifactRef() { return digitalArtifactRef; }
    public void setDigitalArtifactRef(String digitalArtifactRef) { this.digitalArtifactRef = digitalArtifactRef; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}

