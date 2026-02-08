package zw.gov.mohcc.impilo.costa.domain.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Table(name = "costa_abc_driver_rates")
public class AbcDriverRateEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "pool_id", nullable = false)
    private Long poolId;

    @Column(name = "driver_unit", nullable = false, length = 50)
    private String driverUnit;

    @Column(name = "annual_volume", nullable = false, precision = 14, scale = 2)
    private BigDecimal annualVolume;

    @Column(name = "rate", nullable = false, precision = 14, scale = 6)
    private BigDecimal rate;

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
    public Long getPoolId() { return poolId; }
    public void setPoolId(Long poolId) { this.poolId = poolId; }
    public String getDriverUnit() { return driverUnit; }
    public void setDriverUnit(String driverUnit) { this.driverUnit = driverUnit; }
    public BigDecimal getAnnualVolume() { return annualVolume; }
    public void setAnnualVolume(BigDecimal annualVolume) { this.annualVolume = annualVolume; }
    public BigDecimal getRate() { return rate; }
    public void setRate(BigDecimal rate) { this.rate = rate; }
    public LocalDate getEffectiveFrom() { return effectiveFrom; }
    public void setEffectiveFrom(LocalDate effectiveFrom) { this.effectiveFrom = effectiveFrom; }
    public LocalDate getEffectiveTo() { return effectiveTo; }
    public void setEffectiveTo(LocalDate effectiveTo) { this.effectiveTo = effectiveTo; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
