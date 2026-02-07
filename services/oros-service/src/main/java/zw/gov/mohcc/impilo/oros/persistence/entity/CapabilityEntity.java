package zw.gov.mohcc.impilo.oros.persistence.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;
import zw.gov.mohcc.impilo.oros.domain.AdapterMode;
import zw.gov.mohcc.impilo.oros.domain.OrderType;

/**
 * Defines the fulfillment capability of a tenant/facility for a specific
 * order type. Controls whether orders are routed internally, via an
 * external adapter, or in hybrid mode.
 */
@Entity
@Table(name = "oros_capabilities",
       uniqueConstraints = @UniqueConstraint(columnNames = {"tenant_id", "facility_id", "order_type"}))
public class CapabilityEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "capability_id", nullable = false)
    private UUID capabilityId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "facility_id")
    private UUID facilityId;

    @Enumerated(EnumType.STRING)
    @Column(name = "order_type", nullable = false, length = 20)
    private OrderType orderType;

    @Enumerated(EnumType.STRING)
    @Column(name = "adapter_mode", nullable = false, length = 10)
    private AdapterMode adapterMode = AdapterMode.INTERNAL;

    @Column(name = "external_endpoint", length = 500)
    private String externalEndpoint;

    @Column(name = "config", columnDefinition = "jsonb")
    private String config;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
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

    public UUID getCapabilityId() { return capabilityId; }
    public void setCapabilityId(UUID capabilityId) { this.capabilityId = capabilityId; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public UUID getFacilityId() { return facilityId; }
    public void setFacilityId(UUID facilityId) { this.facilityId = facilityId; }

    public OrderType getOrderType() { return orderType; }
    public void setOrderType(OrderType orderType) { this.orderType = orderType; }

    public AdapterMode getAdapterMode() { return adapterMode; }
    public void setAdapterMode(AdapterMode adapterMode) { this.adapterMode = adapterMode; }

    public String getExternalEndpoint() { return externalEndpoint; }
    public void setExternalEndpoint(String externalEndpoint) { this.externalEndpoint = externalEndpoint; }

    public String getConfig() { return config; }
    public void setConfig(String config) { this.config = config; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
