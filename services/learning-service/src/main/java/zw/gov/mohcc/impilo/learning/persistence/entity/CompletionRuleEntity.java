package zw.gov.mohcc.impilo.learning.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Configurable course completion rule (V028) — the {@code completionCriteriaRef}
 * ({@code lrn_completion_rule}) named by the learning-live session template.
 *
 * <p>A course with no rules keeps the legacy implicit behaviour (all required
 * lessons at 100 %). A course with rules only completes when every
 * {@code required} rule is satisfied — see {@code FundoCompletionPolicyService}
 * for the rule-type semantics.</p>
 */
@Entity
@Table(
        name = "lrn_completion_rule",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_lrn_completion_rule",
                columnNames = {"tenant_id", "course_id", "rule_type"}))
public class CompletionRuleEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "course_id", nullable = false)
    private UUID courseId;

    @Column(name = "rule_type", nullable = false, length = 32)
    private String ruleType;

    /** Rule-type-specific threshold; for ATTENDANCE_THRESHOLD this is MINUTES. */
    @Column(name = "threshold_value")
    private BigDecimal thresholdValue;

    @Column(name = "required", nullable = false)
    private boolean required = true;

    @Column(name = "created_by", length = 128)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (id == null) id = UUID.randomUUID();
        if (createdAt == null) createdAt = OffsetDateTime.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public UUID getCourseId() { return courseId; }
    public void setCourseId(UUID courseId) { this.courseId = courseId; }
    public String getRuleType() { return ruleType; }
    public void setRuleType(String ruleType) { this.ruleType = ruleType; }
    public BigDecimal getThresholdValue() { return thresholdValue; }
    public void setThresholdValue(BigDecimal thresholdValue) { this.thresholdValue = thresholdValue; }
    public boolean isRequired() { return required; }
    public void setRequired(boolean required) { this.required = required; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
