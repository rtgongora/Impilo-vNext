package zw.gov.mohcc.impilo.costa.domain.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "costa_revenue_entries")
public class RevenueEntryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "entry_id", nullable = false, unique = true)
    private UUID entryId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "facility_id", nullable = false)
    private UUID facilityId;

    @Column(name = "department_id", length = 128)
    private String departmentId;

    @Column(name = "encounter_id", length = 128)
    private String encounterId;

    @Column(name = "bill_id", length = 128)
    private String billId;

    @Column(name = "revenue_type", nullable = false, length = 32)
    private String revenueType;

    @Column(name = "service_code", length = 64)
    private String serviceCode;

    @Column(name = "description", length = 512)
    private String description;

    @Column(name = "gross_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal grossAmount;

    @Column(name = "discount_amount", precision = 14, scale = 2)
    private BigDecimal discountAmount = BigDecimal.ZERO;

    @Column(name = "net_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal netAmount;

    @Column(name = "payer_type", length = 32)
    private String payerType;

    @Column(name = "payer_ref", length = 128)
    private String payerRef;

    @Column(name = "recorded_at", nullable = false)
    private OffsetDateTime recordedAt;

    @Column(name = "period_year", nullable = false)
    private int periodYear;

    @Column(name = "period_month", nullable = false)
    private int periodMonth;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (entryId == null) {
            entryId = UUID.randomUUID();
        }
        OffsetDateTime now = OffsetDateTime.now();
        if (recordedAt == null) {
            recordedAt = now;
        }
        if (createdAt == null) {
            createdAt = now;
        }
        if (periodYear == 0) {
            periodYear = now.getYear();
        }
        if (periodMonth == 0) {
            periodMonth = now.getMonthValue();
        }
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public UUID getEntryId() { return entryId; }
    public void setEntryId(UUID entryId) { this.entryId = entryId; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public UUID getFacilityId() { return facilityId; }
    public void setFacilityId(UUID facilityId) { this.facilityId = facilityId; }
    public String getDepartmentId() { return departmentId; }
    public void setDepartmentId(String departmentId) { this.departmentId = departmentId; }
    public String getEncounterId() { return encounterId; }
    public void setEncounterId(String encounterId) { this.encounterId = encounterId; }
    public String getBillId() { return billId; }
    public void setBillId(String billId) { this.billId = billId; }
    public String getRevenueType() { return revenueType; }
    public void setRevenueType(String revenueType) { this.revenueType = revenueType; }
    public String getServiceCode() { return serviceCode; }
    public void setServiceCode(String serviceCode) { this.serviceCode = serviceCode; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public BigDecimal getGrossAmount() { return grossAmount; }
    public void setGrossAmount(BigDecimal grossAmount) { this.grossAmount = grossAmount; }
    public BigDecimal getDiscountAmount() { return discountAmount; }
    public void setDiscountAmount(BigDecimal discountAmount) { this.discountAmount = discountAmount; }
    public BigDecimal getNetAmount() { return netAmount; }
    public void setNetAmount(BigDecimal netAmount) { this.netAmount = netAmount; }
    public String getPayerType() { return payerType; }
    public void setPayerType(String payerType) { this.payerType = payerType; }
    public String getPayerRef() { return payerRef; }
    public void setPayerRef(String payerRef) { this.payerRef = payerRef; }
    public OffsetDateTime getRecordedAt() { return recordedAt; }
    public void setRecordedAt(OffsetDateTime recordedAt) { this.recordedAt = recordedAt; }
    public int getPeriodYear() { return periodYear; }
    public void setPeriodYear(int periodYear) { this.periodYear = periodYear; }
    public int getPeriodMonth() { return periodMonth; }
    public void setPeriodMonth(int periodMonth) { this.periodMonth = periodMonth; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
