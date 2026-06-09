package zw.gov.mohcc.impilo.inpatient.persistence.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "procedure_episode_document", schema = "inpatient")
public class ProcedureEpisodeDocumentEntity {

    @Id
    @Column(name = "document_link_id")
    private UUID documentLinkId;

    @Column(name = "episode_id", nullable = false)
    private UUID episodeId;

    @Column(name = "document_object_id", nullable = false)
    private UUID documentObjectId;

    @Column(name = "document_type", nullable = false)
    private String documentType;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "storage_key")
    private String storageKey;

    @Column(name = "recorded_by")
    private String recordedBy;

    @Column(name = "recorded_at")
    private OffsetDateTime recordedAt;

    @PrePersist
    void onCreate() {
        if (documentLinkId == null) documentLinkId = UUID.randomUUID();
        if (recordedAt == null) recordedAt = OffsetDateTime.now();
    }

    public UUID getDocumentLinkId() { return documentLinkId; }
    public void setDocumentLinkId(UUID documentLinkId) { this.documentLinkId = documentLinkId; }
    public UUID getEpisodeId() { return episodeId; }
    public void setEpisodeId(UUID episodeId) { this.episodeId = episodeId; }
    public UUID getDocumentObjectId() { return documentObjectId; }
    public void setDocumentObjectId(UUID documentObjectId) { this.documentObjectId = documentObjectId; }
    public String getDocumentType() { return documentType; }
    public void setDocumentType(String documentType) { this.documentType = documentType; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getStorageKey() { return storageKey; }
    public void setStorageKey(String storageKey) { this.storageKey = storageKey; }
    public String getRecordedBy() { return recordedBy; }
    public void setRecordedBy(String recordedBy) { this.recordedBy = recordedBy; }
    public OffsetDateTime getRecordedAt() { return recordedAt; }
    public void setRecordedAt(OffsetDateTime recordedAt) { this.recordedAt = recordedAt; }
}
