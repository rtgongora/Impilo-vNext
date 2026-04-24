package zw.gov.mohcc.impilo.varapi.persistence.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "provider_council_status_histories", schema = "varapi")
public class ProviderCouncilStandingHistoryEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "tenant_id", nullable = false) private UUID tenantId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "provider_id") private ProviderEntity provider;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "council_id") private CouncilEntity council;
    @Column(name = "previous_status", length = 50) private String previousStatus;
    @Column(name = "new_status", nullable = false, length = 50) private String newStatus;
    @Column(name = "reason", columnDefinition = "TEXT") private String reason;
    @Column(name = "changed_by", length = 255) private String changedBy;
    @Column(name = "changed_at", nullable = false) private Instant changedAt = Instant.now();
    @Column(name = "authority_context", length = 255) private String authorityContext;
    @Column(name = "metadata", columnDefinition = "JSONB") private String metadata;
    public Long getId() { return id; } public void setId(Long id) { this.id = id; }
    public UUID getTenantId() { return tenantId; } public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public ProviderEntity getProvider() { return provider; } public void setProvider(ProviderEntity provider) { this.provider = provider; }
    public CouncilEntity getCouncil() { return council; } public void setCouncil(CouncilEntity council) { this.council = council; }
    public String getPreviousStatus() { return previousStatus; } public void setPreviousStatus(String previousStatus) { this.previousStatus = previousStatus; }
    public String getNewStatus() { return newStatus; } public void setNewStatus(String newStatus) { this.newStatus = newStatus; }
    public String getReason() { return reason; } public void setReason(String reason) { this.reason = reason; }
    public String getChangedBy() { return changedBy; } public void setChangedBy(String changedBy) { this.changedBy = changedBy; }
    public Instant getChangedAt() { return changedAt; } public void setChangedAt(Instant changedAt) { this.changedAt = changedAt; }
    public String getAuthorityContext() { return authorityContext; } public void setAuthorityContext(String authorityContext) { this.authorityContext = authorityContext; }
    public String getMetadata() { return metadata; } public void setMetadata(String metadata) { this.metadata = metadata; }
}
