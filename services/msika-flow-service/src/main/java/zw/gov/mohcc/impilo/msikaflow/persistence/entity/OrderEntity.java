package zw.gov.mohcc.impilo.msikaflow.persistence.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;
import zw.gov.mohcc.impilo.msikaflow.domain.ActorType;
import zw.gov.mohcc.impilo.msikaflow.domain.OrderStatus;
import zw.gov.mohcc.impilo.msikaflow.domain.OrderType;

/**
 * Represents a marketplace order placed by a patient, provider, or vendor.
 * The orderId is a ULID string assigned by the application layer, not auto-generated.
 */
@Entity
@Table(name = "mf_orders")
public class OrderEntity {

    @Id
    @Column(name = "order_id", nullable = false, length = 26)
    private String orderId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "actor_id", nullable = false)
    private String actorId;

    @Enumerated(EnumType.STRING)
    @Column(name = "actor_type", nullable = false)
    private ActorType actorType;

    @Column(name = "patient_cpid")
    private String patientCpid;

    @Enumerated(EnumType.STRING)
    @Column(name = "order_type", nullable = false)
    private OrderType orderType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private OrderStatus status;

    @Column(name = "facility_id")
    private UUID facilityId;

    @Column(name = "facility_ref", length = 255)
    private String facilityRef;

    @Column(name = "vendor_id")
    private UUID vendorId;

    @Column(name = "vendor_ref", length = 255)
    private String vendorRef;

    @Column(name = "provider_ref", length = 255)
    private String providerRef;

    @Column(name = "amount_total", nullable = false, precision = 14, scale = 2)
    private BigDecimal amountTotal;

    @Column(name = "currency", nullable = false)
    private String currency = "ZWG";

    @JdbcTypeCode(SqlTypes.JSON)


    @Column(name = "price_snapshot", columnDefinition = "jsonb")
    private String priceSnapshot;

    @JdbcTypeCode(SqlTypes.JSON)


    @Column(name = "restrictions_snapshot", columnDefinition = "jsonb")
    private String restrictionsSnapshot;

    @Column(name = "idempotency_key", unique = true)
    private String idempotencyKey;

    /** Verified share-slip token reference for Rx orders (RxTokenService). */
    @Column(name = "rx_token_ref", length = 255)
    private String rxTokenRef;

    /** Subject type reported by share-slip verification (e.g. PRESCRIPTION). */
    @Column(name = "rx_token_subject_type", length = 64)
    private String rxTokenSubjectType;

    @Version
    @Column(name = "lock_version")
    private int lockVersion;

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

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public String getRxTokenRef() {
        return rxTokenRef;
    }

    public void setRxTokenRef(String rxTokenRef) {
        this.rxTokenRef = rxTokenRef;
    }

    public String getRxTokenSubjectType() {
        return rxTokenSubjectType;
    }

    public void setRxTokenSubjectType(String rxTokenSubjectType) {
        this.rxTokenSubjectType = rxTokenSubjectType;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }

    public String getActorId() {
        return actorId;
    }

    public void setActorId(String actorId) {
        this.actorId = actorId;
    }

    public ActorType getActorType() {
        return actorType;
    }

    public void setActorType(ActorType actorType) {
        this.actorType = actorType;
    }

    public String getPatientCpid() {
        return patientCpid;
    }

    public void setPatientCpid(String patientCpid) {
        this.patientCpid = patientCpid;
    }

    public OrderType getOrderType() {
        return orderType;
    }

    public void setOrderType(OrderType orderType) {
        this.orderType = orderType;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public void setStatus(OrderStatus status) {
        this.status = status;
    }

    public UUID getFacilityId() {
        return facilityId;
    }

    public void setFacilityId(UUID facilityId) {
        this.facilityId = facilityId;
    }

    public String getFacilityRef() { return facilityRef; }
    public void setFacilityRef(String facilityRef) { this.facilityRef = facilityRef; }

    public UUID getVendorId() {
        return vendorId;
    }

    public void setVendorId(UUID vendorId) {
        this.vendorId = vendorId;
    }

    public String getVendorRef() { return vendorRef; }
    public void setVendorRef(String vendorRef) { this.vendorRef = vendorRef; }

    public String getProviderRef() { return providerRef; }
    public void setProviderRef(String providerRef) { this.providerRef = providerRef; }

    public BigDecimal getAmountTotal() {
        return amountTotal;
    }

    public void setAmountTotal(BigDecimal amountTotal) {
        this.amountTotal = amountTotal;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public String getPriceSnapshot() {
        return priceSnapshot;
    }

    public void setPriceSnapshot(String priceSnapshot) {
        this.priceSnapshot = priceSnapshot;
    }

    public String getRestrictionsSnapshot() {
        return restrictionsSnapshot;
    }

    public void setRestrictionsSnapshot(String restrictionsSnapshot) {
        this.restrictionsSnapshot = restrictionsSnapshot;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public int getLockVersion() {
        return lockVersion;
    }

    public void setLockVersion(int lockVersion) {
        this.lockVersion = lockVersion;
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
