package zw.gov.mohcc.impilo.simba.persistence.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Deterministic risk-scoring rule (config, not runtime). Read by {@code RiskScoringService} to
 * evaluate assessment responses into a risk band. No fake AI — pure table-driven thresholds.
 */
@Entity
@Table(name = "simba_risk_rule", schema = "simba")
public class RiskRuleEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "rule_id", nullable = false, unique = true)
    private UUID ruleId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "rule_code", nullable = false, length = 64)
    private String ruleCode;

    @Column(name = "template_code", nullable = false, length = 64)
    private String templateCode;

    @Column(name = "question_code", nullable = false, length = 64)
    private String questionCode;

    @Column(name = "operator", nullable = false, length = 8)
    private String operator;

    @Column(name = "threshold_low")
    private BigDecimal thresholdLow;

    @Column(name = "threshold_high")
    private BigDecimal thresholdHigh;

    @Column(name = "match_values", length = 255)
    private String matchValues;

    @Column(name = "contributes_band", nullable = false, length = 32)
    private String contributesBand;

    @Column(name = "weight", nullable = false)
    private BigDecimal weight = BigDecimal.ONE;

    @Column(name = "red_flag", nullable = false)
    private boolean redFlag = false;

    @Column(name = "rationale", length = 255)
    private String rationale;

    @Column(name = "status", nullable = false, length = 16)
    private String status = "ACTIVE";

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (ruleId == null) {
            ruleId = UUID.randomUUID();
        }
        createdAt = OffsetDateTime.now();
    }

    public Long getId() { return id; }
    public UUID getRuleId() { return ruleId; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public String getRuleCode() { return ruleCode; }
    public void setRuleCode(String ruleCode) { this.ruleCode = ruleCode; }
    public String getTemplateCode() { return templateCode; }
    public void setTemplateCode(String templateCode) { this.templateCode = templateCode; }
    public String getQuestionCode() { return questionCode; }
    public void setQuestionCode(String questionCode) { this.questionCode = questionCode; }
    public String getOperator() { return operator; }
    public void setOperator(String operator) { this.operator = operator; }
    public BigDecimal getThresholdLow() { return thresholdLow; }
    public void setThresholdLow(BigDecimal thresholdLow) { this.thresholdLow = thresholdLow; }
    public BigDecimal getThresholdHigh() { return thresholdHigh; }
    public void setThresholdHigh(BigDecimal thresholdHigh) { this.thresholdHigh = thresholdHigh; }
    public String getMatchValues() { return matchValues; }
    public void setMatchValues(String matchValues) { this.matchValues = matchValues; }
    public String getContributesBand() { return contributesBand; }
    public void setContributesBand(String contributesBand) { this.contributesBand = contributesBand; }
    public BigDecimal getWeight() { return weight; }
    public void setWeight(BigDecimal weight) { this.weight = weight; }
    public boolean isRedFlag() { return redFlag; }
    public void setRedFlag(boolean redFlag) { this.redFlag = redFlag; }
    public String getRationale() { return rationale; }
    public void setRationale(String rationale) { this.rationale = rationale; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
