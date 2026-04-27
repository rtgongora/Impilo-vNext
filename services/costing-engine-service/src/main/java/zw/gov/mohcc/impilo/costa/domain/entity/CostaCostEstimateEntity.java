package zw.gov.mohcc.impilo.costa.domain.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import zw.gov.mohcc.impilo.costa.domain.enums.BillingTimingMode;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "costa_cost_estimates")
public class CostaCostEstimateEntity {

    @Id
    @Column(name = "cost_estimate_id")
    private UUID costEstimateId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "tariff_list_id", nullable = false)
    private Long tariffListId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "request_payload", nullable = false, columnDefinition = "jsonb")
    private String requestPayload;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "response_payload", nullable = false, columnDefinition = "jsonb")
    private String responsePayload;

    @Column(name = "audit_reference", length = 120)
    private String auditReference;

    @Enumerated(EnumType.STRING)
    @Column(name = "billing_timing_mode", length = 40)
    private BillingTimingMode billingTimingMode;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (costEstimateId == null) {
            costEstimateId = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }

    public UUID getCostEstimateId() {
        return costEstimateId;
    }

    public void setCostEstimateId(UUID costEstimateId) {
        this.costEstimateId = costEstimateId;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }

    public Long getTariffListId() {
        return tariffListId;
    }

    public void setTariffListId(Long tariffListId) {
        this.tariffListId = tariffListId;
    }

    public String getRequestPayload() {
        return requestPayload;
    }

    public void setRequestPayload(String requestPayload) {
        this.requestPayload = requestPayload;
    }

    public String getResponsePayload() {
        return responsePayload;
    }

    public void setResponsePayload(String responsePayload) {
        this.responsePayload = responsePayload;
    }

    public String getAuditReference() {
        return auditReference;
    }

    public void setAuditReference(String auditReference) {
        this.auditReference = auditReference;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public BillingTimingMode getBillingTimingMode() {
        return billingTimingMode;
    }

    public void setBillingTimingMode(BillingTimingMode billingTimingMode) {
        this.billingTimingMode = billingTimingMode;
    }
}
