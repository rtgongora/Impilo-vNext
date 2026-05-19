package zw.gov.mohcc.impilo.llm.core;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "llm_interaction_log", schema = "llm")
public class LlmAuditEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id")
    private UUID tenantId;
    @Column(name = "actor_id")
    private String actorId;
    @Column(name = "provider", nullable = false)
    private String provider;
    @Column(name = "model", nullable = false)
    private String model;
    @Column(name = "use_case")
    private String useCase;
    @Column(name = "risk_level")
    private String riskLevel;
    @Column(name = "input_hash")
    private String inputHash;
    @Column(name = "output_hash")
    private String outputHash;
    @Column(name = "fallback_used", nullable = false)
    private boolean fallbackUsed;
    @Column(name = "requires_human_approval", nullable = false)
    private boolean requiresHumanApproval;
    @Column(name = "latency_ms", nullable = false)
    private long latencyMs;
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void prePersist() {
        createdAt = OffsetDateTime.now();
    }

    public Long getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public String getActorId() { return actorId; }
    public void setActorId(String actorId) { this.actorId = actorId; }
    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public String getUseCase() { return useCase; }
    public void setUseCase(String useCase) { this.useCase = useCase; }
    public String getRiskLevel() { return riskLevel; }
    public void setRiskLevel(String riskLevel) { this.riskLevel = riskLevel; }
    public String getInputHash() { return inputHash; }
    public void setInputHash(String inputHash) { this.inputHash = inputHash; }
    public String getOutputHash() { return outputHash; }
    public void setOutputHash(String outputHash) { this.outputHash = outputHash; }
    public boolean isFallbackUsed() { return fallbackUsed; }
    public void setFallbackUsed(boolean fallbackUsed) { this.fallbackUsed = fallbackUsed; }
    public boolean isRequiresHumanApproval() { return requiresHumanApproval; }
    public void setRequiresHumanApproval(boolean requiresHumanApproval) { this.requiresHumanApproval = requiresHumanApproval; }
    public long getLatencyMs() { return latencyMs; }
    public void setLatencyMs(long latencyMs) { this.latencyMs = latencyMs; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
