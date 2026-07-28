package zw.gov.mohcc.impilo.pct.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

/** One item within an order-set instance (pct V206, W7b). Ordered or explicitly declined-with-reason. */
@Entity
@Table(name = "emergency_order_set_item")
public class EmergencyOrderSetItemEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "item_id")
    private UUID itemId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "instance_id", nullable = false)
    private UUID instanceId;

    @Column(name = "order_code", nullable = false)
    private String orderCode;

    @Column(name = "order_label")
    private String orderLabel;

    @Column(name = "order_type")
    private String orderType;

    @Column(name = "status", nullable = false)
    private String status = "PENDING";

    @Column(name = "diagnostic_order_id")
    private UUID diagnosticOrderId;

    @Column(name = "ordered_at")
    private OffsetDateTime orderedAt;

    @Column(name = "ordered_by")
    private String orderedBy;

    @Column(name = "declined_at")
    private OffsetDateTime declinedAt;

    @Column(name = "declined_by")
    private String declinedBy;

    @Column(name = "decline_reason", columnDefinition = "TEXT")
    private String declineReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    public UUID getItemId() { return itemId; }
    public void setItemId(UUID itemId) { this.itemId = itemId; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public UUID getInstanceId() { return instanceId; }
    public void setInstanceId(UUID instanceId) { this.instanceId = instanceId; }
    public String getOrderCode() { return orderCode; }
    public void setOrderCode(String orderCode) { this.orderCode = orderCode; }
    public String getOrderLabel() { return orderLabel; }
    public void setOrderLabel(String orderLabel) { this.orderLabel = orderLabel; }
    public String getOrderType() { return orderType; }
    public void setOrderType(String orderType) { this.orderType = orderType; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public UUID getDiagnosticOrderId() { return diagnosticOrderId; }
    public void setDiagnosticOrderId(UUID diagnosticOrderId) { this.diagnosticOrderId = diagnosticOrderId; }
    public OffsetDateTime getOrderedAt() { return orderedAt; }
    public void setOrderedAt(OffsetDateTime orderedAt) { this.orderedAt = orderedAt; }
    public String getOrderedBy() { return orderedBy; }
    public void setOrderedBy(String orderedBy) { this.orderedBy = orderedBy; }
    public OffsetDateTime getDeclinedAt() { return declinedAt; }
    public void setDeclinedAt(OffsetDateTime declinedAt) { this.declinedAt = declinedAt; }
    public String getDeclinedBy() { return declinedBy; }
    public void setDeclinedBy(String declinedBy) { this.declinedBy = declinedBy; }
    public String getDeclineReason() { return declineReason; }
    public void setDeclineReason(String declineReason) { this.declineReason = declineReason; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
