package zw.gov.mohcc.impilo.rules.domain;

import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "rs_rule_versions")
public class RuleVersionEntity {

    @Id
    @Column(name = "id", length = 36, nullable = false)
    private String id;

    @Column(name = "rule_id", length = 36, nullable = false)
    private String ruleId;

    @Column(name = "version", nullable = false)
    private int version;

    @Column(name = "dsl_text", columnDefinition = "TEXT", nullable = false)
    private String dslText;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (id == null) id = UUID.randomUUID().toString();
        if (createdAt == null) createdAt = OffsetDateTime.now();
    }

    // --- Getters and Setters ---

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getRuleId() { return ruleId; }
    public void setRuleId(String ruleId) { this.ruleId = ruleId; }

    public int getVersion() { return version; }
    public void setVersion(int version) { this.version = version; }

    public String getDslText() { return dslText; }
    public void setDslText(String dslText) { this.dslText = dslText; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
}
