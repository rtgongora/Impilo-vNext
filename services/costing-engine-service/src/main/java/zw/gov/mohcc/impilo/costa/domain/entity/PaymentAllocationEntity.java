package zw.gov.mohcc.impilo.costa.domain.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "costa_payment_allocations")
public class PaymentAllocationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "allocation_id", nullable = false, unique = true)
    private UUID allocationId = UUID.randomUUID();

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "invoice_id", nullable = false, length = 26)
    private String invoiceId;

    @Column(name = "charge_id", length = 26)
    private String chargeId;

    @Column(name = "costa_payment_id", nullable = false)
    private Long costaPaymentId;

    @Column(name = "mushex_payment_intent_id", length = 100)
    private String mushexPaymentIntentId;

    @Column(name = "allocated_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal allocatedAmount;

    @Column(name = "allocation_status", nullable = false, length = 30)
    private String allocationStatus = "SETTLED";

    @Column(name = "allocated_at", nullable = false)
    private OffsetDateTime allocatedAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata_json", columnDefinition = "jsonb")
    private String metadataJson = "{}";

    @PrePersist
    protected void onCreate() {
        if (allocationId == null) {
            allocationId = UUID.randomUUID();
        }
        if (allocatedAt == null) {
            allocatedAt = OffsetDateTime.now();
        }
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public UUID getAllocationId() { return allocationId; }
    public void setAllocationId(UUID allocationId) { this.allocationId = allocationId; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public String getInvoiceId() { return invoiceId; }
    public void setInvoiceId(String invoiceId) { this.invoiceId = invoiceId; }
    public String getChargeId() { return chargeId; }
    public void setChargeId(String chargeId) { this.chargeId = chargeId; }
    public Long getCostaPaymentId() { return costaPaymentId; }
    public void setCostaPaymentId(Long costaPaymentId) { this.costaPaymentId = costaPaymentId; }
    public String getMushexPaymentIntentId() { return mushexPaymentIntentId; }
    public void setMushexPaymentIntentId(String mushexPaymentIntentId) { this.mushexPaymentIntentId = mushexPaymentIntentId; }
    public BigDecimal getAllocatedAmount() { return allocatedAmount; }
    public void setAllocatedAmount(BigDecimal allocatedAmount) { this.allocatedAmount = allocatedAmount; }
    public String getAllocationStatus() { return allocationStatus; }
    public void setAllocationStatus(String allocationStatus) { this.allocationStatus = allocationStatus; }
    public OffsetDateTime getAllocatedAt() { return allocatedAt; }
    public void setAllocatedAt(OffsetDateTime allocatedAt) { this.allocatedAt = allocatedAt; }
    public String getMetadataJson() { return metadataJson; }
    public void setMetadataJson(String metadataJson) { this.metadataJson = metadataJson; }
}
