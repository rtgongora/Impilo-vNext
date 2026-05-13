package zw.gov.mohcc.impilo.learning.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Native Fundo course lesson (Phase 5A). */
@Entity
@Table(
        name = "lrn_course_lesson",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_lrn_course_lesson_seq",
                columnNames = {"module_id", "sequence_no"}))
public class CourseLessonEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "module_id", nullable = false)
    private UUID moduleId;

    @Column(name = "title", nullable = false, length = 512)
    private String title;

    @Column(name = "content_type", nullable = false, length = 32)
    private String contentType;

    @Column(name = "content_body", columnDefinition = "TEXT")
    private String contentBody;

    @Column(name = "content_ref", length = 1024)
    private String contentRef;

    @Column(name = "estimated_duration_minutes")
    private Integer estimatedDurationMinutes;

    @Column(name = "sequence_no", nullable = false)
    private int sequenceNo;

    @Column(name = "required", nullable = false)
    private boolean required = true;

    @Column(name = "status", nullable = false, length = 32)
    private String status = "PUBLISHED";

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    void prePersist() {
        if (id == null) id = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() { updatedAt = OffsetDateTime.now(); }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public UUID getModuleId() { return moduleId; }
    public void setModuleId(UUID moduleId) { this.moduleId = moduleId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }
    public String getContentBody() { return contentBody; }
    public void setContentBody(String contentBody) { this.contentBody = contentBody; }
    public String getContentRef() { return contentRef; }
    public void setContentRef(String contentRef) { this.contentRef = contentRef; }
    public Integer getEstimatedDurationMinutes() { return estimatedDurationMinutes; }
    public void setEstimatedDurationMinutes(Integer m) { this.estimatedDurationMinutes = m; }
    public int getSequenceNo() { return sequenceNo; }
    public void setSequenceNo(int sequenceNo) { this.sequenceNo = sequenceNo; }
    public boolean isRequired() { return required; }
    public void setRequired(boolean required) { this.required = required; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
