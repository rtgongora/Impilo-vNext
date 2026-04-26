package zw.gov.mohcc.impilo.costa.domain.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "costa_tariff_lists")
public class CostaTariffListEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(name = "library_id", nullable = false)
    private Long libraryId;

    @Column(name = "external_code", nullable = false, length = 120)
    private String externalCode;

    @Column(name = "name", nullable = false, length = 400)
    private String name;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Column(name = "tariff_family", nullable = false, length = 80)
    private String tariffFamily;

    @Column(name = "tariff_type", nullable = false, length = 60)
    private String tariffType;

    @Column(name = "price_basis", nullable = false, length = 60)
    private String priceBasis;

    @Column(name = "official_status", nullable = false, length = 60)
    private String officialStatus;

    @Column(name = "validation_status", nullable = false, length = 60)
    private String validationStatus = "validated";

    @Column(name = "reference_only", nullable = false)
    private boolean referenceOnly;

    @Column(name = "approved_for_billing", nullable = false)
    private boolean approvedForBilling;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "USD";

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", nullable = false, columnDefinition = "jsonb")
    private String metadata = "{}";

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }

    public Long getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }

    public Long getLibraryId() {
        return libraryId;
    }

    public void setLibraryId(Long libraryId) {
        this.libraryId = libraryId;
    }

    public String getExternalCode() {
        return externalCode;
    }

    public void setExternalCode(String externalCode) {
        this.externalCode = externalCode;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getTariffFamily() {
        return tariffFamily;
    }

    public void setTariffFamily(String tariffFamily) {
        this.tariffFamily = tariffFamily;
    }

    public String getTariffType() {
        return tariffType;
    }

    public void setTariffType(String tariffType) {
        this.tariffType = tariffType;
    }

    public String getPriceBasis() {
        return priceBasis;
    }

    public void setPriceBasis(String priceBasis) {
        this.priceBasis = priceBasis;
    }

    public String getOfficialStatus() {
        return officialStatus;
    }

    public void setOfficialStatus(String officialStatus) {
        this.officialStatus = officialStatus;
    }

    public String getValidationStatus() {
        return validationStatus;
    }

    public void setValidationStatus(String validationStatus) {
        this.validationStatus = validationStatus;
    }

    public boolean isReferenceOnly() {
        return referenceOnly;
    }

    public void setReferenceOnly(boolean referenceOnly) {
        this.referenceOnly = referenceOnly;
    }

    public boolean isApprovedForBilling() {
        return approvedForBilling;
    }

    public void setApprovedForBilling(boolean approvedForBilling) {
        this.approvedForBilling = approvedForBilling;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public LocalDate getEffectiveFrom() {
        return effectiveFrom;
    }

    public void setEffectiveFrom(LocalDate effectiveFrom) {
        this.effectiveFrom = effectiveFrom;
    }

    public LocalDate getEffectiveTo() {
        return effectiveTo;
    }

    public void setEffectiveTo(LocalDate effectiveTo) {
        this.effectiveTo = effectiveTo;
    }

    public String getMetadata() {
        return metadata;
    }

    public void setMetadata(String metadata) {
        this.metadata = metadata;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
