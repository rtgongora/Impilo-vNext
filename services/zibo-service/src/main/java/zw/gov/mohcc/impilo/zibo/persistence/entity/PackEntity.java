package zw.gov.mohcc.impilo.zibo.persistence.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;
import zw.gov.mohcc.impilo.zibo.domain.ArtifactStatus;

/**
 * Represents a curated pack of terminology artifacts that can be
 * assigned to tenants, facilities, or workspaces as a unit.
 */
@Entity
@Table(name = "zibo_packs")
public class PackEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "pack_id", nullable = false)
    private UUID packId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "version", nullable = false)
    private String version;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ArtifactStatus status = ArtifactStatus.DRAFT;

    @Column(name = "manifest_json", columnDefinition = "jsonb")
    private String manifestJson;

    @Column(name = "created_by", nullable = false)
    private String createdBy;

    @Column(name = "published_by")
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
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    // getId/setId aliases

    public UUID getId() { return packId; }
    public void setId(UUID id) { this.packId = id; }

    // Getters and setters

    public UUID getPackId() { return packId; }
    public void setPackId(UUID packId) { this.packId = packId; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }

    public ArtifactStatus getStatus() { return status; }
    public void setStatus(ArtifactStatus status) { this.status = status; }

    public String getManifestJson() { return manifestJson; }
    public void setManifestJson(String manifestJson) { this.manifestJson = manifestJson; }

    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }

    public String getPublishedBy() { return publishedBy; }
    public void setPublishedBy(String publishedBy) { this.publishedBy = publishedBy; }

    public OffsetDateTime getPublishedAt() { return publishedAt; }
    public void setPublishedAt(OffsetDateTime publishedAt) { this.publishedAt = publishedAt; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
