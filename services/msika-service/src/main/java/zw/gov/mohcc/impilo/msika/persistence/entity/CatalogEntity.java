package zw.gov.mohcc.impilo.msika.persistence.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "msika_catalogs")
public class CatalogEntity {

    @Id
    @Column(name = "catalog_id", length = 26)
    private String catalogId;

    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(name = "scope", nullable = false, length = 10)
    private String scope;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "version", nullable = false, length = 50)
    private String version;

    @Column(name = "parent_catalog_id", length = 26)
    private String parentCatalogId;

    @Column(name = "checksum", length = 64)
    private String checksum;

    @Column(name = "created_by", nullable = false, length = 128)
    private String createdBy;

    @Column(name = "reviewed_by", length = 128)
    private String reviewedBy;

    @Column(name = "approved_by", length = 128)
    private String approvedBy;

    @Column(name = "published_by", length = 128)
    private String publishedBy;

    @Column(name = "published_at")
    private OffsetDateTime publishedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
        updatedAt = OffsetDateTime.now();
        if (status == null) status = "DRAFT";
        if (scope == null) scope = "NATIONAL";
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    // ID aliases for framework compatibility
    public String getId() { return catalogId; }
    public void setId(String id) { this.catalogId = id; }

    public String getCatalogId() { return catalogId; }
    public void setCatalogId(String catalogId) { this.catalogId = catalogId; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public String getScope() { return scope; }
    public void setScope(String scope) { this.scope = scope; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }
    public String getParentCatalogId() { return parentCatalogId; }
    public void setParentCatalogId(String parentCatalogId) { this.parentCatalogId = parentCatalogId; }
    public String getChecksum() { return checksum; }
    public void setChecksum(String checksum) { this.checksum = checksum; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public String getReviewedBy() { return reviewedBy; }
    public void setReviewedBy(String reviewedBy) { this.reviewedBy = reviewedBy; }
    public String getApprovedBy() { return approvedBy; }
    public void setApprovedBy(String approvedBy) { this.approvedBy = approvedBy; }
    public String getPublishedBy() { return publishedBy; }
    public void setPublishedBy(String publishedBy) { this.publishedBy = publishedBy; }
    public OffsetDateTime getPublishedAt() { return publishedAt; }
    public void setPublishedAt(OffsetDateTime publishedAt) { this.publishedAt = publishedAt; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
