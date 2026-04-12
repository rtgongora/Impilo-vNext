package zw.gov.mohcc.impilo.coverage.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "cv_member_contributions")
public class MemberContributionEntity {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "member_id", nullable = false)
    private UUID memberId;

    @Column(name = "plan_id", nullable = false)
    private UUID planId;

    @Column(name = "amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "USD";

    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    @Column(name = "period_end", nullable = false)
    private LocalDate periodEnd;

    @Column(name = "status", nullable = false, length = 30)
    private String status;

    @Column(name = "paid_at")
    private OffsetDateTime paidAt;

    @Column(name = "payment_ref", length = 100)
    private String paymentRef;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected MemberContributionEntity() {}

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public UUID getMemberId() {
        return memberId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public LocalDate getPeriodStart() {
        return periodStart;
    }

    public LocalDate getPeriodEnd() {
        return periodEnd;
    }

    public String getStatus() {
        return status;
    }

    public OffsetDateTime getPaidAt() {
        return paidAt;
    }

    public String getPaymentRef() {
        return paymentRef;
    }
}
