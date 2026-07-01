package zw.gov.mohcc.impilo.costa.domain.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

/** A rule-based, explainable budget recommendation. No ML — evidenceJson shows the drivers. */
@Entity
@Table(name = "costa_budget_recommendation")
public class BudgetRecommendationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "recommendation_id", nullable = false, unique = true)
    private UUID recommendationId;

    @Column(name = "budget_id", nullable = false)
    private UUID budgetId;

    @Column(name = "budget_line_id")
    private UUID budgetLineId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "rule_code", nullable = false, length = 32)
    private String ruleCode;

    @Column(name = "severity", nullable = false, length = 8)
    private String severity;

    @Column(name = "title", nullable = false, length = 256)
    private String title;

    @Column(name = "rationale")
    private String rationale;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "evidence_json", nullable = false, columnDefinition = "jsonb")
    private String evidenceJson = "{}";

    @Column(name = "suggested_action", length = 256)
    private String suggestedAction;

    @Column(name = "status", nullable = false, length = 12)
    private String status = "OPEN";

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (recommendationId == null) recommendationId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() { updatedAt = OffsetDateTime.now(); }

    public Long getId() { return id; }
    public UUID getRecommendationId() { return recommendationId; }
    public void setRecommendationId(UUID recommendationId) { this.recommendationId = recommendationId; }
    public UUID getBudgetId() { return budgetId; }
    public void setBudgetId(UUID budgetId) { this.budgetId = budgetId; }
    public UUID getBudgetLineId() { return budgetLineId; }
    public void setBudgetLineId(UUID budgetLineId) { this.budgetLineId = budgetLineId; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public String getRuleCode() { return ruleCode; }
    public void setRuleCode(String ruleCode) { this.ruleCode = ruleCode; }
    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getRationale() { return rationale; }
    public void setRationale(String rationale) { this.rationale = rationale; }
    public String getEvidenceJson() { return evidenceJson; }
    public void setEvidenceJson(String evidenceJson) { this.evidenceJson = evidenceJson; }
    public String getSuggestedAction() { return suggestedAction; }
    public void setSuggestedAction(String suggestedAction) { this.suggestedAction = suggestedAction; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
