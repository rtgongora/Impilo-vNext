package zw.gov.mohcc.impilo.varapi.persistence.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "provider_council_documents", schema = "varapi")
public class ProviderCouncilDocumentEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "tenant_id", nullable = false) private UUID tenantId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "provider_id") private ProviderEntity provider;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "application_id") private ProviderApplicationEntity application;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "council_id") private CouncilEntity council;
    @Column(name = "document_type", nullable = false, length = 80) private String documentType;
    @Column(name = "file_reference", nullable = false, length = 512) private String fileReference;
    @Column(name = "verification_state", nullable = false, length = 40) private String verificationState = "PENDING";
    @Column(name = "uploaded_by", length = 255) private String uploadedBy;
    @Column(name = "uploaded_at", nullable = false) private Instant uploadedAt = Instant.now();
    @Column(name = "reviewed_by", length = 255) private String reviewedBy;
    @Column(name = "reviewed_at") private Instant reviewedAt;
    @Column(name = "metadata", columnDefinition = "JSONB") private String metadata;
    public Long getId() { return id; } public void setId(Long id) { this.id = id; }
    public UUID getTenantId() { return tenantId; } public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public ProviderEntity getProvider() { return provider; } public void setProvider(ProviderEntity provider) { this.provider = provider; }
    public ProviderApplicationEntity getApplication() { return application; } public void setApplication(ProviderApplicationEntity application) { this.application = application; }
    public CouncilEntity getCouncil() { return council; } public void setCouncil(CouncilEntity council) { this.council = council; }
    public String getDocumentType() { return documentType; } public void setDocumentType(String documentType) { this.documentType = documentType; }
    public String getFileReference() { return fileReference; } public void setFileReference(String fileReference) { this.fileReference = fileReference; }
    public String getVerificationState() { return verificationState; } public void setVerificationState(String verificationState) { this.verificationState = verificationState; }
    public String getUploadedBy() { return uploadedBy; } public void setUploadedBy(String uploadedBy) { this.uploadedBy = uploadedBy; }
    public Instant getUploadedAt() { return uploadedAt; } public void setUploadedAt(Instant uploadedAt) { this.uploadedAt = uploadedAt; }
    public String getReviewedBy() { return reviewedBy; } public void setReviewedBy(String reviewedBy) { this.reviewedBy = reviewedBy; }
    public Instant getReviewedAt() { return reviewedAt; } public void setReviewedAt(Instant reviewedAt) { this.reviewedAt = reviewedAt; }
    public String getMetadata() { return metadata; } public void setMetadata(String metadata) { this.metadata = metadata; }
}
