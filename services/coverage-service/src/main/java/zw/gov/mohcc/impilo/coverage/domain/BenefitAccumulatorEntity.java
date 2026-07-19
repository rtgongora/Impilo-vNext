package zw.gov.mohcc.impilo.coverage.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Reservation-aware benefit accumulator (spec §13). Tracks used + reserved so pending
 * authorisations and submitted claims cannot cause accidental over-use. remaining is
 * derived: limit - used - reserved. Modeled on the proven subsidy balance ledger.
 */
@Entity
@Table(name = "cv_benefit_accumulators")
public class BenefitAccumulatorEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "member_id", nullable = false)
    private UUID memberId;

    @Column(name = "plan_version_id", nullable = false)
    private UUID planVersionId;

    @Column(name = "benefit_code", nullable = false, length = 64)
    private String benefitCode;

    @Column(name = "period_key", nullable = false, length = 16)
    private String periodKey;

    @Column(name = "limit_amount")
    private BigDecimal limitAmount;

    @Column(name = "used_amount", nullable = false)
    private BigDecimal usedAmount = BigDecimal.ZERO;

    @Column(name = "reserved_amount", nullable = false)
    private BigDecimal reservedAmount = BigDecimal.ZERO;

    @Column(name = "currency", nullable = false, length = 8)
    private String currency = "USD";

    @Column(name = "version", nullable = false)
    private long version = 0;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    public BenefitAccumulatorEntity() {}

    public static BenefitAccumulatorEntity create(UUID tenantId, UUID memberId, UUID planVersionId,
                                                  String benefitCode, String periodKey,
                                                  BigDecimal limitAmount, String currency) {
        BenefitAccumulatorEntity a = new BenefitAccumulatorEntity();
        a.id = UUID.randomUUID();
        a.tenantId = tenantId;
        a.memberId = memberId;
        a.planVersionId = planVersionId;
        a.benefitCode = benefitCode;
        a.periodKey = periodKey;
        a.limitAmount = limitAmount;
        if (currency != null && !currency.isBlank()) a.currency = currency;
        return a;
    }

    public BigDecimal remaining() {
        if (limitAmount == null) return null; // unlimited
        return limitAmount.subtract(usedAmount).subtract(reservedAmount);
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getMemberId() { return memberId; }
    public UUID getPlanVersionId() { return planVersionId; }
    public String getBenefitCode() { return benefitCode; }
    public String getPeriodKey() { return periodKey; }
    public BigDecimal getLimitAmount() { return limitAmount; }
    public void setLimitAmount(BigDecimal v) { this.limitAmount = v; }
    public BigDecimal getUsedAmount() { return usedAmount; }
    public BigDecimal getReservedAmount() { return reservedAmount; }
    public String getCurrency() { return currency; }
    public long getVersion() { return version; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
