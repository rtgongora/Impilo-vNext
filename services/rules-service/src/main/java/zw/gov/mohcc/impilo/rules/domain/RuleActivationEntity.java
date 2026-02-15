package zw.gov.mohcc.impilo.rules.domain;

import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "rs_rule_activations")
public class RuleActivationEntity {

    @Id
    @Column(name = "id", length = 36, nullable = false)
    private String id;

    @Column(name = "rule_id", length = 36, nullable = false)
    private String ruleId;

    @Column(name = "version_id", length = 36, nullable = false)
    private String versionId;

    @Column(name = "active_from", nullable = false)
    private OffsetDateTime activeFrom;

    @Column(name = "active_to")
    private OffsetDateTime activeTo;

    @PrePersist
    protected void onCreate() {
        if (id == null) id = UUID.randomUUID().toString();
        if (activeFrom == null) activeFrom = OffsetDateTime.now();
    }

    // --- Getters and Setters ---

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getRuleId() { return ruleId; }
    public void setRuleId(String ruleId) { this.ruleId = ruleId; }

    public String getVersionId() { return versionId; }
    public void setVersionId(String versionId) { this.versionId = versionId; }

    public OffsetDateTime getActiveFrom() { return activeFrom; }
    public void setActiveFrom(OffsetDateTime activeFrom) { this.activeFrom = activeFrom; }

    public OffsetDateTime getActiveTo() { return activeTo; }
    public void setActiveTo(OffsetDateTime activeTo) { this.activeTo = activeTo; }
}
