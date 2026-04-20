package zw.gov.mohcc.impilo.costa.domain.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import zw.gov.mohcc.impilo.costa.domain.enums.BillLineKind;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "costa_bill_lines")
public class BillLineEntity {

    @Id
    @Column(name = "line_id", length = 26)
    private String lineId;

    @Column(name = "bill_id", nullable = false, length = 26)
    private String billId;

    @Column(name = "msika_code", length = 50)
    private String msikaCode;

    @Column(name = "description", nullable = false, length = 500)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 30)
    private BillLineKind kind;

    @Column(name = "qty", nullable = false, precision = 10, scale = 3)
    private BigDecimal qty = BigDecimal.ONE;

    @Column(name = "unit_price", nullable = false, precision = 14, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Column(name = "cost_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal costAmount = BigDecimal.ZERO;

    @Column(name = "cost_method_used", length = 20)
    private String costMethodUsed;

    @JdbcTypeCode(SqlTypes.JSON)


    @Column(name = "cost_trace", nullable = false, columnDefinition = "jsonb")
    private String costTrace = "{}";

    @JdbcTypeCode(SqlTypes.JSON)


    @Column(name = "charge_trace", nullable = false, columnDefinition = "jsonb")
    private String chargeTrace = "{}";

    @JdbcTypeCode(SqlTypes.JSON)


    @Column(name = "restriction_snapshot", nullable = false, columnDefinition = "jsonb")
    private String restrictionSnapshot = "{}";

    @Column(name = "source_event", length = 100)
    private String sourceEvent;

    @Column(name = "source_ref", length = 100)
    private String sourceRef;

    @Column(name = "posted_at", nullable = false)
    private OffsetDateTime postedAt;

    @Column(name = "voided", nullable = false)
    private boolean voided = false;

    @PrePersist
    protected void onCreate() { if (postedAt == null) postedAt = OffsetDateTime.now(); }

    public String getLineId() { return lineId; }
    public void setLineId(String lineId) { this.lineId = lineId; }
    public String getBillId() { return billId; }
    public void setBillId(String billId) { this.billId = billId; }
    public String getMsikaCode() { return msikaCode; }
    public void setMsikaCode(String msikaCode) { this.msikaCode = msikaCode; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public BillLineKind getKind() { return kind; }
    public void setKind(BillLineKind kind) { this.kind = kind; }
    public BigDecimal getQty() { return qty; }
    public void setQty(BigDecimal qty) { this.qty = qty; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public void setUnitPrice(BigDecimal unitPrice) { this.unitPrice = unitPrice; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public BigDecimal getCostAmount() { return costAmount; }
    public void setCostAmount(BigDecimal costAmount) { this.costAmount = costAmount; }
    public String getCostMethodUsed() { return costMethodUsed; }
    public void setCostMethodUsed(String costMethodUsed) { this.costMethodUsed = costMethodUsed; }
    public String getCostTrace() { return costTrace; }
    public void setCostTrace(String costTrace) { this.costTrace = costTrace; }
    public String getChargeTrace() { return chargeTrace; }
    public void setChargeTrace(String chargeTrace) { this.chargeTrace = chargeTrace; }
    public String getRestrictionSnapshot() { return restrictionSnapshot; }
    public void setRestrictionSnapshot(String restrictionSnapshot) { this.restrictionSnapshot = restrictionSnapshot; }
    public String getSourceEvent() { return sourceEvent; }
    public void setSourceEvent(String sourceEvent) { this.sourceEvent = sourceEvent; }
    public String getSourceRef() { return sourceRef; }
    public void setSourceRef(String sourceRef) { this.sourceRef = sourceRef; }
    public OffsetDateTime getPostedAt() { return postedAt; }
    public boolean isVoided() { return voided; }
    public void setVoided(boolean voided) { this.voided = voided; }
}
