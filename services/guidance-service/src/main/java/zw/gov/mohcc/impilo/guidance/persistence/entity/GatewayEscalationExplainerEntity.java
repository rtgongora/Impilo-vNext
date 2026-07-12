package zw.gov.mohcc.impilo.guidance.persistence.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * A gateway trust-escalation explainer (V012). Nompilo-mediated escalation copy for a
 * gateway step (gateway doctrine §9): why the escalation is needed, what level of
 * verification is required, what happens next (progress saved), and how to get help.
 * Served on the public lane — the seeded copy must never contain PII, internal service
 * names, or security internals.
 */
@Entity
@Table(name = "gateway_escalation_explainer", schema = "guidance")
public class GatewayEscalationExplainerEntity {

    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false) private String tenantId = "national-spine";
    @Column(name = "step_key", nullable = false, length = 128) private String stepKey;
    @Column(length = 64) private String pillar;
    @Column(name = "rung_from", nullable = false, length = 8) private String rungFrom;
    @Column(name = "rung_to", nullable = false, length = 8) private String rungTo;

    @Column(nullable = false) private String title;
    @Column(nullable = false, columnDefinition = "TEXT") private String why;
    @Column(name = "level_label", nullable = false, length = 512) private String levelLabel;
    @Column(name = "what_next", nullable = false, columnDefinition = "TEXT") private String whatNext;
    @Column(name = "how_to_get_help", nullable = false, columnDefinition = "TEXT") private String howToGetHelp;
    @Column(name = "continue_without_label", length = 128) private String continueWithoutLabel;
    @Column(nullable = false, length = 16) private String status = "ACTIVE";

    @Column(name = "created_at", nullable = false, updatable = false) private OffsetDateTime createdAt;
    @Column(name = "updated_at", nullable = false) private OffsetDateTime updatedAt;

    @PrePersist
    void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    public UUID getId() { return id; }
    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
    public String getStepKey() { return stepKey; }
    public void setStepKey(String stepKey) { this.stepKey = stepKey; }
    public String getPillar() { return pillar; }
    public void setPillar(String pillar) { this.pillar = pillar; }
    public String getRungFrom() { return rungFrom; }
    public void setRungFrom(String rungFrom) { this.rungFrom = rungFrom; }
    public String getRungTo() { return rungTo; }
    public void setRungTo(String rungTo) { this.rungTo = rungTo; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getWhy() { return why; }
    public void setWhy(String why) { this.why = why; }
    public String getLevelLabel() { return levelLabel; }
    public void setLevelLabel(String levelLabel) { this.levelLabel = levelLabel; }
    public String getWhatNext() { return whatNext; }
    public void setWhatNext(String whatNext) { this.whatNext = whatNext; }
    public String getHowToGetHelp() { return howToGetHelp; }
    public void setHowToGetHelp(String howToGetHelp) { this.howToGetHelp = howToGetHelp; }
    public String getContinueWithoutLabel() { return continueWithoutLabel; }
    public void setContinueWithoutLabel(String continueWithoutLabel) { this.continueWithoutLabel = continueWithoutLabel; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
