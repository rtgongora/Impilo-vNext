package zw.gov.mohcc.impilo.mushex.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "mushex_remittance_requests")
public class RemittanceRequestEntity {

    @Id
    @Column(name = "remittance_request_id", length = 26)
    private String remittanceRequestId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "remittance_code", nullable = false, length = 64)
    private String remittanceCode;

    @Column(name = "sender_ref", nullable = false, length = 128)
    private String senderRef;

    @Column(name = "recipient_ref", nullable = false, length = 128)
    private String recipientRef;

    @Column(name = "recipient_type", nullable = false, length = 40)
    private String recipientType;

    @Column(name = "origin_country", length = 3)
    private String originCountry;

    @Column(name = "destination_country", length = 3)
    private String destinationCountry;

    @Column(name = "domestic_or_international", nullable = false, length = 16)
    private String domesticOrInternational = "DOMESTIC";

    @Column(name = "remittance_purpose", length = 64)
    private String remittancePurpose;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "health_context", columnDefinition = "jsonb")
    private String healthContext;

    @Column(name = "amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "USD";

    @Column(name = "status", nullable = false, length = 30)
    private String status = "REQUESTED";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private String metadata;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    public String getRemittanceRequestId() { return remittanceRequestId; }
    public void setRemittanceRequestId(String remittanceRequestId) { this.remittanceRequestId = remittanceRequestId; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public String getRemittanceCode() { return remittanceCode; }
    public void setRemittanceCode(String remittanceCode) { this.remittanceCode = remittanceCode; }
    public String getSenderRef() { return senderRef; }
    public void setSenderRef(String senderRef) { this.senderRef = senderRef; }
    public String getRecipientRef() { return recipientRef; }
    public void setRecipientRef(String recipientRef) { this.recipientRef = recipientRef; }
    public String getRecipientType() { return recipientType; }
    public void setRecipientType(String recipientType) { this.recipientType = recipientType; }
    public String getOriginCountry() { return originCountry; }
    public void setOriginCountry(String originCountry) { this.originCountry = originCountry; }
    public String getDestinationCountry() { return destinationCountry; }
    public void setDestinationCountry(String destinationCountry) { this.destinationCountry = destinationCountry; }
    public String getDomesticOrInternational() { return domesticOrInternational; }
    public void setDomesticOrInternational(String domesticOrInternational) { this.domesticOrInternational = domesticOrInternational; }
    public String getRemittancePurpose() { return remittancePurpose; }
    public void setRemittancePurpose(String remittancePurpose) { this.remittancePurpose = remittancePurpose; }
    public String getHealthContext() { return healthContext; }
    public void setHealthContext(String healthContext) { this.healthContext = healthContext; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getMetadata() { return metadata; }
    public void setMetadata(String metadata) { this.metadata = metadata; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
