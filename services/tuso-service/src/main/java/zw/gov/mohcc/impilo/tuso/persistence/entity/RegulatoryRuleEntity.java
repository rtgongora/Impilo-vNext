package zw.gov.mohcc.impilo.tuso.persistence.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "regulatory_rule", schema = "tuso")
public class RegulatoryRuleEntity {

    @Id
    @Column(name = "rule_id", nullable = false, updatable = false)
    private UUID ruleId;

    @Column(name = "code", nullable = false, length = 128)
    private String code;

    @Column(name = "version", nullable = false)
    private Integer version = 1;

    @Column(name = "owner", length = 64)
    private String owner;

    @Column(name = "rule_kind", nullable = false, length = 32)
    private String ruleKind = "PRINCIPLE";

    @Column(name = "statement", nullable = false, columnDefinition = "text")
    private String statement;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "value_json", columnDefinition = "jsonb")
    private Map<String, Object> valueJson;

    @Column(name = "implementation", length = 128)
    private String implementation;

    @Column(name = "effective_from")
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @Column(name = "status", nullable = false, length = 64)
    private String status = "PENDING_REGULATOR_APPROVAL";

    @Column(name = "source_manual", length = 64)
    private String sourceManual;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "source_pages", columnDefinition = "jsonb")
    private List<Integer> sourcePages;

    @Column(name = "approved_by", length = 255)
    private String approvedBy;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (ruleId == null) {
            ruleId = UUID.randomUUID();
        }
        createdAt = Instant.now();
    }

    public UUID getRuleId() { return ruleId; }
    public void setRuleId(UUID ruleId) { this.ruleId = ruleId; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
    public String getOwner() { return owner; }
    public void setOwner(String owner) { this.owner = owner; }
    public String getRuleKind() { return ruleKind; }
    public void setRuleKind(String ruleKind) { this.ruleKind = ruleKind; }
    public String getStatement() { return statement; }
    public void setStatement(String statement) { this.statement = statement; }
    public Map<String, Object> getValueJson() { return valueJson; }
    public void setValueJson(Map<String, Object> valueJson) { this.valueJson = valueJson; }
    public String getImplementation() { return implementation; }
    public void setImplementation(String implementation) { this.implementation = implementation; }
    public LocalDate getEffectiveFrom() { return effectiveFrom; }
    public void setEffectiveFrom(LocalDate effectiveFrom) { this.effectiveFrom = effectiveFrom; }
    public LocalDate getEffectiveTo() { return effectiveTo; }
    public void setEffectiveTo(LocalDate effectiveTo) { this.effectiveTo = effectiveTo; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getSourceManual() { return sourceManual; }
    public void setSourceManual(String sourceManual) { this.sourceManual = sourceManual; }
    public List<Integer> getSourcePages() { return sourcePages; }
    public void setSourcePages(List<Integer> sourcePages) { this.sourcePages = sourcePages; }
    public String getApprovedBy() { return approvedBy; }
    public void setApprovedBy(String approvedBy) { this.approvedBy = approvedBy; }
    public Instant getApprovedAt() { return approvedAt; }
    public void setApprovedAt(Instant approvedAt) { this.approvedAt = approvedAt; }
    public Instant getCreatedAt() { return createdAt; }
}
