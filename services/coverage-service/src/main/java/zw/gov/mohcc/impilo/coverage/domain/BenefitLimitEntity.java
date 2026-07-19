package zw.gov.mohcc.impilo.coverage.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Benefit limit — monetary/quantity/frequency/etc. over a period (spec §13). */
@Entity
@Table(name = "cv_benefit_limits")
public class BenefitLimitEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "benefit_id", nullable = false)
    private UUID benefitId;

    @Column(name = "limit_type", nullable = false, length = 24)
    private String limitType;

    @Column(name = "limit_value")
    private BigDecimal limitValue;

    @Column(name = "limit_period", nullable = false, length = 24)
    private String limitPeriod = "CALENDAR_YEAR";

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    public BenefitLimitEntity() {}

    public static BenefitLimitEntity create(UUID tenantId, UUID benefitId, String limitType,
                                            BigDecimal limitValue, String limitPeriod) {
        BenefitLimitEntity l = new BenefitLimitEntity();
        l.id = UUID.randomUUID();
        l.tenantId = tenantId;
        l.benefitId = benefitId;
        l.limitType = limitType;
        l.limitValue = limitValue;
        if (limitPeriod != null && !limitPeriod.isBlank()) l.limitPeriod = limitPeriod;
        return l;
    }

    public UUID getId() { return id; }
    public UUID getBenefitId() { return benefitId; }
    public String getLimitType() { return limitType; }
    public BigDecimal getLimitValue() { return limitValue; }
    public String getLimitPeriod() { return limitPeriod; }
}
