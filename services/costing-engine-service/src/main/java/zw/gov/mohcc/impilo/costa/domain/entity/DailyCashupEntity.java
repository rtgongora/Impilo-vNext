package zw.gov.mohcc.impilo.costa.domain.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "costa_daily_cashup")
public class DailyCashupEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cashup_id", nullable = false, unique = true)
    private UUID cashupId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "facility_id", nullable = false)
    private UUID facilityId;

    @Column(name = "cashier_id", nullable = false, length = 128)
    private String cashierId;

    @Column(name = "shift_id", length = 128)
    private String shiftId;

    @Column(name = "cashup_date", nullable = false)
    private LocalDate cashupDate;

    @Column(name = "cash_collected", precision = 14, scale = 2)
    private BigDecimal cashCollected = BigDecimal.ZERO;

    @Column(name = "card_collected", precision = 14, scale = 2)
    private BigDecimal cardCollected = BigDecimal.ZERO;

    @Column(name = "mobile_collected", precision = 14, scale = 2)
    private BigDecimal mobileCollected = BigDecimal.ZERO;

    @Column(name = "insurance_billed", precision = 14, scale = 2)
    private BigDecimal insuranceBilled = BigDecimal.ZERO;

    @Column(name = "total_collected", precision = 14, scale = 2)
    private BigDecimal totalCollected = BigDecimal.ZERO;

    @Column(name = "total_billed", precision = 14, scale = 2)
    private BigDecimal totalBilled = BigDecimal.ZERO;

    @Column(name = "variance", precision = 14, scale = 2)
    private BigDecimal variance = BigDecimal.ZERO;

    @Column(name = "status", length = 16)
    private String status = "OPEN";

    @Column(name = "closed_at")
    private OffsetDateTime closedAt;

    @Column(name = "supervisor_id", length = 128)
    private String supervisorId;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (cashupId == null) {
            cashupId = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public UUID getCashupId() { return cashupId; }
    public void setCashupId(UUID cashupId) { this.cashupId = cashupId; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public UUID getFacilityId() { return facilityId; }
    public void setFacilityId(UUID facilityId) { this.facilityId = facilityId; }
    public String getCashierId() { return cashierId; }
    public void setCashierId(String cashierId) { this.cashierId = cashierId; }
    public String getShiftId() { return shiftId; }
    public void setShiftId(String shiftId) { this.shiftId = shiftId; }
    public LocalDate getCashupDate() { return cashupDate; }
    public void setCashupDate(LocalDate cashupDate) { this.cashupDate = cashupDate; }
    public BigDecimal getCashCollected() { return cashCollected; }
    public void setCashCollected(BigDecimal cashCollected) { this.cashCollected = cashCollected; }
    public BigDecimal getCardCollected() { return cardCollected; }
    public void setCardCollected(BigDecimal cardCollected) { this.cardCollected = cardCollected; }
    public BigDecimal getMobileCollected() { return mobileCollected; }
    public void setMobileCollected(BigDecimal mobileCollected) { this.mobileCollected = mobileCollected; }
    public BigDecimal getInsuranceBilled() { return insuranceBilled; }
    public void setInsuranceBilled(BigDecimal insuranceBilled) { this.insuranceBilled = insuranceBilled; }
    public BigDecimal getTotalCollected() { return totalCollected; }
    public void setTotalCollected(BigDecimal totalCollected) { this.totalCollected = totalCollected; }
    public BigDecimal getTotalBilled() { return totalBilled; }
    public void setTotalBilled(BigDecimal totalBilled) { this.totalBilled = totalBilled; }
    public BigDecimal getVariance() { return variance; }
    public void setVariance(BigDecimal variance) { this.variance = variance; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public OffsetDateTime getClosedAt() { return closedAt; }
    public void setClosedAt(OffsetDateTime closedAt) { this.closedAt = closedAt; }
    public String getSupervisorId() { return supervisorId; }
    public void setSupervisorId(String supervisorId) { this.supervisorId = supervisorId; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
