package zw.gov.mohcc.impilo.varapi.persistence.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "provider_identifiers", schema = "varapi",
        uniqueConstraints = @UniqueConstraint(columnNames = {"identifier_system", "identifier_value"}))
public class ProviderIdentifierEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "provider_id", nullable = false)
    private ProviderEntity provider;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "identifier_system", length = 255)
    private String identifierSystem;

    @Column(name = "identifier_value", length = 255)
    private String identifierValue;

    @Column(name = "issuing_council_id")
    private Long issuingCouncilId;

    @Column(name = "status", length = 20)
    private String status;

    @Column(name = "issued_date")
    private LocalDate issuedDate;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }

    // Convenience methods

    public boolean isActive() {
        return "ACTIVE".equals(status);
    }

    public boolean isExpired() {
        return expiryDate != null && LocalDate.now().isAfter(expiryDate);
    }

    // Getters and setters

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public ProviderEntity getProvider() { return provider; }
    public void setProvider(ProviderEntity provider) { this.provider = provider; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public String getIdentifierSystem() { return identifierSystem; }
    public void setIdentifierSystem(String identifierSystem) { this.identifierSystem = identifierSystem; }

    public String getIdentifierValue() { return identifierValue; }
    public void setIdentifierValue(String identifierValue) { this.identifierValue = identifierValue; }

    public Long getIssuingCouncilId() { return issuingCouncilId; }
    public void setIssuingCouncilId(Long issuingCouncilId) { this.issuingCouncilId = issuingCouncilId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public LocalDate getIssuedDate() { return issuedDate; }
    public void setIssuedDate(LocalDate issuedDate) { this.issuedDate = issuedDate; }

    public LocalDate getExpiryDate() { return expiryDate; }
    public void setExpiryDate(LocalDate expiryDate) { this.expiryDate = expiryDate; }

    public Instant getCreatedAt() { return createdAt; }
}
