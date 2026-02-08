package zw.gov.mohcc.impilo.costa.domain.entity;

import jakarta.persistence.*;

import java.time.OffsetDateTime;

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
}
