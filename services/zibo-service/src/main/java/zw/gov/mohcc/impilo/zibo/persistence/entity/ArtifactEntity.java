package zw.gov.mohcc.impilo.zibo.persistence.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;
import zw.gov.mohcc.impilo.zibo.domain.ArtifactStatus;
import zw.gov.mohcc.impilo.zibo.domain.ArtifactType;

/**
 * Represents a FHIR terminology artifact (CodeSystem, ValueSet, ConceptMap, etc.)
 * managed by ZIBO. Each artifact is versioned and tenant-scoped.
 */
@Entity
@Table(name = "zibo_artifacts")
public class ArtifactEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "artifact_id", nullable = false)
    private UUID artifactId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Enumerated(EnumType.STRING)
    @Column(name = "fhir_type", nullable = false)
    private ArtifactType fhirType;

    @Column(name = "canonical_url", nullable = false, length = 500)
    private String canonicalUrl;

    @Column(name = "version", nullable = false, length = 50)
    private String version;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "title", length = 500)
    private String title;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ArtifactStatus status = ArtifactStatus.DRAFT;

    @Column(name = "content_json", nullable = false, columnDefinition = "jsonb")
    private String contentJson;

    @Column(name = "content_hash", nullable = false, length = 64)
    private String contentHash;

    @Column(name = "publisher", length = 255)
    private String publisher;

    @Column(name = "jurisdiction")
    private String jurisdiction = "ZW";

    @Column(name = "created_by", nullable = false)
    private String createdBy;

    @Column(name = "published_by")
    private String publishedBy;

    @Column(name = "published_at")
    private OffsetDateTime publishedAt;

    @Column(name = "deprecated_at")
    private OffsetDateTime deprecatedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
        updatedAt = OffsetDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    // getId/setId aliases

    public UUID getId() { return artifactId; }
    public void setId(UUID id) { this.artifactId = id; }

    // Getters and setters

    public UUID getArtifactId() { return artifactId; }
    public void setArtifactId(UUID artifactId) { this.artifactId = artifactId; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public ArtifactType getFhirType() { return fhirType; }
    public void setFhirType(ArtifactType fhirType) { this.fhirType = fhirType; }

    public String getCanonicalUrl() { return canonicalUrl; }
    public void setCanonicalUrl(String canonicalUrl) { this.canonicalUrl = canonicalUrl; }

    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public ArtifactStatus getStatus() { return status; }
    public void setStatus(ArtifactStatus status) { this.status = status; }

    public String getContentJson() { return contentJson; }
    public void setContentJson(String contentJson) { this.contentJson = contentJson; }

    public String getContentHash() { return contentHash; }
    public void setContentHash(String contentHash) { this.contentHash = contentHash; }

    public String getPublisher() { return publisher; }
    public void setPublisher(String publisher) { this.publisher = publisher; }

    public String getJurisdiction() { return jurisdiction; }
    public void setJurisdiction(String jurisdiction) { this.jurisdiction = jurisdiction; }

    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }

    public String getPublishedBy() { return publishedBy; }
    public void setPublishedBy(String publishedBy) { this.publishedBy = publishedBy; }

    public OffsetDateTime getPublishedAt() { return publishedAt; }
    public void setPublishedAt(OffsetDateTime publishedAt) { this.publishedAt = publishedAt; }

    public OffsetDateTime getDeprecatedAt() { return deprecatedAt; }
    public void setDeprecatedAt(OffsetDateTime deprecatedAt) { this.deprecatedAt = deprecatedAt; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
