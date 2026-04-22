package zw.gov.mohcc.impilo.indawo.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "ind_site_status_history")
public class SiteStatusHistoryEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "site_id", nullable = false)
    private UUID siteId;

    @Column(name = "previous_status", length = 64)
    private String previousStatus;

    @Column(name = "new_status", nullable = false, length = 64)
    private String newStatus;

    @Column(name = "reason", columnDefinition = "TEXT")
    private String reason;

    @Column(name = "changed_by", length = 255)
    private String changedBy;

    @Column(name = "changed_at", nullable = false)
    private OffsetDateTime changedAt;

    @Column(name = "authority_ctx", columnDefinition = "JSONB")
    private String authorityCtxJson;

    protected SiteStatusHistoryEntity() {}

    public SiteStatusHistoryEntity(UUID id, UUID tenantId, UUID siteId, String previousStatus, String newStatus, String reason, String changedBy) {
        this.id = id;
        this.tenantId = tenantId;
        this.siteId = siteId;
        this.previousStatus = previousStatus;
        this.newStatus = newStatus;
        this.reason = reason;
        this.changedBy = changedBy;
        this.changedAt = OffsetDateTime.now();
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getSiteId() { return siteId; }
    public String getPreviousStatus() { return previousStatus; }
    public String getNewStatus() { return newStatus; }
    public String getReason() { return reason; }
    public String getChangedBy() { return changedBy; }
    public OffsetDateTime getChangedAt() { return changedAt; }
    public String getAuthorityCtxJson() { return authorityCtxJson; }
    public void setAuthorityCtxJson(String authorityCtxJson) { this.authorityCtxJson = authorityCtxJson; }
}

