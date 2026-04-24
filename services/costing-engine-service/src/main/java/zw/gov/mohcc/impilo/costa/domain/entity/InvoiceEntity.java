package zw.gov.mohcc.impilo.costa.domain.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "costa_invoices")
public class InvoiceEntity {

    @Id
    @Column(name = "invoice_id", length = 26)
    private String invoiceId;

    @Column(name = "bill_id", nullable = false, length = 26)
    private String billId;

    @Column(name = "invoice_number", nullable = false, length = 50)
    private String invoiceNumber;

    @Column(name = "landela_doc_id", length = 100)
    private String landelaDocId;

    @Column(name = "issued_at", nullable = false)
    private OffsetDateTime issuedAt;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "billing_account_id", length = 26)
    private String billingAccountId;

    @Column(name = "client_ref", length = 128)
    private String clientRef;

    @Column(name = "payer_ref", length = 128)
    private String payerRef;

    @Column(name = "invoice_status", nullable = false, length = 30)
    private String invoiceStatus = "ISSUED";

    @Column(name = "subtotal", precision = 14, scale = 2)
    private BigDecimal subtotal;

    @Column(name = "total", precision = 14, scale = 2)
    private BigDecimal total;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "USD";

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "settled_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal settledAmount = BigDecimal.ZERO;

    @Column(name = "outstanding_amount", precision = 14, scale = 2)
    private BigDecimal outstandingAmount;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata_json", columnDefinition = "jsonb")
    private String metadataJson = "{}";

    @PrePersist
    protected void onCreate() { if (issuedAt == null) issuedAt = OffsetDateTime.now(); }

    public String getInvoiceId() { return invoiceId; }
    public void setInvoiceId(String invoiceId) { this.invoiceId = invoiceId; }
    public String getBillId() { return billId; }
    public void setBillId(String billId) { this.billId = billId; }
    public String getInvoiceNumber() { return invoiceNumber; }
    public void setInvoiceNumber(String invoiceNumber) { this.invoiceNumber = invoiceNumber; }
    public String getLandelaDocId() { return landelaDocId; }
    public void setLandelaDocId(String landelaDocId) { this.landelaDocId = landelaDocId; }
    public OffsetDateTime getIssuedAt() { return issuedAt; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public String getBillingAccountId() { return billingAccountId; }
    public void setBillingAccountId(String billingAccountId) { this.billingAccountId = billingAccountId; }
    public String getClientRef() { return clientRef; }
    public void setClientRef(String clientRef) { this.clientRef = clientRef; }
    public String getPayerRef() { return payerRef; }
    public void setPayerRef(String payerRef) { this.payerRef = payerRef; }
    public String getInvoiceStatus() { return invoiceStatus; }
    public void setInvoiceStatus(String invoiceStatus) { this.invoiceStatus = invoiceStatus; }
    public BigDecimal getSubtotal() { return subtotal; }
    public void setSubtotal(BigDecimal subtotal) { this.subtotal = subtotal; }
    public BigDecimal getTotal() { return total; }
    public void setTotal(BigDecimal total) { this.total = total; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public LocalDate getDueDate() { return dueDate; }
    public void setDueDate(LocalDate dueDate) { this.dueDate = dueDate; }
    public BigDecimal getSettledAmount() { return settledAmount; }
    public void setSettledAmount(BigDecimal settledAmount) { this.settledAmount = settledAmount; }
    public BigDecimal getOutstandingAmount() { return outstandingAmount; }
    public void setOutstandingAmount(BigDecimal outstandingAmount) { this.outstandingAmount = outstandingAmount; }
    public String getMetadataJson() { return metadataJson; }
    public void setMetadataJson(String metadataJson) { this.metadataJson = metadataJson; }
}
