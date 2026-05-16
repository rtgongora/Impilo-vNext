package zw.gov.mohcc.impilo.surv.persistence.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "intelligence_rule_evaluation", schema = "surv")
public class IntelligenceRuleEvaluationEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;
    @Column(name = "rule_id", nullable = false)
    private Long ruleId;
    @Column(name = "evaluation_mode", nullable = false)
    private String evaluationMode;
    @Column(name = "evaluation_status", nullable = false)
    private String evaluationStatus;
    @Column(name = "triggered", nullable = false)
    private boolean triggered;
    @Column(name = "details")
    private String details;
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void prePersist() { createdAt = OffsetDateTime.now(); }

    public Long getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public Long getRuleId() { return ruleId; }
    public void setRuleId(Long ruleId) { this.ruleId = ruleId; }
    public String getEvaluationMode() { return evaluationMode; }
    public void setEvaluationMode(String evaluationMode) { this.evaluationMode = evaluationMode; }
    public String getEvaluationStatus() { return evaluationStatus; }
    public void setEvaluationStatus(String evaluationStatus) { this.evaluationStatus = evaluationStatus; }
    public boolean isTriggered() { return triggered; }
    public void setTriggered(boolean triggered) { this.triggered = triggered; }
    public String getDetails() { return details; }
    public void setDetails(String details) { this.details = details; }
}
