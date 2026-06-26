package zw.gov.mohcc.impilo.patientsafety.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Reference to an attachment whose binary is stored in document-service. */
@Entity
@Table(name = "ps_attachment_ref")
public class AttachmentRefEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;
    @Column(name = "report_id", nullable = false)
    private UUID reportId;
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;
    @Column(name = "document_object_id", nullable = false, length = 255)
    private String documentObjectId;
    @Column(name = "filename", length = 512)
    private String filename;
    @Column(name = "mime_type", length = 128)
    private String mimeType;
    @Column(name = "description", length = 512)
    private String description;
    @Column(name = "consent_obtained", nullable = false)
    private boolean consentObtained = false;
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected AttachmentRefEntity() {}

    public AttachmentRefEntity(UUID reportId, UUID tenantId, String documentObjectId) {
        this.id = UUID.randomUUID();
        this.reportId = reportId;
        this.tenantId = tenantId;
        this.documentObjectId = documentObjectId;
        this.createdAt = OffsetDateTime.now();
    }

    public UUID getId() { return id; }
    public UUID getReportId() { return reportId; }
    public UUID getTenantId() { return tenantId; }
    public String getDocumentObjectId() { return documentObjectId; }
    public String getFilename() { return filename; }
    public void setFilename(String v) { this.filename = v; }
    public String getMimeType() { return mimeType; }
    public void setMimeType(String v) { this.mimeType = v; }
    public String getDescription() { return description; }
    public void setDescription(String v) { this.description = v; }
    public boolean isConsentObtained() { return consentObtained; }
    public void setConsentObtained(boolean v) { this.consentObtained = v; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
