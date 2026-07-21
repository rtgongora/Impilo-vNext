package zw.gov.mohcc.impilo.rito.persistence.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Derived facility-level reputation aggregate (RW8): all PUBLISHED ratings at a facility,
 * across every provider, per domain. Never a source of truth — regenerated from the
 * ratings. Public reads use verified_* only.
 */
@Entity
@Table(name = "rit_facility_reputation_summary", schema = "rito")
public class FacilityReputationSummaryEntity {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "facility_id", nullable = false)
    private UUID facilityId;

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
    public UUID getFacilityId() { return facilityId; }
    public void setFacilityId(UUID facilityId) { this.facilityId = facilityId; }
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
