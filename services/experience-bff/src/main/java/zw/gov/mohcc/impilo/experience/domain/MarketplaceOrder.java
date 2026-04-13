package zw.gov.mohcc.impilo.experience.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "marketplace_orders")
public class MarketplaceOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(name = "facility_id", nullable = false)
    private UUID facilityId;

    @Column(name = "vendor_id")
    private UUID vendorId;

    @Column(name = "order_number", nullable = false)
    private String orderNumber;

    private String status;

    @Column(name = "total_amount")
    private BigDecimal totalAmount;

    private String currency;

    @Column(columnDefinition = "jsonb")
    private String items;

    @Column(name = "ordered_by")
    private String orderedBy;

    @Column(name = "ordered_at")
    private OffsetDateTime orderedAt;

    @Column(name = "delivered_at")
    private OffsetDateTime deliveredAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected MarketplaceOrder() {}

    public UUID getId() { return id; }
    public String getTenantId() { return tenantId; }
    public UUID getFacilityId() { return facilityId; }
    public UUID getVendorId() { return vendorId; }
    public String getOrderNumber() { return orderNumber; }
    public String getStatus() { return status; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public String getCurrency() { return currency; }
    public String getItems() { return items; }
    public String getOrderedBy() { return orderedBy; }
    public OffsetDateTime getOrderedAt() { return orderedAt; }
    public OffsetDateTime getDeliveredAt() { return deliveredAt; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
