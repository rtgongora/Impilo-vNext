package zw.gov.mohcc.impilo.coverage.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "cv_provider_contracts")
public class ProviderContractEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "contract_id", nullable = false, unique = true, updatable = false)
    private UUID contractId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "provider_id", nullable = false, length = 128)
    private String providerId;

    @Column(name = "payer_id", nullable = false, length = 128)
    private String payerId;

    @Column(name = "plan_code", length = 64)
    private String planCode;

    @Column(name = "contract_type", nullable = false, length = 32)
    private String contractType = "FEE_FOR_SERVICE";

    @Column(name = "reimbursement_mode", nullable = false, length = 32)
    private String reimbursementMode = "FEE_FOR_SERVICE";

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @Column(name = "rate_schedule_json", columnDefinition = "jsonb")
    private String rateScheduleJson = "{}";

    @Column(name = "terms_json", columnDefinition = "jsonb")
    private String termsJson = "{}";

    @Column(name = "status", nullable = false, length = 16)
    private String status = "ACTIVE";

    @Column(name = "suspended_at")
    private OffsetDateTime suspendedAt;

    @Column(name = "suspension_reason")
    private String suspensionReason;

    @Column(name = "created_by", length = 128)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public ProviderContractEntity() {}

    @PrePersist
    void prePersist() {
        if (contractId == null) {
            contractId = UUID.randomUUID();
        }
        OffsetDateTime now = OffsetDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    public Long getId() { return id; }
    public UUID getContractId() { return contractId; }
    public UUID getTenantId() { return tenantId; }
    public String getProviderId() { return providerId; }
    public void setProviderId(String providerId) { this.providerId = providerId; }
    public String getPayerId() { return payerId; }
    public void setPayerId(String payerId) { this.payerId = payerId; }
    public String getPlanCode() { return planCode; }
    public void setPlanCode(String planCode) { this.planCode = planCode; }
    public String getContractType() { return contractType; }
    public void setContractType(String contractType) { this.contractType = contractType; }
    public String getReimbursementMode() { return reimbursementMode; }
    public void setReimbursementMode(String reimbursementMode) { this.reimbursementMode = reimbursementMode; }
    public LocalDate getEffectiveFrom() { return effectiveFrom; }
    public void setEffectiveFrom(LocalDate effectiveFrom) { this.effectiveFrom = effectiveFrom; }
    public LocalDate getEffectiveTo() { return effectiveTo; }
    public void setEffectiveTo(LocalDate effectiveTo) { this.effectiveTo = effectiveTo; }
    public String getRateScheduleJson() { return rateScheduleJson; }
    public void setRateScheduleJson(String rateScheduleJson) { this.rateScheduleJson = rateScheduleJson; }
    public String getTermsJson() { return termsJson; }
    public void setTermsJson(String termsJson) { this.termsJson = termsJson; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public OffsetDateTime getSuspendedAt() { return suspendedAt; }
    public void setSuspendedAt(OffsetDateTime suspendedAt) { this.suspendedAt = suspendedAt; }
    public String getSuspensionReason() { return suspensionReason; }
    public void setSuspensionReason(String suspensionReason) { this.suspensionReason = suspensionReason; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }

    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
}
