package zw.gov.mohcc.impilo.clinical.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;

/**
 * Java binding for {@code clinical.rule_definitions} (existed in V001 with zero code
 * bindings — OF-B3 activates it as the versioned-rules governance seam). Executable
 * logic remains in {@code ClinicalRulesEngine}; these rows govern the METADATA of a
 * rule: severity, interruptive/override posture, and the effective window that
 * enables or retires it. A licensed interaction/dose database later plugs in behind
 * the same code-keyed governance without re-architecture.
 */
@Entity
@Table(schema = "clinical", name = "rule_definitions")
public class RuleDefinitionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "code", nullable = false, unique = true)
    private String code;

    @Column(name = "rule_type")
    private String ruleType;

    @Column(name = "logic_expression")
    private String logicExpression;

    @Column(name = "severity")
    private String severity;

    @Column(name = "message_template")
    private String messageTemplate;

    @Column(name = "interruptive")
    private Boolean interruptive;

    @Column(name = "override_allowed")
    private Boolean overrideAllowed;

    @Column(name = "effective_start")
    private LocalDate effectiveStart;

    @Column(name = "effective_end")
    private LocalDate effectiveEnd;

    @Column(name = "version")
    private Integer version;

    @Column(name = "source_refs_json")
    private String sourceRefsJson;

    /** Active today: within [effectiveStart, effectiveEnd] (open-ended when null). */
    public boolean activeOn(LocalDate date) {
        boolean started = effectiveStart == null || !date.isBefore(effectiveStart);
        boolean notEnded = effectiveEnd == null || !date.isAfter(effectiveEnd);
        return started && notEnded;
    }

    public Long getId() { return id; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getRuleType() { return ruleType; }
    public void setRuleType(String ruleType) { this.ruleType = ruleType; }
    public String getLogicExpression() { return logicExpression; }
    public void setLogicExpression(String logicExpression) { this.logicExpression = logicExpression; }
    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }
    public String getMessageTemplate() { return messageTemplate; }
    public void setMessageTemplate(String messageTemplate) { this.messageTemplate = messageTemplate; }
    public Boolean getInterruptive() { return interruptive; }
    public void setInterruptive(Boolean interruptive) { this.interruptive = interruptive; }
    public Boolean getOverrideAllowed() { return overrideAllowed; }
    public void setOverrideAllowed(Boolean overrideAllowed) { this.overrideAllowed = overrideAllowed; }
    public LocalDate getEffectiveStart() { return effectiveStart; }
    public void setEffectiveStart(LocalDate effectiveStart) { this.effectiveStart = effectiveStart; }
    public LocalDate getEffectiveEnd() { return effectiveEnd; }
    public void setEffectiveEnd(LocalDate effectiveEnd) { this.effectiveEnd = effectiveEnd; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
    public String getSourceRefsJson() { return sourceRefsJson; }
    public void setSourceRefsJson(String sourceRefsJson) { this.sourceRefsJson = sourceRefsJson; }
}
