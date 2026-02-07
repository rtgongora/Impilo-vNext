package zw.gov.mohcc.impilo.pct.persistence.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Represents a discharge case tracking all clearance gates before a patient can leave.
 * Each boolean flag represents a clearance checkpoint that must be satisfied.
 * Blockers are stored as a JSONB array describing any outstanding items.
 */
@Entity
@Table(name = "pct_discharge_cases")
public class DischargeCaseEntity {

    @Id
    @Column(name = "case_id", nullable = false)
    private UUID caseId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "facility_id")
    private UUID facilityId;

    @Column(name = "journey_id", nullable = false)
    private String journeyId;

    @Column(name = "discharge_type", nullable = false)
    private String dischargeType = "CLINICAL";

    @Column(name = "status", nullable = false)
    private String status = "INITIATED";

    @Column(name = "blockers", columnDefinition = "jsonb")
    private String blockers = "[]";

    @Column(name = "clinical_cleared", nullable = false)
    private boolean clinicalCleared;

    @Column(name = "billing_cleared", nullable = false)
    private boolean billingCleared;

    @Column(name = "pharmacy_cleared", nullable = false)
    private boolean pharmacyCleared;

    @Column(name = "payment_cleared", nullable = false)
    private boolean paymentCleared;

    @Column(name = "summary_requested", nullable = false)
    private boolean summaryRequested;

    @Column(name = "summary_cleared", nullable = false)
    private boolean summaryCleared;

    @Column(name = "exit_cleared", nullable = false)
    private boolean exitCleared;

    @Column(name = "closed_at")
    private OffsetDateTime closedAt;

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

    // Getters and setters

    public UUID getId() { return caseId; }
    public void setId(UUID id) { this.caseId = id; }

    public UUID getCaseId() { return caseId; }
    public void setCaseId(UUID caseId) { this.caseId = caseId; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public UUID getFacilityId() { return facilityId; }
    public void setFacilityId(UUID facilityId) { this.facilityId = facilityId; }

    public String getJourneyId() { return journeyId; }
    public void setJourneyId(String journeyId) { this.journeyId = journeyId; }

    public String getDischargeType() { return dischargeType; }
    public void setDischargeType(String dischargeType) { this.dischargeType = dischargeType; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getBlockers() { return blockers; }
    public void setBlockers(String blockers) { this.blockers = blockers; }

    public boolean isClinicalCleared() { return clinicalCleared; }
    public void setClinicalCleared(boolean clinicalCleared) { this.clinicalCleared = clinicalCleared; }

    public boolean isBillingCleared() { return billingCleared; }
    public void setBillingCleared(boolean billingCleared) { this.billingCleared = billingCleared; }

    public boolean isPharmacyCleared() { return pharmacyCleared; }
    public void setPharmacyCleared(boolean pharmacyCleared) { this.pharmacyCleared = pharmacyCleared; }

    public boolean isPaymentCleared() { return paymentCleared; }
    public void setPaymentCleared(boolean paymentCleared) { this.paymentCleared = paymentCleared; }

    public boolean isSummaryRequested() { return summaryRequested; }
    public void setSummaryRequested(boolean summaryRequested) { this.summaryRequested = summaryRequested; }

    public boolean isSummaryCleared() { return summaryCleared; }
    public void setSummaryCleared(boolean summaryCleared) { this.summaryCleared = summaryCleared; }

    public boolean isExitCleared() { return exitCleared; }
    public void setExitCleared(boolean exitCleared) { this.exitCleared = exitCleared; }

    public OffsetDateTime getClosedAt() { return closedAt; }
    public void setClosedAt(OffsetDateTime closedAt) { this.closedAt = closedAt; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }

}
