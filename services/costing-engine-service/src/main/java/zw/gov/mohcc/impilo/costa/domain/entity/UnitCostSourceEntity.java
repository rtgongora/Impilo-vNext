package zw.gov.mohcc.impilo.costa.domain.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import zw.gov.mohcc.impilo.costa.domain.enums.UnitCostSourceType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "costa_unit_cost_sources")
public class UnitCostSourceEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 20)
    private UnitCostSourceType sourceType;

    @Column(name = "msika_code", length = 50)
    private String msikaCode;

    @JdbcTypeCode(SqlTypes.JSON)


    @Column(name = "ref", nullable = false, columnDefinition = "jsonb")
    private String ref = "{}";

    @Column(name = "unit_cost", nullable = false, precision = 14, scale = 2)
    private BigDecimal unitCost;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "USD";

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() { if (createdAt == null) createdAt = OffsetDateTime.now(); }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public UnitCostSourceType getSourceType() { return sourceType; }
    public void setSourceType(UnitCostSourceType sourceType) { this.sourceType = sourceType; }
    public String getMsikaCode() { return msikaCode; }
    public void setMsikaCode(String msikaCode) { this.msikaCode = msikaCode; }
    public String getRef() { return ref; }
    public void setRef(String ref) { this.ref = ref; }
    public BigDecimal getUnitCost() { return unitCost; }
    public void setUnitCost(BigDecimal unitCost) { this.unitCost = unitCost; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public LocalDate getEffectiveFrom() { return effectiveFrom; }
    public void setEffectiveFrom(LocalDate effectiveFrom) { this.effectiveFrom = effectiveFrom; }
    public LocalDate getEffectiveTo() { return effectiveTo; }
    public void setEffectiveTo(LocalDate effectiveTo) { this.effectiveTo = effectiveTo; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
