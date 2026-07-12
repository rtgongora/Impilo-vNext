package zw.gov.mohcc.impilo.msikaflow.persistence.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.OffsetDateTime;
import zw.gov.mohcc.impilo.msikaflow.domain.SettlementStatus;

/**
 * Tracks settlement of payments for an order via MUSHEX payment intents.
 * The id is a ULID string assigned by the application layer.
 */
@Entity
@Table(name = "mf_settlements")
public class SettlementEntity {

    @Id
    @Column(name = "id", nullable = false, length = 26)
    private String id;

    @Column(name = "order_id", nullable = false)
    private String orderId;

    @Column(name = "mushex_payment_intent_id", nullable = false, unique = true)
    private String mushexPaymentIntentId;

    @JdbcTypeCode(SqlTypes.JSON)


    @Column(name = "splits", columnDefinition = "jsonb")
    private String splits;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private SettlementStatus status;

    /** COSTA ChargeRecord id (money-truth linkage), set from costa.charge.created. */
    @Column(name = "costa_charge_ref", length = 64)
    private String costaChargeRef;

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

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public String getMushexPaymentIntentId() {
        return mushexPaymentIntentId;
    }

    public void setMushexPaymentIntentId(String mushexPaymentIntentId) {
        this.mushexPaymentIntentId = mushexPaymentIntentId;
    }

    public String getSplits() {
        return splits;
    }

    public void setSplits(String splits) {
        this.splits = splits;
    }

    public SettlementStatus getStatus() {
        return status;
    }

    public void setStatus(SettlementStatus status) {
        this.status = status;
    }

    public String getCostaChargeRef() {
        return costaChargeRef;
    }

    public void setCostaChargeRef(String costaChargeRef) {
        this.costaChargeRef = costaChargeRef;
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
