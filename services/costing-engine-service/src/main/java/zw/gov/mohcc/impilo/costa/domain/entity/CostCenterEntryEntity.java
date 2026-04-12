package zw.gov.mohcc.impilo.costa.domain.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "costa_cost_center_entries")
public class CostCenterEntryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "center_id", nullable = false)
    private UUID centerId;

    @Column(name = "entry_type", nullable = false, length = 16)
    private String entryType;

    @Column(name = "amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Column(name = "description", length = 512)
    private String description;

    @Column(name = "reference_type", length = 32)
    private String referenceType;

    @Column(name = "reference_id", length = 128)
    private String referenceId;

    @Column(name = "period_year", nullable = false)
    private int periodYear;

    @Column(name = "period_month", nullable = false)
    private int periodMonth;

    @Column(name = "recorded_at", nullable = false)
    private OffsetDateTime recordedAt;

    @PrePersist
    protected void onCreate() {
        if (recordedAt == null) {
            recordedAt = OffsetDateTime.now();
        }
        if (periodYear == 0) {
            periodYear = recordedAt.getYear();
        }
        if (periodMonth == 0) {
            periodMonth = recordedAt.getMonthValue();
        }
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public UUID getCenterId() { return centerId; }
    public void setCenterId(UUID centerId) { this.centerId = centerId; }
    public String getEntryType() { return entryType; }
    public void setEntryType(String entryType) { this.entryType = entryType; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getReferenceType() { return referenceType; }
    public void setReferenceType(String referenceType) { this.referenceType = referenceType; }
    public String getReferenceId() { return referenceId; }
    public void setReferenceId(String referenceId) { this.referenceId = referenceId; }
    public int getPeriodYear() { return periodYear; }
    public void setPeriodYear(int periodYear) { this.periodYear = periodYear; }
    public int getPeriodMonth() { return periodMonth; }
    public void setPeriodMonth(int periodMonth) { this.periodMonth = periodMonth; }
    public OffsetDateTime getRecordedAt() { return recordedAt; }
    public void setRecordedAt(OffsetDateTime recordedAt) { this.recordedAt = recordedAt; }
}
