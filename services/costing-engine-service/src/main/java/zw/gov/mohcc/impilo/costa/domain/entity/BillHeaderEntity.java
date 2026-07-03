package zw.gov.mohcc.impilo.costa.domain.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import zw.gov.mohcc.impilo.costa.domain.enums.BillStatus;
import zw.gov.mohcc.impilo.costa.domain.enums.BillType;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "costa_bill_headers")
public class BillHeaderEntity {

    @Id
    @Column(name = "bill_id", length = 26)
    private String billId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "facility_id", nullable = false)
    private UUID facilityId;

    @Column(name = "encounter_id", length = 26)
    private String encounterId;

    @Column(name = "msika_order_id", length = 50)
    private String msikaOrderId;

    @Enumerated(EnumType.STRING)
    @Column(name = "bill_type", nullable = false, length = 30)
    private BillType billType = BillType.ENCOUNTER;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private BillStatus status = BillStatus.DRAFT;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "USD";

    @Column(name = "total_cost", nullable = false, precision = 14, scale = 2)
    private BigDecimal totalCost = BigDecimal.ZERO;

    @Column(name = "total_charge", nullable = false, precision = 14, scale = 2)
    private BigDecimal totalCharge = BigDecimal.ZERO;

    @Column(name = "total_discount", nullable = false, precision = 14, scale = 2)
    private BigDecimal totalDiscount = BigDecimal.ZERO;

    @Column(name = "total_payable", nullable = false, precision = 14, scale = 2)
    private BigDecimal totalPayable = BigDecimal.ZERO;

    @Column(name = "patient_payable", nullable = false, precision = 14, scale = 2)
    private BigDecimal patientPayable = BigDecimal.ZERO;

    @Column(name = "insurer_payable", nullable = false, precision = 14, scale = 2)
    private BigDecimal insurerPayable = BigDecimal.ZERO;

    @Column(name = "subsidy_payable", nullable = false, precision = 14, scale = 2)
    private BigDecimal subsidyPayable = BigDecimal.ZERO;

    @Column(name = "write_off", nullable = false, precision = 14, scale = 2)
    private BigDecimal writeOff = BigDecimal.ZERO;

    @JdbcTypeCode(SqlTypes.JSON)


    @Column(name = "trace_summary", nullable = false, columnDefinition = "jsonb")
    private String traceSummary = "{}";

    @Column(name = "notes")
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "finalized_at")
    private OffsetDateTime finalizedAt;

    @Column(name = "voided_at")
    private OffsetDateTime voidedAt;

    @PrePersist
    protected void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() { updatedAt = OffsetDateTime.now(); }

    public String getBillId() { return billId; }
    public void setBillId(String billId) { this.billId = billId; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public UUID getFacilityId() { return facilityId; }
    public void setFacilityId(UUID facilityId) { this.facilityId = facilityId; }
    public String getEncounterId() { return encounterId; }
    public void setEncounterId(String encounterId) { this.encounterId = encounterId; }
    public String getMsikaOrderId() { return msikaOrderId; }
    public void setMsikaOrderId(String msikaOrderId) { this.msikaOrderId = msikaOrderId; }
    public BillType getBillType() { return billType; }
    public void setBillType(BillType billType) { this.billType = billType; }
    public BillStatus getStatus() { return status; }
    public void setStatus(BillStatus status) { this.status = status; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public BigDecimal getTotalCost() { return totalCost; }
    public void setTotalCost(BigDecimal totalCost) { this.totalCost = totalCost; }
    public BigDecimal getTotalCharge() { return totalCharge; }
    public void setTotalCharge(BigDecimal totalCharge) { this.totalCharge = totalCharge; }
    public BigDecimal getTotalDiscount() { return totalDiscount; }
    public void setTotalDiscount(BigDecimal totalDiscount) { this.totalDiscount = totalDiscount; }
    public BigDecimal getTotalPayable() { return totalPayable; }
    public void setTotalPayable(BigDecimal totalPayable) { this.totalPayable = totalPayable; }
    @Column(name = "coverage_plan_code", length = 64)
    private String coveragePlanCode;

    @Column(name = "coverage_member_id")
    private UUID coverageMemberId;

    @Column(name = "coverage_status", length = 64)
    private String coverageStatus;

    @Column(name = "coverage_checked_at")
    private OffsetDateTime coverageCheckedAt;

    public String getCoveragePlanCode() { return coveragePlanCode; }
    public void setCoveragePlanCode(String coveragePlanCode) { this.coveragePlanCode = coveragePlanCode; }
    public UUID getCoverageMemberId() { return coverageMemberId; }
    public void setCoverageMemberId(UUID coverageMemberId) { this.coverageMemberId = coverageMemberId; }
    public String getCoverageStatus() { return coverageStatus; }
    public void setCoverageStatus(String coverageStatus) { this.coverageStatus = coverageStatus; }
    public OffsetDateTime getCoverageCheckedAt() { return coverageCheckedAt; }
    public void setCoverageCheckedAt(OffsetDateTime coverageCheckedAt) { this.coverageCheckedAt = coverageCheckedAt; }

    public BigDecimal getPatientPayable() { return patientPayable; }
    public void setPatientPayable(BigDecimal patientPayable) { this.patientPayable = patientPayable; }
    public BigDecimal getInsurerPayable() { return insurerPayable; }
    public void setInsurerPayable(BigDecimal insurerPayable) { this.insurerPayable = insurerPayable; }
    public BigDecimal getSubsidyPayable() { return subsidyPayable; }
    public void setSubsidyPayable(BigDecimal subsidyPayable) { this.subsidyPayable = subsidyPayable; }
    public BigDecimal getWriteOff() { return writeOff; }
    public void setWriteOff(BigDecimal writeOff) { this.writeOff = writeOff; }
    public String getTraceSummary() { return traceSummary; }
    public void setTraceSummary(String traceSummary) { this.traceSummary = traceSummary; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public OffsetDateTime getFinalizedAt() { return finalizedAt; }
    public void setFinalizedAt(OffsetDateTime finalizedAt) { this.finalizedAt = finalizedAt; }
    public OffsetDateTime getVoidedAt() { return voidedAt; }
    public void setVoidedAt(OffsetDateTime voidedAt) { this.voidedAt = voidedAt; }
}
