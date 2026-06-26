package zw.gov.mohcc.impilo.coverage.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Annual-cap consumption for a subsidy enrolment in a given cap year. */
@Entity
@Table(name = "cv_subsidy_balances")
public class SubsidyBalanceEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "enrolment_id", nullable = false)
    private UUID enrolmentId;

    @Column(name = "period_year", nullable = false)
    private int periodYear;

    @Column(name = "cap_amount")
    private BigDecimal capAmount;

    @Column(name = "consumed_amount", nullable = false)
    private BigDecimal consumedAmount = BigDecimal.ZERO;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "USD";

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    void prePersist() {
        if (id == null) id = UUID.randomUUID();
        updatedAt = OffsetDateTime.now();
    }

    @PreUpdate
    void preUpdate() { updatedAt = OffsetDateTime.now(); }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public UUID getEnrolmentId() { return enrolmentId; }
    public void setEnrolmentId(UUID enrolmentId) { this.enrolmentId = enrolmentId; }
    public int getPeriodYear() { return periodYear; }
    public void setPeriodYear(int periodYear) { this.periodYear = periodYear; }
    public BigDecimal getCapAmount() { return capAmount; }
    public void setCapAmount(BigDecimal capAmount) { this.capAmount = capAmount; }
    public BigDecimal getConsumedAmount() { return consumedAmount; }
    public void setConsumedAmount(BigDecimal consumedAmount) { this.consumedAmount = consumedAmount; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
