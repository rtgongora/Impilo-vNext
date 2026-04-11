package zw.gov.mohcc.impilo.clinical.persistence.entity;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(schema = "clinical", name = "clinical_knowledge_items")
public class ClinicalKnowledgeItemEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 64)
    private String type;

    @Column(name = "canonical_code", length = 128)
    private String canonicalCode;

    @Column(nullable = false, length = 512)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String summary;

    @Column(length = 255)
    private String population;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "eligibility_json", nullable = false, columnDefinition = "jsonb")
    private JsonNode eligibilityJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "logic_json", nullable = false, columnDefinition = "jsonb")
    private JsonNode logicJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "evidence_json", nullable = false, columnDefinition = "jsonb")
    private JsonNode evidenceJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "source_refs_json", nullable = false, columnDefinition = "jsonb")
    private JsonNode sourceRefsJson;

    @Column(name = "effective_start", nullable = false)
    private LocalDate effectiveStart;

    @Column(name = "effective_end")
    private LocalDate effectiveEnd;

    @Column(name = "approval_status", nullable = false, length = 32)
    private String approvalStatus = "APPROVED";

    @Column(nullable = false)
    private Integer version = 1;

    @Column(name = "created_by", length = 255)
    private String createdBy;

    @Column(name = "approved_by", length = 255)
    private String approvedBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getCanonicalCode() {
        return canonicalCode;
    }

    public void setCanonicalCode(String canonicalCode) {
        this.canonicalCode = canonicalCode;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getPopulation() {
        return population;
    }

    public void setPopulation(String population) {
        this.population = population;
    }

    public JsonNode getEligibilityJson() {
        return eligibilityJson;
    }

    public void setEligibilityJson(JsonNode eligibilityJson) {
        this.eligibilityJson = eligibilityJson;
    }

    public JsonNode getLogicJson() {
        return logicJson;
    }

    public void setLogicJson(JsonNode logicJson) {
        this.logicJson = logicJson;
    }

    public JsonNode getEvidenceJson() {
        return evidenceJson;
    }

    public void setEvidenceJson(JsonNode evidenceJson) {
        this.evidenceJson = evidenceJson;
    }

    public JsonNode getSourceRefsJson() {
        return sourceRefsJson;
    }

    public void setSourceRefsJson(JsonNode sourceRefsJson) {
        this.sourceRefsJson = sourceRefsJson;
    }

    public LocalDate getEffectiveStart() {
        return effectiveStart;
    }

    public void setEffectiveStart(LocalDate effectiveStart) {
        this.effectiveStart = effectiveStart;
    }

    public LocalDate getEffectiveEnd() {
        return effectiveEnd;
    }

    public void setEffectiveEnd(LocalDate effectiveEnd) {
        this.effectiveEnd = effectiveEnd;
    }

    public String getApprovalStatus() {
        return approvalStatus;
    }

    public void setApprovalStatus(String approvalStatus) {
        this.approvalStatus = approvalStatus;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public String getApprovedBy() {
        return approvedBy;
    }

    public void setApprovedBy(String approvedBy) {
        this.approvedBy = approvedBy;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
