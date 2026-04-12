package zw.gov.mohcc.impilo.costa.domain.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "costa_receivables")
public class ReceivableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "receivable_id", nullable = false, unique = true)
    private UUID receivableId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "facility_id", nullable = false)
    private UUID facilityId;

    @Column(name = "debtor_type", nullable = false, length = 32)
    private String debtorType;

    @Column(name = "debtor_ref", nullable = false, length = 128)
    private String debtorRef;

    @Column(name = "debtor_name", length = 255)
    private String debtorName;

    @Column(name = "bill_id", length = 128)
    private String billId;

    @Column(name = "invoice_id", length = 128)
    private String invoiceId;

    @Column(name = "original_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal originalAmount;

    @Column(name = "paid_amount", precision = 14, scale = 2)
    private BigDecimal paidAmount = BigDecimal.ZERO;

    @Column(name = "outstanding", nullable = false, precision = 14, scale = 2)
    private BigDecimal outstanding;

    @Column(name = "currency", length = 3)
    private String currency = "USD";

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(name = "status", length = 16)
    private String status = "OPEN";

    @Column(name = "aging_bucket", length = 16)
    private String agingBucket;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (receivableId == null) {
            receivableId = UUID.randomUUID();
        }
        OffsetDateTime now = OffsetDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
        if (paidAmount == null) {
            paidAmount = BigDecimal.ZERO;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public UUID getReceivableId() { return receivableId; }
    public void setReceivableId(UUID receivableId) { this.receivableId = receivableId; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public UUID getFacilityId() { return facilityId; }
    public void setFacilityId(UUID facilityId) { this.facilityId = facilityId; }
    public String getDebtorType() { return debtorType; }
    public void setDebtorType(String debtorType) { this.debtorType = debtorType; }
    public String getDebtorRef() { return debtorRef; }
    public void setDebtorRef(String debtorRef) { this.debtorRef = debtorRef; }
    public String getDebtorName() { return debtorName; }
    public void setDebtorName(String debtorName) { this.debtorName = debtorName; }
    public String getBillId() { return billId; }
    public void setBillId(String billId) { this.billId = billId; }
    public String getInvoiceId() { return invoiceId; }
    public void setInvoiceId(String invoiceId) { this.invoiceId = invoiceId; }
    public BigDecimal getOriginalAmount() { return originalAmount; }
    public void setOriginalAmount(BigDecimal originalAmount) { this.originalAmount = originalAmount; }
    public BigDecimal getPaidAmount() { return paidAmount; }
    public void setPaidAmount(BigDecimal paidAmount) { this.paidAmount = paidAmount; }
    public BigDecimal getOutstanding() { return outstanding; }
    public void setOutstanding(BigDecimal outstanding) { this.outstanding = outstanding; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public LocalDate getDueDate() { return dueDate; }
    public void setDueDate(LocalDate dueDate) { this.dueDate = dueDate; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getAgingBucket() { return agingBucket; }
    public void setAgingBucket(String agingBucket) { this.agingBucket = agingBucket; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
