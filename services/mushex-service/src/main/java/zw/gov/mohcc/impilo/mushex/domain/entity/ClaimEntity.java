package zw.gov.mohcc.impilo.mushex.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import zw.gov.mohcc.impilo.mushex.domain.enums.ClaimStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "mushex_claims")
public class ClaimEntity {

    @Id
    @Column(name = "claim_id", length = 26)
    private String claimId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "facility_id", nullable = false)
    private UUID facilityId;

    @Column(name = "bill_id", nullable = false)
    private String billId;

    @Column(name = "insurer_id", nullable = false)
    private String insurerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ClaimStatus status;

    @Column(name = "totals", columnDefinition = "jsonb")
    private String totals;

    @Column(name = "submitted_at")
    private OffsetDateTime submittedAt;

    @Column(name = "adjudicated_at")
    private OffsetDateTime adjudicatedAt;

    @Column(name = "external_ref")
    private String externalRef;

    @Column(name = "patient_cpid")
    private String patientCpid;

    @Column(name = "plan_code", length = 64)
    private String planCode;

    @Column(name = "service_code", length = 64)
    private String serviceCode;

    @Column(name = "preauth_required", nullable = false)
    private boolean preauthRequired = true;

    @Column(name = "denial_reason", length = 500)
    private String denialReason;

    @Column(name = "coverage_eligibility_ref", length = 255)
    private String coverageEligibilityRef;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = OffsetDateTime.now();
    }

    public String getClaimId() {
        return claimId;
    }

    public void setClaimId(String claimId) {
        this.claimId = claimId;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }

    public UUID getFacilityId() {
        return facilityId;
    }

    public void setFacilityId(UUID facilityId) {
        this.facilityId = facilityId;
    }

    public String getBillId() {
        return billId;
    }

    public void setBillId(String billId) {
        this.billId = billId;
    }

    public String getInsurerId() {
        return insurerId;
    }

    public void setInsurerId(String insurerId) {
        this.insurerId = insurerId;
    }

    public ClaimStatus getStatus() {
        return status;
    }

    public void setStatus(ClaimStatus status) {
        this.status = status;
    }

    public String getTotals() {
        return totals;
    }

    public void setTotals(String totals) {
        this.totals = totals;
    }

    public OffsetDateTime getSubmittedAt() {
        return submittedAt;
    }

    public void setSubmittedAt(OffsetDateTime submittedAt) {
        this.submittedAt = submittedAt;
    }

    public OffsetDateTime getAdjudicatedAt() {
        return adjudicatedAt;
    }

    public void setAdjudicatedAt(OffsetDateTime adjudicatedAt) {
        this.adjudicatedAt = adjudicatedAt;
    }

    public String getExternalRef() {
        return externalRef;
    }

    public void setExternalRef(String externalRef) {
        this.externalRef = externalRef;
    }

    public String getPatientCpid() {
        return patientCpid;
    }

    public void setPatientCpid(String patientCpid) {
        this.patientCpid = patientCpid;
    }

    public String getPlanCode() {
        return planCode;
    }

    public void setPlanCode(String planCode) {
        this.planCode = planCode;
    }

    public String getServiceCode() {
        return serviceCode;
    }

    public void setServiceCode(String serviceCode) {
        this.serviceCode = serviceCode;
    }

    public boolean isPreauthRequired() {
        return preauthRequired;
    }

    public void setPreauthRequired(boolean preauthRequired) {
        this.preauthRequired = preauthRequired;
    }

    public String getDenialReason() {
        return denialReason;
    }

    public void setDenialReason(String denialReason) {
        this.denialReason = denialReason;
    }

    public String getCoverageEligibilityRef() {
        return coverageEligibilityRef;
    }

    public void setCoverageEligibilityRef(String coverageEligibilityRef) {
        this.coverageEligibilityRef = coverageEligibilityRef;
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
