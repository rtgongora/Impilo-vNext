package zw.gov.mohcc.impilo.msika.persistence.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "msika_tariffs")
public class TariffEntity {

    @Id
    @Column(name = "tariff_id", length = 26)
    private String tariffId;

    @Column(name = "tariff_code", nullable = false, length = 100)
    private String tariffCode;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "category", length = 100)
    private String category;

    @Column(name = "unit_price", nullable = false, precision = 18, scale = 4)
    private BigDecimal unitPrice = BigDecimal.ZERO;

    @Column(name = "currency", nullable = false, length = 10)
    private String currency = "USD";

    @Column(name = "billing_model", nullable = false, length = 50)
    private String billingModel = "FEE_FOR_SERVICE";

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(name = "status", nullable = false, length = 20)
    private String status = "ACTIVE";

    @Column(name = "pricing_rules", columnDefinition = "jsonb")
    private String pricingRules = "{}";

    @Column(name = "created_by", nullable = false, length = 128)
    private String createdBy;

    @Column(name = "updated_by", length = 128)
    private String updatedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
        updatedAt = OffsetDateTime.now();
        if (pricingRules == null) pricingRules = "{}";
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    public String getTariffId() { return tariffId; }
    public void setTariffId(String tariffId) { this.tariffId = tariffId; }
    public String getTariffCode() { return tariffCode; }
    public void setTariffCode(String tariffCode) { this.tariffCode = tariffCode; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public void setUnitPrice(BigDecimal unitPrice) { this.unitPrice = unitPrice; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public String getBillingModel() { return billingModel; }
    public void setBillingModel(String billingModel) { this.billingModel = billingModel; }
    public LocalDate getEffectiveFrom() { return effectiveFrom; }
    public void setEffectiveFrom(LocalDate effectiveFrom) { this.effectiveFrom = effectiveFrom; }
    public LocalDate getEffectiveTo() { return effectiveTo; }
    public void setEffectiveTo(LocalDate effectiveTo) { this.effectiveTo = effectiveTo; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getPricingRules() { return pricingRules; }
    public void setPricingRules(String pricingRules) { this.pricingRules = pricingRules; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public String getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
