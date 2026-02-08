package zw.gov.mohcc.impilo.msikaflow.persistence.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import zw.gov.mohcc.impilo.msikaflow.domain.FulfillmentMode;
import zw.gov.mohcc.impilo.msikaflow.domain.RouteStatus;

/**
 * Tracks a fulfillment route for an order — pickup, delivery, facility, or booking.
 * The id is a ULID string assigned by the application layer.
 */
@Entity
@Table(name = "mf_fulfillment_routes")
public class FulfillmentRouteEntity {

    @Id
    @Column(name = "id", nullable = false, length = 26)
    private String id;

    @Column(name = "order_id", nullable = false)
    private String orderId;

    @Enumerated(EnumType.STRING)
    @Column(name = "route_type", nullable = false)
    private FulfillmentMode routeType;

    @Column(name = "target_ref", columnDefinition = "jsonb")
    private String targetRef;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private RouteStatus status;

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

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

    public FulfillmentMode getRouteType() {
        return routeType;
    }

    public void setRouteType(FulfillmentMode routeType) {
        this.routeType = routeType;
    }

    public String getTargetRef() {
        return targetRef;
    }

    public void setTargetRef(String targetRef) {
        this.targetRef = targetRef;
    }

    public RouteStatus getStatus() {
        return status;
    }

    public void setStatus(RouteStatus status) {
        this.status = status;
    }

    public String getLastError() {
        return lastError;
    }

    public void setLastError(String lastError) {
        this.lastError = lastError;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(int retryCount) {
        this.retryCount = retryCount;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
