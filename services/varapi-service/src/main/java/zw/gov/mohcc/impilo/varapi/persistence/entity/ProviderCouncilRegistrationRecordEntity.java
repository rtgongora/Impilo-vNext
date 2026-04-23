package zw.gov.mohcc.impilo.varapi.persistence.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "provider_council_registration_records", schema = "varapi")
public class ProviderCouncilRegistrationRecordEntity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "tenant_id", nullable = false) private UUID tenantId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "provider_id") private ProviderEntity provider;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "council_id") private CouncilEntity council;
    @Column(name = "registration_number", length = 120) private String registrationNumber;
    @Column(name = "registration_category", length = 80) private String registrationCategory;
    @Column(name = "registration_date") private LocalDate registrationDate;
    @Column(name = "status", nullable = false, length = 40) private String status = "REGISTERED";
    @Column(name = "issued_under_authority", length = 255) private String issuedUnderAuthority;
    @Column(name = "expiry_date") private LocalDate expiryDate;
    @Column(name = "source_reference", length = 255) private String sourceReference;
    @Column(name = "metadata", columnDefinition = "JSONB") private String metadata;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    @PrePersist void pc() { Instant n = Instant.now(); createdAt = n; updatedAt = n; }
    @PreUpdate void pu() { updatedAt = Instant.now(); }

    public Long getId() { return id; } public void setId(Long id) { this.id = id; }
    public UUID getTenantId() { return tenantId; } public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public ProviderEntity getProvider() { return provider; } public void setProvider(ProviderEntity provider) { this.provider = provider; }
    public CouncilEntity getCouncil() { return council; } public void setCouncil(CouncilEntity council) { this.council = council; }
    public String getRegistrationNumber() { return registrationNumber; } public void setRegistrationNumber(String registrationNumber) { this.registrationNumber = registrationNumber; }
    public String getRegistrationCategory() { return registrationCategory; } public void setRegistrationCategory(String registrationCategory) { this.registrationCategory = registrationCategory; }
    public LocalDate getRegistrationDate() { return registrationDate; } public void setRegistrationDate(LocalDate registrationDate) { this.registrationDate = registrationDate; }
    public String getStatus() { return status; } public void setStatus(String status) { this.status = status; }
    public String getIssuedUnderAuthority() { return issuedUnderAuthority; } public void setIssuedUnderAuthority(String issuedUnderAuthority) { this.issuedUnderAuthority = issuedUnderAuthority; }
    public LocalDate getExpiryDate() { return expiryDate; } public void setExpiryDate(LocalDate expiryDate) { this.expiryDate = expiryDate; }
    public String getSourceReference() { return sourceReference; } public void setSourceReference(String sourceReference) { this.sourceReference = sourceReference; }
    public String getMetadata() { return metadata; } public void setMetadata(String metadata) { this.metadata = metadata; }
    public Instant getCreatedAt() { return createdAt; } public Instant getUpdatedAt() { return updatedAt; }
}
