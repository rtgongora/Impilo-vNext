package zw.gov.mohcc.impilo.landela.persistence.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "documents", schema = "landela_adapter")
public class DocumentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "document_id", nullable = false, unique = true)
    private UUID documentId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "subject_type", nullable = false, length = 50)
    private String subjectType;

    @Column(name = "subject_id", nullable = false, length = 255)
    private String subjectId;

    @Column(name = "document_type_code", length = 100)
    private String documentTypeCode;

    @Column(name = "source", nullable = false, length = 20)
    private String source = "uploaded";

    @Column(name = "issuer", length = 255)
    private String issuer;

    @Column(name = "confidentiality", nullable = false, length = 30)
    private String confidentiality = "NORMAL";

    @Column(name = "hash_sha256", nullable = false, length = 64)
    private String hashSha256;

    @Column(name = "mime_type", nullable = false, length = 100)
    private String mimeType;

    @Column(name = "file_name", length = 500)
    private String fileName;

    @Column(name = "file_size_bytes")
    private Long fileSizeBytes;

    @Column(name = "storage_provider", nullable = false, length = 20)
    private String storageProvider = "INTERNAL";

    @Column(name = "storage_ref", length = 500)
    private String storageRef;

    @Column(name = "retention_policy_id", length = 100)
    private String retentionPolicyId;

    @JdbcTypeCode(SqlTypes.JSON)


    @Column(name = "tags", columnDefinition = "jsonb")
    private String tags = "{}";

    @Column(name = "lifecycle_state", nullable = false, length = 30)
    private String lifecycleState = "ACTIVE";

    @Column(name = "created_by", nullable = false, length = 255)
    private String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "superseded_by")
    private Long supersededBy;

    @Column(name = "version", nullable = false)
    private Integer version = 1;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
        updatedAt = OffsetDateTime.now();
        if (documentId == null) {
            documentId = UUID.randomUUID();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    // Getters and setters

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public UUID getDocumentId() { return documentId; }
    public void setDocumentId(UUID documentId) { this.documentId = documentId; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public String getSubjectType() { return subjectType; }
    public void setSubjectType(String subjectType) { this.subjectType = subjectType; }
    public String getSubjectId() { return subjectId; }
    public void setSubjectId(String subjectId) { this.subjectId = subjectId; }
    public String getDocumentTypeCode() { return documentTypeCode; }
    public void setDocumentTypeCode(String documentTypeCode) { this.documentTypeCode = documentTypeCode; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public String getIssuer() { return issuer; }
    public void setIssuer(String issuer) { this.issuer = issuer; }
    public String getConfidentiality() { return confidentiality; }
    public void setConfidentiality(String confidentiality) { this.confidentiality = confidentiality; }
    public String getHashSha256() { return hashSha256; }
    public void setHashSha256(String hashSha256) { this.hashSha256 = hashSha256; }
    public String getMimeType() { return mimeType; }
    public void setMimeType(String mimeType) { this.mimeType = mimeType; }
    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }
    public Long getFileSizeBytes() { return fileSizeBytes; }
    public void setFileSizeBytes(Long fileSizeBytes) { this.fileSizeBytes = fileSizeBytes; }
    public String getStorageProvider() { return storageProvider; }
    public void setStorageProvider(String storageProvider) { this.storageProvider = storageProvider; }
    public String getStorageRef() { return storageRef; }
    public void setStorageRef(String storageRef) { this.storageRef = storageRef; }
    public String getRetentionPolicyId() { return retentionPolicyId; }
    public void setRetentionPolicyId(String retentionPolicyId) { this.retentionPolicyId = retentionPolicyId; }
    public String getTags() { return tags; }
    public void setTags(String tags) { this.tags = tags; }
    public String getLifecycleState() { return lifecycleState; }
    public void setLifecycleState(String lifecycleState) { this.lifecycleState = lifecycleState; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public Long getSupersededBy() { return supersededBy; }
    public void setSupersededBy(Long supersededBy) { this.supersededBy = supersededBy; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
}
