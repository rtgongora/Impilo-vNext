package zw.gov.mohcc.impilo.varapi.persistence.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Provider disciplinary case entity for tracking investigations and outcomes.
 */
@Entity
@Table(name = "provider_disciplinary_cases", schema = "varapi")
public class ProviderDisciplinaryCaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "provider_id", nullable = false)
    private ProviderEntity provider;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "trigger_type", nullable = false, length = 50)
    private String triggerType;

    @Column(name = "status", nullable = false, length = 20)
    private String status = "OPEN";

    // ROM-W7 disciplinary FSM + rito-referral firewall
    @Column(name = "case_stage", nullable = false, length = 40)
    private String caseStage = "RECEIVED";

    @Column(name = "council_id")
    private Long councilId;

    @Column(name = "source_rito_case_id", length = 128)
    private String sourceRitoCaseId;

    @Column(name = "opened_by", length = 128)
    private String openedBy;

    @Column(name = "respondent_notified", nullable = false)
    private Boolean respondentNotified = false;

    @Column(name = "opened_date")
    private LocalDate openedDate;

    @Column(name = "route_to_authority", length = 255)
    private String routeToAuthority;

    @Column(name = "summary", columnDefinition = "TEXT")
    private String summary;

    @Column(name = "linked_evidence", columnDefinition = "JSONB")
    private String linkedEvidence;

    @Column(name = "final_recommendation", columnDefinition = "TEXT")
    private String finalRecommendation;

    @Column(name = "final_decision", length = 50)
    private String finalDecision;

    @Column(name = "final_decision_date")
    private LocalDate finalDecisionDate;

    @Column(name = "version")
    private Integer version = 1;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
        if (openedDate == null) {
            openedDate = LocalDate.now();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    public boolean isOpen() {
        return "OPEN".equals(status);
    }

    public boolean isClosed() {
        return "CLOSED".equals(status);
    }

    // Getters and setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public ProviderEntity getProvider() { return provider; }
    public void setProvider(ProviderEntity provider) { this.provider = provider; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public String getTriggerType() { return triggerType; }
    public void setTriggerType(String triggerType) { this.triggerType = triggerType; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getCaseStage() { return caseStage; }
    public void setCaseStage(String caseStage) { this.caseStage = caseStage; }
    public Long getCouncilId() { return councilId; }
    public void setCouncilId(Long councilId) { this.councilId = councilId; }
    public String getSourceRitoCaseId() { return sourceRitoCaseId; }
    public void setSourceRitoCaseId(String sourceRitoCaseId) { this.sourceRitoCaseId = sourceRitoCaseId; }
    public String getOpenedBy() { return openedBy; }
    public void setOpenedBy(String openedBy) { this.openedBy = openedBy; }
    public Boolean getRespondentNotified() { return respondentNotified; }
    public void setRespondentNotified(Boolean respondentNotified) { this.respondentNotified = respondentNotified; }

    public LocalDate getOpenedDate() { return openedDate; }
    public void setOpenedDate(LocalDate openedDate) { this.openedDate = openedDate; }

    public String getRouteToAuthority() { return routeToAuthority; }
    public void setRouteToAuthority(String routeToAuthority) { this.routeToAuthority = routeToAuthority; }

    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }

    public String getLinkedEvidence() { return linkedEvidence; }
    public void setLinkedEvidence(String linkedEvidence) { this.linkedEvidence = linkedEvidence; }

    public String getFinalRecommendation() { return finalRecommendation; }
    public void setFinalRecommendation(String finalRecommendation) { this.finalRecommendation = finalRecommendation; }

    public String getFinalDecision() { return finalDecision; }
    public void setFinalDecision(String finalDecision) { this.finalDecision = finalDecision; }

    public LocalDate getFinalDecisionDate() { return finalDecisionDate; }
    public void setFinalDecisionDate(LocalDate finalDecisionDate) { this.finalDecisionDate = finalDecisionDate; }

    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }

    public Instant getCreatedAt() { return createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
}
