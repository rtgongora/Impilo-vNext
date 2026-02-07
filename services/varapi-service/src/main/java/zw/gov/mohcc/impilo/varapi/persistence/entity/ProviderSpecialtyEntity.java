package zw.gov.mohcc.impilo.varapi.persistence.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "provider_specialties", schema = "varapi")
public class ProviderSpecialtyEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "provider_id", nullable = false)
    private ProviderEntity provider;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "specialty_code", length = 50)
    private String specialtyCode;

    @Column(name = "specialty_name", length = 255)
    private String specialtyName;

    @Column(name = "subspecialty_code", length = 50)
    private String subspecialtyCode;

    @Column(name = "subspecialty_name", length = 255)
    private String subspecialtyName;

    @Column(name = "zibo_validated")
    private Boolean ziboValidated;

    @Column(name = "primary_specialty")
    private Boolean primarySpecialty;

    @Column(name = "effective_date")
    private LocalDate effectiveDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }

    // Convenience methods

    public boolean isZiboValidated() {
        return Boolean.TRUE.equals(ziboValidated);
    }

    public boolean isPrimarySpecialty() {
        return Boolean.TRUE.equals(primarySpecialty);
    }

    // Getters and setters

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public ProviderEntity getProvider() { return provider; }
    public void setProvider(ProviderEntity provider) { this.provider = provider; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public String getSpecialtyCode() { return specialtyCode; }
    public void setSpecialtyCode(String specialtyCode) { this.specialtyCode = specialtyCode; }

    public String getSpecialtyName() { return specialtyName; }
    public void setSpecialtyName(String specialtyName) { this.specialtyName = specialtyName; }

    public String getSubspecialtyCode() { return subspecialtyCode; }
    public void setSubspecialtyCode(String subspecialtyCode) { this.subspecialtyCode = subspecialtyCode; }

    public String getSubspecialtyName() { return subspecialtyName; }
    public void setSubspecialtyName(String subspecialtyName) { this.subspecialtyName = subspecialtyName; }

    public Boolean getZiboValidated() { return ziboValidated; }
    public void setZiboValidated(Boolean ziboValidated) { this.ziboValidated = ziboValidated; }

    public Boolean getPrimarySpecialty() { return primarySpecialty; }
    public void setPrimarySpecialty(Boolean primarySpecialty) { this.primarySpecialty = primarySpecialty; }

    public LocalDate getEffectiveDate() { return effectiveDate; }
    public void setEffectiveDate(LocalDate effectiveDate) { this.effectiveDate = effectiveDate; }

    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }

    public Instant getCreatedAt() { return createdAt; }
}
