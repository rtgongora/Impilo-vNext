package zw.gov.mohcc.impilo.rito.persistence.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Derived reputation aggregate — NEVER a source of truth. Recomputed by the aggregator
 * (RW4) from {@link ProviderRatingEntity} + {@link RatingDomainScoreEntity}. Public reads
 * use the verified_* fields only. Null facility/service point = an "all" rollup.
 */
@Entity
@Table(name = "rit_provider_reputation_summary", schema = "rito")
public class ProviderReputationSummaryEntity {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "provider_public_id", nullable = false, length = 64)
    private String providerPublicId;

    @Column(name = "facility_id")
    private UUID facilityId;

    @Column(name = "service_point_id")
    private UUID servicePointId;

    @Column(name = "domain", nullable = false, length = 32)
    private String domain;

    @Column(name = "reporting_period", nullable = false, length = 16)
    private String reportingPeriod;

    @Column(name = "rating_count", nullable = false)
    private Integer ratingCount = 0;

    @Column(name = "verified_count", nullable = false)
    private Integer verifiedCount = 0;

    @Column(name = "mean_score")
    private BigDecimal meanScore;

    @Column(name = "verified_mean_score")
    private BigDecimal verifiedMeanScore;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "distribution", nullable = false, columnDefinition = "jsonb")
    private String distribution = "{}";

    @Column(name = "last_recomputed_at", nullable = false)
    private OffsetDateTime lastRecomputedAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (lastRecomputedAt == null) {
            lastRecomputedAt = now;
        }
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public String getProviderPublicId() { return providerPublicId; }
    public void setProviderPublicId(String providerPublicId) { this.providerPublicId = providerPublicId; }
    public UUID getFacilityId() { return facilityId; }
    public void setFacilityId(UUID facilityId) { this.facilityId = facilityId; }
    public UUID getServicePointId() { return servicePointId; }
    public void setServicePointId(UUID servicePointId) { this.servicePointId = servicePointId; }
    public String getDomain() { return domain; }
    public void setDomain(String domain) { this.domain = domain; }
    public String getReportingPeriod() { return reportingPeriod; }
    public void setReportingPeriod(String reportingPeriod) { this.reportingPeriod = reportingPeriod; }
    public Integer getRatingCount() { return ratingCount; }
    public void setRatingCount(Integer ratingCount) { this.ratingCount = ratingCount; }
    public Integer getVerifiedCount() { return verifiedCount; }
    public void setVerifiedCount(Integer verifiedCount) { this.verifiedCount = verifiedCount; }
    public BigDecimal getMeanScore() { return meanScore; }
    public void setMeanScore(BigDecimal meanScore) { this.meanScore = meanScore; }
    public BigDecimal getVerifiedMeanScore() { return verifiedMeanScore; }
    public void setVerifiedMeanScore(BigDecimal verifiedMeanScore) { this.verifiedMeanScore = verifiedMeanScore; }
    public String getDistribution() { return distribution; }
    public void setDistribution(String distribution) { this.distribution = distribution; }
    public OffsetDateTime getLastRecomputedAt() { return lastRecomputedAt; }
    public void setLastRecomputedAt(OffsetDateTime lastRecomputedAt) { this.lastRecomputedAt = lastRecomputedAt; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
