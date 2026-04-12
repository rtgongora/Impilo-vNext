package zw.gov.mohcc.impilo.air.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "inference_records", schema = "ai_registry")
public class InferenceRecordEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "record_id", nullable = false, unique = true, updatable = false)
    @JdbcTypeCode(SqlTypes.UUID)
    private UUID recordId;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    @JdbcTypeCode(SqlTypes.UUID)
    private UUID tenantId;

    @Column(name = "model_id", nullable = false, updatable = false)
    @JdbcTypeCode(SqlTypes.UUID)
    private UUID modelId;

    @Column(name = "model_version", length = 32)
    private String modelVersion;

    @Column(name = "inference_class", nullable = false, length = 4)
    private String inferenceClass;

    @Column(name = "workflow_ref", length = 128)
    private String workflowRef;

    @Column(name = "subject_type", length = 32)
    private String subjectType;

    @Column(name = "subject_id", length = 128)
    private String subjectId;

    @Column(name = "output_summary", columnDefinition = "TEXT")
    private String outputSummary;

    @Column(precision = 5, scale = 4)
    private BigDecimal score;

    @Column(precision = 5, scale = 4)
    private BigDecimal confidence;

    @Column(name = "human_review_required")
    private Boolean humanReviewRequired = Boolean.TRUE;

    @Column(name = "human_accepted")
    private Boolean humanAccepted;

    @Column(name = "override_reason", columnDefinition = "TEXT")
    private String overrideReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (recordId == null) {
            recordId = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
        if (humanReviewRequired == null) {
            humanReviewRequired = Boolean.TRUE;
        }
    }

    public Long getId() {
        return id;
    }

    public UUID getRecordId() {
        return recordId;
    }

    public void setRecordId(UUID recordId) {
        this.recordId = recordId;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }

    public UUID getModelId() {
        return modelId;
    }

    public void setModelId(UUID modelId) {
        this.modelId = modelId;
    }

    public String getModelVersion() {
        return modelVersion;
    }

    public void setModelVersion(String modelVersion) {
        this.modelVersion = modelVersion;
    }

    public String getInferenceClass() {
        return inferenceClass;
    }

    public void setInferenceClass(String inferenceClass) {
        this.inferenceClass = inferenceClass;
    }

    public String getWorkflowRef() {
        return workflowRef;
    }

    public void setWorkflowRef(String workflowRef) {
        this.workflowRef = workflowRef;
    }

    public String getSubjectType() {
        return subjectType;
    }

    public void setSubjectType(String subjectType) {
        this.subjectType = subjectType;
    }

    public String getSubjectId() {
        return subjectId;
    }

    public void setSubjectId(String subjectId) {
        this.subjectId = subjectId;
    }

    public String getOutputSummary() {
        return outputSummary;
    }

    public void setOutputSummary(String outputSummary) {
        this.outputSummary = outputSummary;
    }

    public BigDecimal getScore() {
        return score;
    }

    public void setScore(BigDecimal score) {
        this.score = score;
    }

    public BigDecimal getConfidence() {
        return confidence;
    }

    public void setConfidence(BigDecimal confidence) {
        this.confidence = confidence;
    }

    public Boolean getHumanReviewRequired() {
        return humanReviewRequired;
    }

    public void setHumanReviewRequired(Boolean humanReviewRequired) {
        this.humanReviewRequired = humanReviewRequired;
    }

    public Boolean getHumanAccepted() {
        return humanAccepted;
    }

    public void setHumanAccepted(Boolean humanAccepted) {
        this.humanAccepted = humanAccepted;
    }

    public String getOverrideReason() {
        return overrideReason;
    }

    public void setOverrideReason(String overrideReason) {
        this.overrideReason = overrideReason;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
