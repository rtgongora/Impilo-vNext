package zw.gov.mohcc.impilo.costa.domain.entity;

import jakarta.persistence.*;
import zw.gov.mohcc.impilo.costa.domain.enums.BillingTimingMode;
import zw.gov.mohcc.impilo.costa.domain.enums.ServiceAccessStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Gate for whether care may proceed before payment, after authorisation, under coverage,
 * exemption, waiver, deferred payment, or emergency override.
 */
@Entity
@Table(name = "costa_service_access_decisions")
public class ServiceAccessDecisionEntity {

    @Id
    @Column(name = "service_access_decision_id")
    private UUID serviceAccessDecisionId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "patient_cpid", length = 80)
    private String patientCpid;

    @Column(name = "encounter_id", length = 80)
    private String encounterId;

    @Column(name = "facility_id")
    private UUID facilityId;

    @Column(name = "workspace_id")
    private UUID workspaceId;

    @Column(name = "requested_service_id", length = 120)
    private String requestedServiceId;

    @Column(name = "requested_service_name", length = 400)
    private String requestedServiceName;

    @Column(name = "requested_service_category", length = 120)
    private String requestedServiceCategory;

    @Column(name = "estimated_cost", precision = 14, scale = 2)
    private BigDecimal estimatedCost;

    @Column(name = "selected_tariff_list_id")
    private Long selectedTariffListId;

    @Column(name = "tariffed_price", precision = 14, scale = 2)
    private BigDecimal tariffedPrice;

    @Enumerated(EnumType.STRING)
    @Column(name = "billing_timing_mode", length = 40)
    private BillingTimingMode billingTimingMode;

    @Enumerated(EnumType.STRING)
    @Column(name = "access_status", nullable = false, length = 50)
    private ServiceAccessStatus accessStatus;

    @Column(name = "required_deposit_amount", precision = 14, scale = 2)
    private BigDecimal requiredDepositAmount;

    @Column(name = "required_payment_amount", precision = 14, scale = 2)
    private BigDecimal requiredPaymentAmount;

    @Column(name = "payer_id", length = 120)
    private String payerId;

    @Column(name = "coverage_reference", length = 200)
    private String coverageReference;

    @Column(name = "exemption_reference", length = 200)
    private String exemptionReference;

    @Column(name = "waiver_reference", length = 200)
    private String waiverReference;

    @Column(name = "authorisation_reference", length = 200)
    private String authorisationReference;

    @Column(name = "promise_to_pay_reference", length = 200)
    private String promiseToPayReference;

    @Column(name = "decision_reason", columnDefinition = "TEXT")
    private String decisionReason;

    @Column(name = "decided_by", length = 120)
    private String decidedBy;

    @Column(name = "decision_timestamp", nullable = false)
    private OffsetDateTime decisionTimestamp;

    @Column(name = "audit_reference", length = 120)
    private String auditReference;

    @PrePersist
    void prePersist() {
        if (serviceAccessDecisionId == null) {
            serviceAccessDecisionId = UUID.randomUUID();
        }
        if (decisionTimestamp == null) {
            decisionTimestamp = OffsetDateTime.now();
        }
    }

    public UUID getServiceAccessDecisionId() {
        return serviceAccessDecisionId;
    }

    public void setServiceAccessDecisionId(UUID serviceAccessDecisionId) {
        this.serviceAccessDecisionId = serviceAccessDecisionId;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }

    public String getPatientCpid() {
        return patientCpid;
    }

    public void setPatientCpid(String patientCpid) {
        this.patientCpid = patientCpid;
    }

    public String getEncounterId() {
        return encounterId;
    }

    public void setEncounterId(String encounterId) {
        this.encounterId = encounterId;
    }

    public UUID getFacilityId() {
        return facilityId;
    }

    public void setFacilityId(UUID facilityId) {
        this.facilityId = facilityId;
    }

    public UUID getWorkspaceId() {
        return workspaceId;
    }

    public void setWorkspaceId(UUID workspaceId) {
        this.workspaceId = workspaceId;
    }

    public String getRequestedServiceId() {
        return requestedServiceId;
    }

    public void setRequestedServiceId(String requestedServiceId) {
        this.requestedServiceId = requestedServiceId;
    }

    public String getRequestedServiceName() {
        return requestedServiceName;
    }

    public void setRequestedServiceName(String requestedServiceName) {
        this.requestedServiceName = requestedServiceName;
    }

    public String getRequestedServiceCategory() {
        return requestedServiceCategory;
    }

    public void setRequestedServiceCategory(String requestedServiceCategory) {
        this.requestedServiceCategory = requestedServiceCategory;
    }

    public BigDecimal getEstimatedCost() {
        return estimatedCost;
    }

    public void setEstimatedCost(BigDecimal estimatedCost) {
        this.estimatedCost = estimatedCost;
    }

    public Long getSelectedTariffListId() {
        return selectedTariffListId;
    }

    public void setSelectedTariffListId(Long selectedTariffListId) {
        this.selectedTariffListId = selectedTariffListId;
    }

    public BigDecimal getTariffedPrice() {
        return tariffedPrice;
    }

    public void setTariffedPrice(BigDecimal tariffedPrice) {
        this.tariffedPrice = tariffedPrice;
    }

    public BillingTimingMode getBillingTimingMode() {
        return billingTimingMode;
    }

    public void setBillingTimingMode(BillingTimingMode billingTimingMode) {
        this.billingTimingMode = billingTimingMode;
    }

    public ServiceAccessStatus getAccessStatus() {
        return accessStatus;
    }

    public void setAccessStatus(ServiceAccessStatus accessStatus) {
        this.accessStatus = accessStatus;
    }

    public BigDecimal getRequiredDepositAmount() {
        return requiredDepositAmount;
    }

    public void setRequiredDepositAmount(BigDecimal requiredDepositAmount) {
        this.requiredDepositAmount = requiredDepositAmount;
    }

    public BigDecimal getRequiredPaymentAmount() {
        return requiredPaymentAmount;
    }

    public void setRequiredPaymentAmount(BigDecimal requiredPaymentAmount) {
        this.requiredPaymentAmount = requiredPaymentAmount;
    }

    public String getPayerId() {
        return payerId;
    }

    public void setPayerId(String payerId) {
        this.payerId = payerId;
    }

    public String getCoverageReference() {
        return coverageReference;
    }

    public void setCoverageReference(String coverageReference) {
        this.coverageReference = coverageReference;
    }

    public String getExemptionReference() {
        return exemptionReference;
    }

    public void setExemptionReference(String exemptionReference) {
        this.exemptionReference = exemptionReference;
    }

    public String getWaiverReference() {
        return waiverReference;
    }

    public void setWaiverReference(String waiverReference) {
        this.waiverReference = waiverReference;
    }

    public String getAuthorisationReference() {
        return authorisationReference;
    }

    public void setAuthorisationReference(String authorisationReference) {
        this.authorisationReference = authorisationReference;
    }

    public String getPromiseToPayReference() {
        return promiseToPayReference;
    }

    public void setPromiseToPayReference(String promiseToPayReference) {
        this.promiseToPayReference = promiseToPayReference;
    }

    public String getDecisionReason() {
        return decisionReason;
    }

    public void setDecisionReason(String decisionReason) {
        this.decisionReason = decisionReason;
    }

    public String getDecidedBy() {
        return decidedBy;
    }

    public void setDecidedBy(String decidedBy) {
        this.decidedBy = decidedBy;
    }

    public OffsetDateTime getDecisionTimestamp() {
        return decisionTimestamp;
    }

    public void setDecisionTimestamp(OffsetDateTime decisionTimestamp) {
        this.decisionTimestamp = decisionTimestamp;
    }

    public String getAuditReference() {
        return auditReference;
    }

    public void setAuditReference(String auditReference) {
        this.auditReference = auditReference;
    }
}
