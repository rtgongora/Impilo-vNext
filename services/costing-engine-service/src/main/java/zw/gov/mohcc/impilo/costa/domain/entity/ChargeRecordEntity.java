package zw.gov.mohcc.impilo.costa.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "costa_charge_records")
public class ChargeRecordEntity {

    @Id
    @Column(name = "charge_id", length = 26)
    private String chargeId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "charge_code", nullable = false, length = 64)
    private String chargeCode;

    @Column(name = "charge_type", nullable = false, length = 40)
    private String chargeType;

    @Column(name = "source_type", nullable = false, length = 64)
    private String sourceType;

    @Column(name = "source_ref", nullable = false, length = 128)
    private String sourceRef;

    @Column(name = "client_ref", length = 128)
    private String clientRef;

    @Column(name = "payer_ref", length = 128)
    private String payerRef;

    @Column(name = "provider_ref", length = 128)
    private String providerRef;

    @Column(name = "facility_id")
    private UUID facilityId;

    @Column(name = "item_ref", length = 128)
    private String itemRef;

    @Column(name = "offering_ref", length = 128)
    private String offeringRef;

    @Column(name = "service_ref", length = 128)
    private String serviceRef;

    @Column(name = "bill_id", length = 26)
    private String billId;

    @Column(name = "line_id", length = 26)
    private String lineId;

    @Column(name = "charge_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal chargeAmount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "USD";

    @Column(name = "charge_status", nullable = false, length = 30)
    private String chargeStatus = "OPEN";

    @Column(name = "billable_flag", nullable = false)
    private boolean billableFlag = true;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata_json", columnDefinition = "jsonb")
    private String metadataJson = "{}";

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }

    public String getChargeId() { return chargeId; }
    public void setChargeId(String chargeId) { this.chargeId = chargeId; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public String getChargeCode() { return chargeCode; }
    public void setChargeCode(String chargeCode) { this.chargeCode = chargeCode; }
    public String getChargeType() { return chargeType; }
    public void setChargeType(String chargeType) { this.chargeType = chargeType; }
    public String getSourceType() { return sourceType; }
    public void setSourceType(String sourceType) { this.sourceType = sourceType; }
    public String getSourceRef() { return sourceRef; }
    public void setSourceRef(String sourceRef) { this.sourceRef = sourceRef; }
    public String getClientRef() { return clientRef; }
    public void setClientRef(String clientRef) { this.clientRef = clientRef; }
    public String getPayerRef() { return payerRef; }
    public void setPayerRef(String payerRef) { this.payerRef = payerRef; }
    public String getProviderRef() { return providerRef; }
    public void setProviderRef(String providerRef) { this.providerRef = providerRef; }
    public UUID getFacilityId() { return facilityId; }
    public void setFacilityId(UUID facilityId) { this.facilityId = facilityId; }
    public String getItemRef() { return itemRef; }
    public void setItemRef(String itemRef) { this.itemRef = itemRef; }
    public String getOfferingRef() { return offeringRef; }
    public void setOfferingRef(String offeringRef) { this.offeringRef = offeringRef; }
    public String getServiceRef() { return serviceRef; }
    public void setServiceRef(String serviceRef) { this.serviceRef = serviceRef; }
    public String getBillId() { return billId; }
    public void setBillId(String billId) { this.billId = billId; }
    public String getLineId() { return lineId; }
    public void setLineId(String lineId) { this.lineId = lineId; }
    public BigDecimal getChargeAmount() { return chargeAmount; }
    public void setChargeAmount(BigDecimal chargeAmount) { this.chargeAmount = chargeAmount; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public String getChargeStatus() { return chargeStatus; }
    public void setChargeStatus(String chargeStatus) { this.chargeStatus = chargeStatus; }
    public boolean isBillableFlag() { return billableFlag; }
    public void setBillableFlag(boolean billableFlag) { this.billableFlag = billableFlag; }
    public String getMetadataJson() { return metadataJson; }
    public void setMetadataJson(String metadataJson) { this.metadataJson = metadataJson; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
