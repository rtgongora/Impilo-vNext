package zw.gov.mohcc.impilo.indawo.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "ind_site_enforcement_cases")
public class SiteEnforcementCaseEntity {

    @Id
    @Column(name = "case_id", nullable = false)
    private UUID caseId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "site_id", nullable = false)
    private UUID siteId;

    @Column(name = "trigger_type", nullable = false, length = 64)
    private String triggerType;

    @Column(name = "status", nullable = false, length = 32)
    private String status = "OPEN";

    @Column(name = "recommendation", length = 128)
    private String recommendation;

    @Column(name = "authority_route", length = 128)
    private String authorityRoute;

    @Column(name = "linked_refs", columnDefinition = "JSONB")
    private String linkedRefsJson;

    @Column(name = "closure_reason", columnDefinition = "TEXT")
    private String closureReason;

    @Column(name = "decision_details", columnDefinition = "JSONB")
    private String decisionDetailsJson;

    @Column(name = "opened_at", nullable = false)
    private OffsetDateTime openedAt;

    @Column(name = "closed_at")
    private OffsetDateTime closedAt;

    protected SiteEnforcementCaseEntity() {}

    public SiteEnforcementCaseEntity(UUID caseId, UUID tenantId, UUID siteId, String triggerType) {
        this.caseId = caseId;
        this.tenantId = tenantId;
        this.siteId = siteId;
        this.triggerType = triggerType;
        this.openedAt = OffsetDateTime.now();
    }

    public UUID getCaseId() { return caseId; }
    public UUID getTenantId() { return tenantId; }
    public UUID getSiteId() { return siteId; }
    public String getTriggerType() { return triggerType; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getRecommendation() { return recommendation; }
    public void setRecommendation(String recommendation) { this.recommendation = recommendation; }
    public String getAuthorityRoute() { return authorityRoute; }
    public void setAuthorityRoute(String authorityRoute) { this.authorityRoute = authorityRoute; }
    public String getLinkedRefsJson() { return linkedRefsJson; }
    public void setLinkedRefsJson(String linkedRefsJson) { this.linkedRefsJson = linkedRefsJson; }
    public String getClosureReason() { return closureReason; }
    public void setClosureReason(String closureReason) { this.closureReason = closureReason; }
    public String getDecisionDetailsJson() { return decisionDetailsJson; }
    public void setDecisionDetailsJson(String decisionDetailsJson) { this.decisionDetailsJson = decisionDetailsJson; }
    public OffsetDateTime getOpenedAt() { return openedAt; }
    public OffsetDateTime getClosedAt() { return closedAt; }
    public void close(String reason) { this.status = "CLOSED"; this.closureReason = reason; this.closedAt = OffsetDateTime.now(); }
}

