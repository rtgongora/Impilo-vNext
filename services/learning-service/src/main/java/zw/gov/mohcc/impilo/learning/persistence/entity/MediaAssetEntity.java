package zw.gov.mohcc.impilo.learning.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Studio media asset ({@code lrn_media_asset}, V008 + V029). Historically only
 * written through the native-SQL studio surface; the JPA mapping was introduced
 * in W4 (LEARNING_RECORDING) for the replay-adoption consumer and the
 * watch-progress APIs.
 *
 * <p><strong>storage_ref semantics (V029):</strong> when {@code storageKind} is
 * {@link #STORAGE_KIND_DOCUMENT_OBJECT} the ref is a document-service object id
 * (UUID) — playback URLs are minted per-request via the document-service
 * signed-url endpoint (document-service stays the artifact SoR). When
 * {@code storageKind} is {@code EXTERNAL_URL} the ref is a directly usable URL
 * (legacy authoring). {@code NULL} storageKind = legacy opaque string.</p>
 */
@Entity
@Table(name = "lrn_media_asset")
public class MediaAssetEntity {

    public static final String STORAGE_KIND_DOCUMENT_OBJECT = "DOCUMENT_OBJECT";
    public static final String STORAGE_KIND_EXTERNAL_URL = "EXTERNAL_URL";

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "title", nullable = false, length = 512)
    private String title;

    @Column(name = "media_type", nullable = false, length = 32)
    private String mediaType;

    @Column(name = "status", nullable = false, length = 32)
    private String status = "DRAFT";

    @Column(name = "file_name", length = 512)
    private String fileName;

    @Column(name = "mime_type", length = 255)
    private String mimeType;

    @Column(name = "storage_ref", length = 1024)
    private String storageRef;

    @Column(name = "storage_kind", length = 32)
    private String storageKind;

    @Column(name = "transcript", columnDefinition = "TEXT")
    private String transcript;

    @Column(name = "caption_ref", length = 1024)
    private String captionRef;

    @Column(name = "recording_type", length = 64)
    private String recordingType;

    /** Lesson attachment point (V008) — the lesson this asset backs. */
    @Column(name = "source_lesson_id")
    private UUID sourceLessonId;

    @Column(name = "duration_seconds")
    private Integer durationSeconds;

    /** Impilo Live event whose replay produced this asset (V029). */
    @Column(name = "live_event_id")
    private UUID liveEventId;

    /** Course linkage resolved from the scheduled learning session (V029). */
    @Column(name = "course_id")
    private UUID courseId;

    /** TRUE = replay asset waiting in the authoring queue for lesson attachment. */
    @Column(name = "unattached", nullable = false)
    private boolean unattached;

    @Column(name = "metadata_json", columnDefinition = "TEXT")
    private String metadataJson;

    @Column(name = "created_by", nullable = false, length = 255)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    void prePersist() {
        if (id == null) id = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() { updatedAt = OffsetDateTime.now(); }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getMediaType() { return mediaType; }
    public void setMediaType(String mediaType) { this.mediaType = mediaType; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }
    public String getMimeType() { return mimeType; }
    public void setMimeType(String mimeType) { this.mimeType = mimeType; }
    public String getStorageRef() { return storageRef; }
    public void setStorageRef(String storageRef) { this.storageRef = storageRef; }
    public String getStorageKind() { return storageKind; }
    public void setStorageKind(String storageKind) { this.storageKind = storageKind; }
    public String getTranscript() { return transcript; }
    public void setTranscript(String transcript) { this.transcript = transcript; }
    public String getCaptionRef() { return captionRef; }
    public void setCaptionRef(String captionRef) { this.captionRef = captionRef; }
    public String getRecordingType() { return recordingType; }
    public void setRecordingType(String recordingType) { this.recordingType = recordingType; }
    public UUID getSourceLessonId() { return sourceLessonId; }
    public void setSourceLessonId(UUID sourceLessonId) { this.sourceLessonId = sourceLessonId; }
    public Integer getDurationSeconds() { return durationSeconds; }
    public void setDurationSeconds(Integer durationSeconds) { this.durationSeconds = durationSeconds; }
    public UUID getLiveEventId() { return liveEventId; }
    public void setLiveEventId(UUID liveEventId) { this.liveEventId = liveEventId; }
    public UUID getCourseId() { return courseId; }
    public void setCourseId(UUID courseId) { this.courseId = courseId; }
    public boolean isUnattached() { return unattached; }
    public void setUnattached(boolean unattached) { this.unattached = unattached; }
    public String getMetadataJson() { return metadataJson; }
    public void setMetadataJson(String metadataJson) { this.metadataJson = metadataJson; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
