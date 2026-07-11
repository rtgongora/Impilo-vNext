package zw.gov.mohcc.impilo.tuso.persistence.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * An inspection template composition — an ordered set of requirement modules
 * (common module first) valid for specific inspection types.
 */
@Entity
@Table(name = "inspection_template_composition", schema = "tuso")
public class InspectionTemplateCompositionEntity {

    @Id
    @Column(name = "composition_id", nullable = false, updatable = false)
    private UUID compositionId;

    @Column(name = "code", nullable = false, length = 96)
    private String code;

    @Column(name = "version", nullable = false)
    private Integer version = 1;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "module_codes", nullable = false, columnDefinition = "jsonb")
    private List<String> moduleCodes;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "inspection_types", nullable = false, columnDefinition = "jsonb")
    private List<String> inspectionTypes;

    @Column(name = "status", nullable = false, length = 48)
    private String status = "DRAFT";

    @Column(name = "approved_by", length = 255)
    private String approvedBy;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "effective_from")
    private LocalDate effectiveFrom;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @PrePersist
    void prePersist() {
        if (compositionId == null) compositionId = UUID.randomUUID();
        if (createdAt == null) createdAt = Instant.now();
    }

    public UUID getCompositionId() { return compositionId; }
    public void setCompositionId(UUID compositionId) { this.compositionId = compositionId; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public List<String> getModuleCodes() { return moduleCodes; }
    public void setModuleCodes(List<String> moduleCodes) { this.moduleCodes = moduleCodes; }
    public List<String> getInspectionTypes() { return inspectionTypes; }
    public void setInspectionTypes(List<String> inspectionTypes) { this.inspectionTypes = inspectionTypes; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getApprovedBy() { return approvedBy; }
    public void setApprovedBy(String approvedBy) { this.approvedBy = approvedBy; }
    public Instant getApprovedAt() { return approvedAt; }
    public void setApprovedAt(Instant approvedAt) { this.approvedAt = approvedAt; }
    public LocalDate getEffectiveFrom() { return effectiveFrom; }
    public void setEffectiveFrom(LocalDate effectiveFrom) { this.effectiveFrom = effectiveFrom; }
    public Instant getCreatedAt() { return createdAt; }
}
