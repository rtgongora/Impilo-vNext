package zw.gov.mohcc.impilo.indawo.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "ind_site_documents")
public class SiteDocumentEntity {

    @Id
    @Column(name = "document_id", nullable = false)
    private UUID documentId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "site_id", nullable = false)
    private UUID siteId;

    @Column(name = "application_id")
    private UUID applicationId;

    @Column(name = "document_type", nullable = false, length = 64)
    private String documentType;

    /**
     * Storage reference. For document-service integration this is `docstore:{objectId}`.
     */
    @Column(name = "file_reference", nullable = false, length = 512)
    private String fileReference;

    @Column(name = "version", nullable = false)
    private int version = 1;

    @Column(name = "verification_state", nullable = false, length = 32)
    private String verificationState = "UNVERIFIED";

    @Column(name = "uploaded_by", length = 255)
    private String uploadedBy;

    @Column(name = "uploaded_at", nullable = false)
    private OffsetDateTime uploadedAt;

    @Column(name = "reviewed_by", length = 255)
    private String reviewedBy;

    @Column(name = "reviewed_at")
    private OffsetDateTime reviewedAt;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "metadata", columnDefinition = "JSONB")
    private String metadataJson;

    protected SiteDocumentEntity() {}

    public SiteDocumentEntity(UUID documentId, UUID tenantId, UUID siteId, String documentType, String fileReference) {
        this.documentId = documentId;
        this.tenantId = tenantId;
        this.siteId = siteId;
        this.documentType = documentType;
        this.fileReference = fileReference;
        this.uploadedAt = OffsetDateTime.now();
    }

    public UUID getDocumentId() { return documentId; }
    public UUID getTenantId() { return tenantId; }
    public UUID getSiteId() { return siteId; }
    public UUID getApplicationId() { return applicationId; }
    public void setApplicationId(UUID applicationId) { this.applicationId = applicationId; }
    public String getDocumentType() { return documentType; }
    public String getFileReference() { return fileReference; }
    public int getVersion() { return version; }
    public void setVersion(int version) { this.version = version; }
    public String getVerificationState() { return verificationState; }
    public void setVerificationState(String verificationState) { this.verificationState = verificationState; }
    public String getUploadedBy() { return uploadedBy; }
    public void setUploadedBy(String uploadedBy) { this.uploadedBy = uploadedBy; }
    public OffsetDateTime getUploadedAt() { return uploadedAt; }
    public String getReviewedBy() { return reviewedBy; }
    public OffsetDateTime getReviewedAt() { return reviewedAt; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public String getMetadataJson() { return metadataJson; }
    public void setMetadataJson(String metadataJson) { this.metadataJson = metadataJson; }

    public void review(String reviewer, String state, String notes) {
        this.reviewedBy = reviewer;
        this.reviewedAt = OffsetDateTime.now();
        this.verificationState = state;
        this.notes = notes;
    }
}

