package zw.gov.mohcc.impilo.costa.domain.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/** A funding source / donor / grant that pays for budget lines. */
@Entity
@Table(name = "costa_funding_source")
public class FundingSourceEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "funding_source_id", nullable = false, unique = true)
    private UUID fundingSourceId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "name", nullable = false, length = 256)
    private String name;

    @Column(name = "code", nullable = false, length = 64)
    private String code;

    @Column(name = "source_type", nullable = false, length = 16)
    private String sourceType;

    @Column(name = "donor_name", length = 256)
    private String donorName;

    @Column(name = "grant_reference", length = 128)
    private String grantReference;

    @Column(name = "ceiling_amount", precision = 18, scale = 2)
    private BigDecimal ceilingAmount;

    @Column(name = "absorbed_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal absorbedAmount = BigDecimal.ZERO;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "status", nullable = false, length = 16)
    private String status = "ACTIVE";

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (fundingSourceId == null) fundingSourceId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public UUID getFundingSourceId() { return fundingSourceId; }
    public void setFundingSourceId(UUID fundingSourceId) { this.fundingSourceId = fundingSourceId; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getSourceType() { return sourceType; }
    public void setSourceType(String sourceType) { this.sourceType = sourceType; }
    public String getDonorName() { return donorName; }
    public void setDonorName(String donorName) { this.donorName = donorName; }
    public String getGrantReference() { return grantReference; }
    public void setGrantReference(String grantReference) { this.grantReference = grantReference; }
    public BigDecimal getCeilingAmount() { return ceilingAmount; }
    public void setCeilingAmount(BigDecimal ceilingAmount) { this.ceilingAmount = ceilingAmount; }
    public BigDecimal getAbsorbedAmount() { return absorbedAmount; }
    public void setAbsorbedAmount(BigDecimal absorbedAmount) { this.absorbedAmount = absorbedAmount; }
    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }
    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
