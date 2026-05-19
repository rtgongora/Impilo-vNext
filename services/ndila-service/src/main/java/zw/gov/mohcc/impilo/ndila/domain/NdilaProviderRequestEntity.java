package zw.gov.mohcc.impilo.ndila.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "ndila_provider_requests")
public class NdilaProviderRequestEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "actor_id", length = 255)
    private String actorId;

    @Column(name = "actor_type", length = 64)
    private String actorType;

    @Column(name = "purpose_of_use", length = 64)
    private String purposeOfUse;

    @Column(name = "use_case", length = 64)
    private String useCase;

    @Column(name = "sensitivity_level", length = 32)
    private String sensitivityLevel;

    @Column(name = "provider_name", nullable = false, length = 64)
    private String providerName;

    @Column(name = "operation_type", nullable = false, length = 48)
    private String operationType;

    @Column(name = "fallback_used", nullable = false)
    private boolean fallbackUsed;

    @Column(name = "served_from_cache", nullable = false)
    private boolean servedFromCache;

    @Column(name = "confidence", precision = 5, scale = 4)
    private BigDecimal confidence;

    @Column(name = "response_status")
    private Integer responseStatus;

    @Column(name = "latency_ms")
    private Integer latencyMs;

    @Column(name = "cost_units", precision = 10, scale = 4)
    private BigDecimal costUnits;

    @Column(name = "correlation_id")
    private UUID correlationId;

    @Column(name = "policy_decision_id", length = 64)
    private String policyDecisionId;

    @Column(name = "pii_minimized", nullable = false)
    private boolean piiMinimized = true;

    @Column(name = "request_summary", columnDefinition = "TEXT")
    private String requestSummary;

    @Column(name = "response_summary", columnDefinition = "TEXT")
    private String responseSummary;

    @Column(name = "requested_at", nullable = false)
    private OffsetDateTime requestedAt = OffsetDateTime.now();

    public NdilaProviderRequestEntity() {}

    public Long getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public String getActorId() { return actorId; }
    public void setActorId(String actorId) { this.actorId = actorId; }
    public String getActorType() { return actorType; }
    public void setActorType(String actorType) { this.actorType = actorType; }
    public String getPurposeOfUse() { return purposeOfUse; }
    public void setPurposeOfUse(String purposeOfUse) { this.purposeOfUse = purposeOfUse; }
    public String getUseCase() { return useCase; }
    public void setUseCase(String useCase) { this.useCase = useCase; }
    public String getSensitivityLevel() { return sensitivityLevel; }
    public void setSensitivityLevel(String sensitivityLevel) { this.sensitivityLevel = sensitivityLevel; }
    public String getProviderName() { return providerName; }
    public void setProviderName(String providerName) { this.providerName = providerName; }
    public String getOperationType() { return operationType; }
    public void setOperationType(String operationType) { this.operationType = operationType; }
    public boolean isFallbackUsed() { return fallbackUsed; }
    public void setFallbackUsed(boolean fallbackUsed) { this.fallbackUsed = fallbackUsed; }
    public boolean isServedFromCache() { return servedFromCache; }
    public void setServedFromCache(boolean servedFromCache) { this.servedFromCache = servedFromCache; }
    public BigDecimal getConfidence() { return confidence; }
    public void setConfidence(BigDecimal confidence) { this.confidence = confidence; }
    public Integer getResponseStatus() { return responseStatus; }
    public void setResponseStatus(Integer responseStatus) { this.responseStatus = responseStatus; }
    public Integer getLatencyMs() { return latencyMs; }
    public void setLatencyMs(Integer latencyMs) { this.latencyMs = latencyMs; }
    public BigDecimal getCostUnits() { return costUnits; }
    public void setCostUnits(BigDecimal costUnits) { this.costUnits = costUnits; }
    public UUID getCorrelationId() { return correlationId; }
    public void setCorrelationId(UUID correlationId) { this.correlationId = correlationId; }
    public String getPolicyDecisionId() { return policyDecisionId; }
    public void setPolicyDecisionId(String policyDecisionId) { this.policyDecisionId = policyDecisionId; }
    public boolean isPiiMinimized() { return piiMinimized; }
    public void setPiiMinimized(boolean piiMinimized) { this.piiMinimized = piiMinimized; }
    public String getRequestSummary() { return requestSummary; }
    public void setRequestSummary(String requestSummary) { this.requestSummary = requestSummary; }
    public String getResponseSummary() { return responseSummary; }
    public void setResponseSummary(String responseSummary) { this.responseSummary = responseSummary; }
    public OffsetDateTime getRequestedAt() { return requestedAt; }
}
