package zw.gov.mohcc.impilo.simba.social.entity;

import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.UUID;

/** A social media asset — a REFERENCE to a document-service (MinIO) object. Never stores bytes. */
@Entity
@Table(name = "simba_social_media_asset", schema = "simba")
public class MediaAssetEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "media_asset_id", nullable = false, unique = true)
    private UUID mediaAssetId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "owner_cpid", nullable = false, length = 128)
    private String ownerCpid;

    @Column(name = "document_object_id", nullable = false, length = 128)
    private String documentObjectId;

    @Column(name = "thumbnail_object_id", length = 128)
    private String thumbnailObjectId;

    @Column(name = "content_type", length = 64)
    private String contentType;

    @Column(name = "media_kind", nullable = false, length = 16)
    private String mediaKind = "VIDEO";

    @Column(name = "duration_seconds")
    private Integer durationSeconds;

    @Column(name = "processing_status", nullable = false, length = 16)
    private String processingStatus = "PENDING";

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    void onCreate() {
        if (mediaAssetId == null) {
            mediaAssetId = UUID.randomUUID();
        }
        OffsetDateTime now = OffsetDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    public Long getId() { return id; }
    public UUID getMediaAssetId() { return mediaAssetId; }
    public void setMediaAssetId(UUID mediaAssetId) { this.mediaAssetId = mediaAssetId; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public String getOwnerCpid() { return ownerCpid; }
    public void setOwnerCpid(String ownerCpid) { this.ownerCpid = ownerCpid; }
    public String getDocumentObjectId() { return documentObjectId; }
    public void setDocumentObjectId(String documentObjectId) { this.documentObjectId = documentObjectId; }
    public String getThumbnailObjectId() { return thumbnailObjectId; }
    public void setThumbnailObjectId(String thumbnailObjectId) { this.thumbnailObjectId = thumbnailObjectId; }
    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }
    public String getMediaKind() { return mediaKind; }
    public void setMediaKind(String mediaKind) { this.mediaKind = mediaKind; }
    public Integer getDurationSeconds() { return durationSeconds; }
    public void setDurationSeconds(Integer durationSeconds) { this.durationSeconds = durationSeconds; }
    public String getProcessingStatus() { return processingStatus; }
    public void setProcessingStatus(String processingStatus) { this.processingStatus = processingStatus; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
