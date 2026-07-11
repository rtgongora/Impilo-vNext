package zw.gov.mohcc.impilo.tuso.persistence.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A versioned inspection requirement module — one per HPA manual
 * minimum-requirement schedule. Versions are immutable once persisted;
 * content changes require a new version.
 */
@Entity
@Table(name = "inspection_requirement_module", schema = "tuso")
public class InspectionRequirementModuleEntity {

    @Id
    @Column(name = "module_id", nullable = false, updatable = false)
    private UUID moduleId;

    @Column(name = "code", nullable = false, length = 96)
    private String code;

    @Column(name = "version", nullable = false)
    private Integer version = 1;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "scope", nullable = false, length = 32)
    private String scope;

    @Column(name = "source_doc", length = 64)
    private String sourceDoc;

    @Column(name = "source_heading", length = 255)
    private String sourceHeading;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "source_pages", columnDefinition = "jsonb")
    private List<Integer> sourcePages;

    @Column(name = "notes", columnDefinition = "text")
    private String notes;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "items", nullable = false, columnDefinition = "jsonb")
    private List<Map<String, Object>> items;

    @Column(name = "item_count", nullable = false)
    private Integer itemCount = 0;

    @Column(name = "review_required_count", nullable = false)
    private Integer reviewRequiredCount = 0;

    @Column(name = "content_checksum", nullable = false, length = 64)
    private String contentChecksum;

    @Column(name = "status", nullable = false, length = 48)
    private String status = "DRAFT";

    @Column(name = "approved_by", length = 255)
    private String approvedBy;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "effective_from")
    private LocalDate effectiveFrom;

    @Column(name = "supersedes_module_id")
    private UUID supersedesModuleId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @PrePersist
    void prePersist() {
        if (moduleId == null) moduleId = UUID.randomUUID();
        createdAt = createdAt == null ? Instant.now() : createdAt;
        updatedAt = Instant.now();
    }

    @PreUpdate
    void preUpdate() { updatedAt = Instant.now(); }

    public UUID getModuleId() { return moduleId; }
    public void setModuleId(UUID moduleId) { this.moduleId = moduleId; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getScope() { return scope; }
    public void setScope(String scope) { this.scope = scope; }
    public String getSourceDoc() { return sourceDoc; }
    public void setSourceDoc(String sourceDoc) { this.sourceDoc = sourceDoc; }
    public String getSourceHeading() { return sourceHeading; }
    public void setSourceHeading(String sourceHeading) { this.sourceHeading = sourceHeading; }
    public List<Integer> getSourcePages() { return sourcePages; }
    public void setSourcePages(List<Integer> sourcePages) { this.sourcePages = sourcePages; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public List<Map<String, Object>> getItems() { return items; }
    public void setItems(List<Map<String, Object>> items) { this.items = items; }
    public Integer getItemCount() { return itemCount; }
    public void setItemCount(Integer itemCount) { this.itemCount = itemCount; }
    public Integer getReviewRequiredCount() { return reviewRequiredCount; }
    public void setReviewRequiredCount(Integer reviewRequiredCount) { this.reviewRequiredCount = reviewRequiredCount; }
    public String getContentChecksum() { return contentChecksum; }
    public void setContentChecksum(String contentChecksum) { this.contentChecksum = contentChecksum; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getApprovedBy() { return approvedBy; }
    public void setApprovedBy(String approvedBy) { this.approvedBy = approvedBy; }
    public Instant getApprovedAt() { return approvedAt; }
    public void setApprovedAt(Instant approvedAt) { this.approvedAt = approvedAt; }
    public LocalDate getEffectiveFrom() { return effectiveFrom; }
    public void setEffectiveFrom(LocalDate effectiveFrom) { this.effectiveFrom = effectiveFrom; }
    public UUID getSupersedesModuleId() { return supersedesModuleId; }
    public void setSupersedesModuleId(UUID supersedesModuleId) { this.supersedesModuleId = supersedesModuleId; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
