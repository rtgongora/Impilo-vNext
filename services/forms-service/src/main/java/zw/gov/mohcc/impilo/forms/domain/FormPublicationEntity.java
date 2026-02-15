package zw.gov.mohcc.impilo.forms.domain;

import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "form_publications", schema = "forms")
public class FormPublicationEntity {

    @Id
    @Column(name = "id", length = 36, nullable = false)
    private String id;

    @Column(name = "form_id", length = 36, nullable = false)
    private String formId;

    @Column(name = "version_id", length = 36, nullable = false)
    private String versionId;

    @Column(name = "published_by", length = 255, nullable = false)
    private String publishedBy;

    @Column(name = "published_at", nullable = false, updatable = false)
    private OffsetDateTime publishedAt;

    @PrePersist
    protected void onCreate() {
        if (id == null) id = UUID.randomUUID().toString();
        if (publishedAt == null) publishedAt = OffsetDateTime.now();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getFormId() { return formId; }
    public void setFormId(String formId) { this.formId = formId; }

    public String getVersionId() { return versionId; }
    public void setVersionId(String versionId) { this.versionId = versionId; }

    public String getPublishedBy() { return publishedBy; }
    public void setPublishedBy(String publishedBy) { this.publishedBy = publishedBy; }

    public OffsetDateTime getPublishedAt() { return publishedAt; }
}
