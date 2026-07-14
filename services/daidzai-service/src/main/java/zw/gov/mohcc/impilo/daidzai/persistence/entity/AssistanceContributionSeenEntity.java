package zw.gov.mohcc.impilo.daidzai.persistence.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Idempotent money-projection marker: one row per mushe contribution event applied to a
 * case's raised total (V003). The mushe contribution id is the primary key, so an event
 * replay can never double-count.
 */
@Entity
@Table(name = "dai_assistance_contribution_seen", schema = "daidzai")
public class AssistanceContributionSeenEntity {

    @Id @Column(name = "contribution_id") private UUID contributionId;
    @Column(name = "assistance_id", nullable = false) private UUID assistanceId;
    @Column(name = "amount", nullable = false, precision = 14, scale = 2) private BigDecimal amount;
    @Column(name = "is_anonymous", nullable = false) private Boolean isAnonymous = Boolean.FALSE;
    @Column(name = "contributor_label", length = 255) private String contributorLabel;
    @Column(name = "message", length = 512) private String message;
    @Column(name = "recorded_at", nullable = false) private OffsetDateTime recordedAt;

    @PrePersist void onCreate() {
        if (recordedAt == null) recordedAt = OffsetDateTime.now();
    }

    public UUID getContributionId() { return contributionId; }
    public void setContributionId(UUID contributionId) { this.contributionId = contributionId; }
    public UUID getAssistanceId() { return assistanceId; }
    public void setAssistanceId(UUID assistanceId) { this.assistanceId = assistanceId; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public Boolean getIsAnonymous() { return isAnonymous; }
    public void setIsAnonymous(Boolean isAnonymous) { this.isAnonymous = isAnonymous; }
    public String getContributorLabel() { return contributorLabel; }
    public void setContributorLabel(String contributorLabel) { this.contributorLabel = contributorLabel; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public OffsetDateTime getRecordedAt() { return recordedAt; }
}
