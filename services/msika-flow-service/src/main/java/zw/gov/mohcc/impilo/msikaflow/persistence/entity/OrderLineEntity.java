package zw.gov.mohcc.impilo.msikaflow.persistence.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import zw.gov.mohcc.impilo.msikaflow.domain.FulfillmentMode;
import zw.gov.mohcc.impilo.msikaflow.domain.LineItemKind;
import zw.gov.mohcc.impilo.msikaflow.domain.LineItemState;

/**
 * Represents a single line item within a marketplace order.
 * The lineId is a ULID string assigned by the application layer.
 */
@Entity
@Table(name = "mf_order_lines")
public class OrderLineEntity {

    @Id
    @Column(name = "line_id", nullable = false, length = 26)
    private String lineId;

    @Column(name = "order_id", nullable = false)
    private String orderId;

    @Column(name = "msika_core_code", nullable = false)
    private String msikaCoreCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false)
    private LineItemKind kind;

    @Column(name = "qty", nullable = false)
    private int qty;

    @Column(name = "unit_price", nullable = false, precision = 14, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "line_total", nullable = false, precision = 14, scale = 2)
    private BigDecimal lineTotal;

    @JdbcTypeCode(SqlTypes.JSON)


    @Column(name = "restrictions", columnDefinition = "jsonb")
    private String restrictions;

    @Enumerated(EnumType.STRING)
    @Column(name = "fulfillment_mode")
    private FulfillmentMode fulfillmentMode;

    @JdbcTypeCode(SqlTypes.JSON)


    @Column(name = "substitution_policy", columnDefinition = "jsonb")
    private String substitutionPolicy;

    @Enumerated(EnumType.STRING)
    @Column(name = "state", nullable = false)
    private LineItemState state;

    @JdbcTypeCode(SqlTypes.JSON)


    @Column(name = "metadata", columnDefinition = "jsonb")
    private String metadata;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
        updatedAt = OffsetDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    // Getters and setters

    public String getLineId() {
        return lineId;
    }

    public void setLineId(String lineId) {
        this.lineId = lineId;
    }

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public String getMsikaCoreCode() {
        return msikaCoreCode;
    }

    public void setMsikaCoreCode(String msikaCoreCode) {
        this.msikaCoreCode = msikaCoreCode;
    }

    public LineItemKind getKind() {
        return kind;
    }

    public void setKind(LineItemKind kind) {
        this.kind = kind;
    }

    public int getQty() {
        return qty;
    }

    public void setQty(int qty) {
        this.qty = qty;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public void setUnitPrice(BigDecimal unitPrice) {
        this.unitPrice = unitPrice;
    }

    public BigDecimal getLineTotal() {
        return lineTotal;
    }

    public void setLineTotal(BigDecimal lineTotal) {
        this.lineTotal = lineTotal;
    }

    public String getRestrictions() {
        return restrictions;
    }

    public void setRestrictions(String restrictions) {
        this.restrictions = restrictions;
    }

    public FulfillmentMode getFulfillmentMode() {
        return fulfillmentMode;
    }

    public void setFulfillmentMode(FulfillmentMode fulfillmentMode) {
        this.fulfillmentMode = fulfillmentMode;
    }

    public String getSubstitutionPolicy() {
        return substitutionPolicy;
    }

    public void setSubstitutionPolicy(String substitutionPolicy) {
        this.substitutionPolicy = substitutionPolicy;
    }

    public LineItemState getState() {
        return state;
    }

    public void setState(LineItemState state) {
        this.state = state;
    }

    public String getMetadata() {
        return metadata;
    }

    public void setMetadata(String metadata) {
        this.metadata = metadata;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
