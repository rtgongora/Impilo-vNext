package zw.gov.mohcc.impilo.coverage.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "cv_utilization_summary")
public class UtilizationSummaryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "member_cpid", nullable = false, length = 128)
    private String memberCpid;

    @Column(name = "plan_code", nullable = false, length = 64)
    private String planCode;

    @Column(name = "benefit_code", nullable = false, length = 64)
    private String benefitCode;

    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    @Column(name = "period_end", nullable = false)
    private LocalDate periodEnd;

    @Column(name = "limit_amount", precision = 10, scale = 2)
    private BigDecimal limitAmount;

    @Column(name = "used_amount", precision = 10, scale = 2)
    private BigDecimal usedAmount = BigDecimal.ZERO;

    @Column(name = "used_count")
    private Integer usedCount = 0;

    @Column(name = "last_updated", nullable = false)
    private OffsetDateTime lastUpdated = OffsetDateTime.now();

    protected UtilizationSummaryEntity() {}

    public UtilizationSummaryEntity(UUID tenantId, String memberCpid, String planCode, String benefitCode,
                                    LocalDate periodStart, LocalDate periodEnd, BigDecimal limitAmount) {
        this.tenantId = tenantId;
        this.memberCpid = memberCpid;
        this.planCode = planCode;
        this.benefitCode = benefitCode;
        this.periodStart = periodStart;
        this.periodEnd = periodEnd;
        this.limitAmount = limitAmount;
        this.usedAmount = BigDecimal.ZERO;
        this.usedCount = 0;
        this.lastUpdated = OffsetDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public String getMemberCpid() {
        return memberCpid;
    }

    public String getPlanCode() {
        return planCode;
    }

    public String getBenefitCode() {
        return benefitCode;
    }

    public LocalDate getPeriodStart() {
        return periodStart;
    }

    public LocalDate getPeriodEnd() {
        return periodEnd;
    }

    public BigDecimal getLimitAmount() {
        return limitAmount;
    }

    public BigDecimal getUsedAmount() {
        return usedAmount;
    }

    public void addUsedAmount(BigDecimal delta) {
        if (delta == null) {
            return;
        }
        if (this.usedAmount == null) {
            this.usedAmount = BigDecimal.ZERO;
        }
        this.usedAmount = this.usedAmount.add(delta);
        this.usedCount = (this.usedCount == null ? 0 : this.usedCount) + 1;
        this.lastUpdated = OffsetDateTime.now();
    }
}
