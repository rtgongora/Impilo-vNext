package zw.gov.mohcc.impilo.varapi.persistence.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Provider qualification entity capturing education and credentials.
 */
@Entity
@Table(name = "provider_qualifications", schema = "varapi")
public class ProviderQualificationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "provider_id", nullable = false)
    private ProviderEntity provider;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "qualification_type", nullable = false, length = 50)
    private String qualificationType;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "institution_name", nullable = false, length = 255)
    private String institutionName;

    @Column(name = "country", nullable = false, length = 100)
    private String country;

    @Column(name = "award_date")
    private LocalDate awardDate;

    @Column(name = "verification_status", length = 20)
    private String verificationStatus = "PENDING";

    @Column(name = "verified_by", length = 255)
    private String verifiedBy;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    @Column(name = "evidence_document_id")
    private Long evidenceDocumentId;

    @Column(name = "remarks", columnDefinition = "TEXT")
    private String remarks;

    @Column(name = "qualification_code", length = 50)
    private String qualificationCode;

    @Column(name = "qualification_level", length = 50)
    private String qualificationLevel;

    @Column(name = "professional_category", length = 100)
    private String professionalCategory;

    @Column(name = "verification_date")
    private LocalDate verificationDate;

    @Column(name = "verification_notes", columnDefinition = "TEXT")
    private String verificationNotes;

    @Column(name = "supersedes_id")
    private Long supersedesId;

    @Column(name = "version")
    private Integer version;

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

    public boolean isVerified() {
        return "VERIFIED".equals(verificationStatus);
    }

    // Getters and setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public ProviderEntity getProvider() { return provider; }
    public void setProvider(ProviderEntity provider) { this.provider = provider; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public String getQualificationType() { return qualificationType; }
    public void setQualificationType(String qualificationType) { this.qualificationType = qualificationType; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getInstitutionName() { return institutionName; }
    public void setInstitutionName(String institutionName) { this.institutionName = institutionName; }

    public String getCountry() { return country; }
    public void setCountry(String country) { this.country = country; }

    public LocalDate getAwardDate() { return awardDate; }
    public void setAwardDate(LocalDate awardDate) { this.awardDate = awardDate; }

    public String getVerificationStatus() { return verificationStatus; }
    public void setVerificationStatus(String verificationStatus) { this.verificationStatus = verificationStatus; }

    public String getVerifiedBy() { return verifiedBy; }
    public void setVerifiedBy(String verifiedBy) { this.verifiedBy = verifiedBy; }

    public Instant getVerifiedAt() { return verifiedAt; }
    public void setVerifiedAt(Instant verifiedAt) { this.verifiedAt = verifiedAt; }

    public Long getEvidenceDocumentId() { return evidenceDocumentId; }
    public void setEvidenceDocumentId(Long evidenceDocumentId) { this.evidenceDocumentId = evidenceDocumentId; }

    public String getRemarks() { return remarks; }
    public void setRemarks(String remarks) { this.remarks = remarks; }

    public String getQualificationCode() { return qualificationCode; }
    public void setQualificationCode(String qualificationCode) { this.qualificationCode = qualificationCode; }

    public String getQualificationLevel() { return qualificationLevel; }
    public void setQualificationLevel(String qualificationLevel) { this.qualificationLevel = qualificationLevel; }

    public String getProfessionalCategory() { return professionalCategory; }
    public void setProfessionalCategory(String professionalCategory) { this.professionalCategory = professionalCategory; }

    public LocalDate getVerificationDate() { return verificationDate; }
    public void setVerificationDate(LocalDate verificationDate) { this.verificationDate = verificationDate; }

    public String getVerificationNotes() { return verificationNotes; }
    public void setVerificationNotes(String verificationNotes) { this.verificationNotes = verificationNotes; }

    public Long getSupersedesId() { return supersedesId; }
    public void setSupersedesId(Long supersedesId) { this.supersedesId = supersedesId; }

    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }

    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }

    public String getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }

    public Instant getCreatedAt() { return createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
}
