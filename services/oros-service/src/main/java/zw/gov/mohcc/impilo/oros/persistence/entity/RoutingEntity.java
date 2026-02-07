package zw.gov.mohcc.impilo.oros.persistence.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;
import zw.gov.mohcc.impilo.oros.domain.RouteTarget;
import zw.gov.mohcc.impilo.oros.domain.AdapterMode;
import zw.gov.mohcc.impilo.oros.domain.RouteStatus;

/**
 * Tracks how an order is routed to internal or external fulfilment systems
 * (LIMS, PACS, external pharmacy, etc.) and the current status of that route.
 */
@Entity
@Table(name = "oros_routing")
public class RoutingEntity {

    @Id
    @Column(name = "route_id", nullable = false)
    private UUID routeId;

    @Column(name = "order_id", nullable = false, length = 26)
    private String orderId;

    @Enumerated(EnumType.STRING)
    @Column(name = "route_target")
    private RouteTarget routeTarget;

    @Enumerated(EnumType.STRING)
    @Column(name = "adapter_mode", nullable = false)
    private AdapterMode adapterMode = AdapterMode.INTERNAL;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private RouteStatus status = RouteStatus.PENDING;

    @Column(name = "last_error")
    private String lastError;

    @Column(name = "retry_count", nullable = false)
    private int retryCount = 0;

    @Column(name = "external_ref")
    private String externalRef;

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

    public UUID getId() { return routeId; }
    public void setId(UUID id) { this.routeId = id; }

    public UUID getRouteId() { return routeId; }
    public void setRouteId(UUID routeId) { this.routeId = routeId; }

    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }

    public RouteTarget getRouteTarget() { return routeTarget; }
    public void setRouteTarget(RouteTarget routeTarget) { this.routeTarget = routeTarget; }

    public AdapterMode getAdapterMode() { return adapterMode; }
    public void setAdapterMode(AdapterMode adapterMode) { this.adapterMode = adapterMode; }

    public RouteStatus getStatus() { return status; }
    public void setStatus(RouteStatus status) { this.status = status; }

    public String getLastError() { return lastError; }
    public void setLastError(String lastError) { this.lastError = lastError; }

    public int getRetryCount() { return retryCount; }
    public void setRetryCount(int retryCount) { this.retryCount = retryCount; }

    public String getExternalRef() { return externalRef; }
    public void setExternalRef(String externalRef) { this.externalRef = externalRef; }

    public OffsetDateTime getCreatedAt() { return createdAt; }

    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
