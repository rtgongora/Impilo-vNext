package zw.gov.mohcc.impilo.tuso.persistence.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

/**
 * A versioned classification mapping rule. Persisted from a classpath rule pack
 * with the {@code InspectionContentSeeder} discipline: immutable per
 * {@code (code, version)}, checksum-guarded; content changes require a new version.
 */
@Entity
@Table(name = "facility_classification_mapping_rule", schema = "tuso")
public class FacilityClassificationMappingRuleEntity {

    @Id
    @Column(name = "rule_id", nullable = false, updatable = false)
    private UUID ruleId;

    @Column(name = "code", nullable = false, length = 96)
    private String code;

    @Column(name = "version", nullable = false)
    private Integer version = 1;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "rule_json", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> ruleJson;

    @Column(name = "content_checksum", nullable = false, length = 96)
    private String contentChecksum;

    @Column(name = "status", nullable = false, length = 32)
    private String status = "DRAFT";

    @Column(name = "source_doc", length = 64)
    private String sourceDoc;

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
        if (ruleId == null) ruleId = UUID.randomUUID();
        createdAt = createdAt == null ? Instant.now() : createdAt;
    }

    public UUID getRuleId() { return ruleId; }
    public void setRuleId(UUID ruleId) { this.ruleId = ruleId; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public Map<String, Object> getRuleJson() { return ruleJson; }
    public void setRuleJson(Map<String, Object> ruleJson) { this.ruleJson = ruleJson; }
    public String getContentChecksum() { return contentChecksum; }
    public void setContentChecksum(String contentChecksum) { this.contentChecksum = contentChecksum; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getSourceDoc() { return sourceDoc; }
    public void setSourceDoc(String sourceDoc) { this.sourceDoc = sourceDoc; }
    public String getApprovedBy() { return approvedBy; }
    public void setApprovedBy(String approvedBy) { this.approvedBy = approvedBy; }
    public Instant getApprovedAt() { return approvedAt; }
    public void setApprovedAt(Instant approvedAt) { this.approvedAt = approvedAt; }
    public LocalDate getEffectiveFrom() { return effectiveFrom; }
    public void setEffectiveFrom(LocalDate effectiveFrom) { this.effectiveFrom = effectiveFrom; }
    public Instant getCreatedAt() { return createdAt; }
}
