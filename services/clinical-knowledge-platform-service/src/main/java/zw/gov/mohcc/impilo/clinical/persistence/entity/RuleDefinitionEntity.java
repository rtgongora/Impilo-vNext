package zw.gov.mohcc.impilo.clinical.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Java binding for {@code clinical.rule_definitions} (existed in V001 with zero code
 * bindings — OF-B3 activates it as the versioned-rules governance seam). Executable
 * logic remains in {@code ClinicalRulesEngine}; these rows govern the METADATA of a
 * rule: severity, interruptive/override posture, and the effective window that
 * enables or retires it. A licensed interaction/dose database later plugs in behind
 * the same code-keyed governance without re-architecture.
 *
 * <p>Column set mirrors the V001 DDL exactly (this service boots with
 * {@code ddl-auto: validate} — the mapping must match the live schema).</p>
 */
@Entity
@Table(schema = "clinical", name = "rule_definitions")
public class RuleDefinitionEntity {

    @Id
    @Column(name = "id")
    private UUID id = UUID.randomUUID();

    @Column(name = "code", nullable = false, unique = true, length = 128)
    private String code;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "rule_type", nullable = false, length = 64)
    private String ruleType;

    @Column(name = "logic_expression")
    private String logicExpression;

    @Column(name = "severity", nullable = false, length = 32)
    private String severity;

    @Column(name = "message_template", nullable = false)
    private String messageTemplate;

    @Column(name = "interruptive", nullable = false)
    private Boolean interruptive;

    @Column(name = "override_allowed", nullable = false)
    private Boolean overrideAllowed;

    @Column(name = "effective_start", nullable = false)
    private LocalDate effectiveStart;

    @Column(name = "effective_end")
    private LocalDate effectiveEnd;

    @Column(name = "version", nullable = false)
    private Integer version;

    /** Active today: within [effectiveStart, effectiveEnd] (open-ended when null). */
    public boolean activeOn(LocalDate date) {
        boolean started = effectiveStart == null || !date.isBefore(effectiveStart);
        boolean notEnded = effectiveEnd == null || !date.isAfter(effectiveEnd);
        return started && notEnded;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
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
}
