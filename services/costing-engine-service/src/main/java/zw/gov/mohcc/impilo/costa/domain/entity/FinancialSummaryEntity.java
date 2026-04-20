package zw.gov.mohcc.impilo.costa.domain.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "costa_financial_summaries")
public class FinancialSummaryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "summary_id", nullable = false, unique = true)
    private UUID summaryId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "facility_id")
    private UUID facilityId;

    @Column(name = "period_id")
    private UUID periodId;

    @Column(name = "summary_type", nullable = false, length = 32)
    private String summaryType;

    @Column(name = "total_revenue", precision = 14, scale = 2)
    private BigDecimal totalRevenue = BigDecimal.ZERO;

    @Column(name = "total_cost", precision = 14, scale = 2)
    private BigDecimal totalCost = BigDecimal.ZERO;

    @Column(name = "total_receivable", precision = 14, scale = 2)
    private BigDecimal totalReceivable = BigDecimal.ZERO;

    @Column(name = "total_collected", precision = 14, scale = 2)
    private BigDecimal totalCollected = BigDecimal.ZERO;

    @Column(name = "total_writeoff", precision = 14, scale = 2)
    private BigDecimal totalWriteoff = BigDecimal.ZERO;

    @Column(name = "total_insurer_billed", precision = 14, scale = 2)
    private BigDecimal totalInsurerBilled = BigDecimal.ZERO;

    @Column(name = "total_insurer_paid", precision = 14, scale = 2)
    private BigDecimal totalInsurerPaid = BigDecimal.ZERO;

    @Column(name = "total_patient_billed", precision = 14, scale = 2)
    private BigDecimal totalPatientBilled = BigDecimal.ZERO;

    @Column(name = "total_patient_paid", precision = 14, scale = 2)
    private BigDecimal totalPatientPaid = BigDecimal.ZERO;

    @Column(name = "encounter_count")
    private Integer encounterCount = 0;

    @JdbcTypeCode(SqlTypes.JSON)


    @Column(name = "metadata_json", columnDefinition = "jsonb")
    private String metadataJson = "{}";

    @Column(name = "generated_at", nullable = false)
    private OffsetDateTime generatedAt;

    @PrePersist
    protected void onCreate() {
        if (summaryId == null) {
            summaryId = UUID.randomUUID();
        }
        if (generatedAt == null) {
            generatedAt = OffsetDateTime.now();
        }
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public UUID getSummaryId() { return summaryId; }
    public void setSummaryId(UUID summaryId) { this.summaryId = summaryId; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public UUID getFacilityId() { return facilityId; }
    public void setFacilityId(UUID facilityId) { this.facilityId = facilityId; }
    public UUID getPeriodId() { return periodId; }
    public void setPeriodId(UUID periodId) { this.periodId = periodId; }
    public String getSummaryType() { return summaryType; }
    public void setSummaryType(String summaryType) { this.summaryType = summaryType; }
    public BigDecimal getTotalRevenue() { return totalRevenue; }
    public void setTotalRevenue(BigDecimal totalRevenue) { this.totalRevenue = totalRevenue; }
    public BigDecimal getTotalCost() { return totalCost; }
    public void setTotalCost(BigDecimal totalCost) { this.totalCost = totalCost; }
    public BigDecimal getTotalReceivable() { return totalReceivable; }
    public void setTotalReceivable(BigDecimal totalReceivable) { this.totalReceivable = totalReceivable; }
    public BigDecimal getTotalCollected() { return totalCollected; }
    public void setTotalCollected(BigDecimal totalCollected) { this.totalCollected = totalCollected; }
    public BigDecimal getTotalWriteoff() { return totalWriteoff; }
    public void setTotalWriteoff(BigDecimal totalWriteoff) { this.totalWriteoff = totalWriteoff; }
    public BigDecimal getTotalInsurerBilled() { return totalInsurerBilled; }
    public void setTotalInsurerBilled(BigDecimal totalInsurerBilled) { this.totalInsurerBilled = totalInsurerBilled; }
    public BigDecimal getTotalInsurerPaid() { return totalInsurerPaid; }
    public void setTotalInsurerPaid(BigDecimal totalInsurerPaid) { this.totalInsurerPaid = totalInsurerPaid; }
    public BigDecimal getTotalPatientBilled() { return totalPatientBilled; }
    public void setTotalPatientBilled(BigDecimal totalPatientBilled) { this.totalPatientBilled = totalPatientBilled; }
    public BigDecimal getTotalPatientPaid() { return totalPatientPaid; }
    public void setTotalPatientPaid(BigDecimal totalPatientPaid) { this.totalPatientPaid = totalPatientPaid; }
    public Integer getEncounterCount() { return encounterCount; }
    public void setEncounterCount(Integer encounterCount) { this.encounterCount = encounterCount; }
    public String getMetadataJson() { return metadataJson; }
    public void setMetadataJson(String metadataJson) { this.metadataJson = metadataJson; }
    public OffsetDateTime getGeneratedAt() { return generatedAt; }
    public void setGeneratedAt(OffsetDateTime generatedAt) { this.generatedAt = generatedAt; }
}
