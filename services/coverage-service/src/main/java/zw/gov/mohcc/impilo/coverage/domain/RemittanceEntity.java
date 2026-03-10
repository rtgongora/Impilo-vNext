package zw.gov.mohcc.impilo.coverage.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "cv_remittances")
public class RemittanceEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "pod_id", nullable = false, length = 64)
    private String podId = "national-spine";

    @Column(name = "payer_id", nullable = false, length = 255)
    private String payerId;

    @Column(name = "provider_ref", nullable = false, length = 255)
    private String providerRef;

    @Column(name = "amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "USD";

    @Column(name = "status", nullable = false, length = 16)
    private String status = "PENDING";

    @Column(name = "reference_number", length = 128)
    private String referenceNumber;

    @Column(name = "line_items", columnDefinition = "TEXT")
    private String lineItems;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected RemittanceEntity() {}

    public RemittanceEntity(UUID tenantId, String podId, String payerId,
                            String providerRef, BigDecimal amount, String currency,
                            String referenceNumber, String lineItems, String notes) {
        this.id = UUID.randomUUID();
        this.tenantId = tenantId;
        this.podId = podId;
        this.payerId = payerId;
        this.providerRef = providerRef;
        this.amount = amount;
        this.currency = currency != null ? currency : "USD";
        this.referenceNumber = referenceNumber;
        this.lineItems = lineItems;
        this.notes = notes;
        OffsetDateTime now = OffsetDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public String getPodId() { return podId; }
    public String getPayerId() { return payerId; }
    public String getProviderRef() { return providerRef; }
    public BigDecimal getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public String getStatus() { return status; }
    public String getReferenceNumber() { return referenceNumber; }
    public String getLineItems() { return lineItems; }
    public String getNotes() { return notes; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }

    public void setStatus(String status) {
        this.status = status;
        this.updatedAt = OffsetDateTime.now();
    }
}
