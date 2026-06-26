package zw.gov.mohcc.impilo.coverage.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * One ledger row per successful subsidy drawdown, idempotent on {@code reference}.
 *
 * <p>A retried consume with the same {@code (enrolmentId, reference)} returns the prior
 * result (the {@code consumedAfter} snapshot) instead of drawing the subsidy down again
 * (H2). The unique constraint is enforced in the database.
 */
@Entity
@Table(name = "cv_subsidy_drawdowns")
public class SubsidyDrawdownEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "enrolment_id", nullable = false)
    private UUID enrolmentId;

    @Column(name = "period_year", nullable = false)
    private int periodYear;

    @Column(name = "reference", nullable = false, length = 120)
    private String reference;

    @Column(name = "amount", nullable = false)
    private BigDecimal amount;

    @Column(name = "consumed_after", nullable = false)
    private BigDecimal consumedAfter;

    @Column(name = "cap_amount")
    private BigDecimal capAmount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "USD";

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (id == null) id = UUID.randomUUID();
        if (createdAt == null) createdAt = OffsetDateTime.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public UUID getEnrolmentId() { return enrolmentId; }
    public void setEnrolmentId(UUID enrolmentId) { this.enrolmentId = enrolmentId; }
    public int getPeriodYear() { return periodYear; }
    public void setPeriodYear(int periodYear) { this.periodYear = periodYear; }
    public String getReference() { return reference; }
    public void setReference(String reference) { this.reference = reference; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public BigDecimal getConsumedAfter() { return consumedAfter; }
    public void setConsumedAfter(BigDecimal consumedAfter) { this.consumedAfter = consumedAfter; }
    public BigDecimal getCapAmount() { return capAmount; }
    public void setCapAmount(BigDecimal capAmount) { this.capAmount = capAmount; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
