package zw.gov.mohcc.impilo.varapi.persistence.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "provider_council_cpd_profiles", schema = "varapi")
public class ProviderCouncilCpdProfileEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "tenant_id", nullable = false) private UUID tenantId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "provider_id") private ProviderEntity provider;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "council_id") private CouncilEntity council;
    @Column(name = "cycle_start", nullable = false) private LocalDate cycleStart;
    @Column(name = "cycle_end", nullable = false) private LocalDate cycleEnd;
    @Column(name = "required_credits_or_units", nullable = false) private int requiredCreditsOrUnits;
    @Column(name = "earned_credits_or_units", nullable = false) private int earnedCreditsOrUnits;
    @Column(name = "compliance_status", nullable = false, length = 40) private String complianceStatus = "NOT_STARTED";
    @Column(name = "last_calculated_at") private Instant lastCalculatedAt;
    @Column(name = "metadata", columnDefinition = "JSONB") private String metadata;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @PrePersist void pc() { Instant n = Instant.now(); createdAt = n; updatedAt = n; }
    @PreUpdate void pu() { updatedAt = Instant.now(); }
    public Long getId() { return id; } public void setId(Long id) { this.id = id; }
    public UUID getTenantId() { return tenantId; } public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public ProviderEntity getProvider() { return provider; } public void setProvider(ProviderEntity provider) { this.provider = provider; }
    public CouncilEntity getCouncil() { return council; } public void setCouncil(CouncilEntity council) { this.council = council; }
    public LocalDate getCycleStart() { return cycleStart; } public void setCycleStart(LocalDate cycleStart) { this.cycleStart = cycleStart; }
    public LocalDate getCycleEnd() { return cycleEnd; } public void setCycleEnd(LocalDate cycleEnd) { this.cycleEnd = cycleEnd; }
    public int getRequiredCreditsOrUnits() { return requiredCreditsOrUnits; } public void setRequiredCreditsOrUnits(int requiredCreditsOrUnits) { this.requiredCreditsOrUnits = requiredCreditsOrUnits; }
    public int getEarnedCreditsOrUnits() { return earnedCreditsOrUnits; } public void setEarnedCreditsOrUnits(int earnedCreditsOrUnits) { this.earnedCreditsOrUnits = earnedCreditsOrUnits; }
    public String getComplianceStatus() { return complianceStatus; } public void setComplianceStatus(String complianceStatus) { this.complianceStatus = complianceStatus; }
    public Instant getLastCalculatedAt() { return lastCalculatedAt; } public void setLastCalculatedAt(Instant lastCalculatedAt) { this.lastCalculatedAt = lastCalculatedAt; }
    public String getMetadata() { return metadata; } public void setMetadata(String metadata) { this.metadata = metadata; }
    public Instant getCreatedAt() { return createdAt; } public Instant getUpdatedAt() { return updatedAt; }
}
