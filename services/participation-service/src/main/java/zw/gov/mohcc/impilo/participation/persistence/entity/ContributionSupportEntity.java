package zw.gov.mohcc.impilo.participation.persistence.entity;

import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * A single "support this idea" vote (table {@code participation.contribution_support}).
 * The {@code supporterKey} is an opaque anonymous key or an authenticated actor ref;
 * a supporter may support a given contribution at most once (unique constraint).
 */
@Entity
@Table(name = "contribution_support", schema = "participation")
public class ContributionSupportEntity {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "contribution_id", nullable = false)
    private UUID contributionId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "supporter_key", nullable = false, length = 128)
    private String supporterKey;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getContributionId() { return contributionId; }
    public void setContributionId(UUID contributionId) { this.contributionId = contributionId; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public String getSupporterKey() { return supporterKey; }
    public void setSupporterKey(String supporterKey) { this.supporterKey = supporterKey; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
