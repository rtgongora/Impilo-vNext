package zw.gov.mohcc.impilo.costa.domain.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "costa_tariffs")
public class TariffEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "tariff_code", nullable = false, length = 50)
    private String tariffCode;

    @Column(name = "msika_code", length = 50)
    private String msikaCode;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "price", nullable = false, precision = 14, scale = 2)
    private BigDecimal price;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "USD";

    @Column(name = "facility_category", length = 50)
    private String facilityCategory;

    @Column(name = "patient_category", length = 50)
    private String patientCategory;

    @JdbcTypeCode(SqlTypes.JSON)


    @Column(name = "rules", nullable = false, columnDefinition = "jsonb")
    private String rules = "{}";

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @Column(name = "version", nullable = false)
    private int version = 1;

    @Column(name = "status", nullable = false, length = 20)
    private String status = "ACTIVE";

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() { updatedAt = OffsetDateTime.now(); }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public String getTariffCode() { return tariffCode; }
    public void setTariffCode(String tariffCode) { this.tariffCode = tariffCode; }
    public String getMsikaCode() { return msikaCode; }
    public void setMsikaCode(String msikaCode) { this.msikaCode = msikaCode; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public String getFacilityCategory() { return facilityCategory; }
    public void setFacilityCategory(String facilityCategory) { this.facilityCategory = facilityCategory; }
    public String getPatientCategory() { return patientCategory; }
    public void setPatientCategory(String patientCategory) { this.patientCategory = patientCategory; }
    public String getRules() { return rules; }
    public void setRules(String rules) { this.rules = rules; }
    public LocalDate getEffectiveFrom() { return effectiveFrom; }
    public void setEffectiveFrom(LocalDate effectiveFrom) { this.effectiveFrom = effectiveFrom; }
    public LocalDate getEffectiveTo() { return effectiveTo; }
    public void setEffectiveTo(LocalDate effectiveTo) { this.effectiveTo = effectiveTo; }
    public int getVersion() { return version; }
    public void setVersion(int version) { this.version = version; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
