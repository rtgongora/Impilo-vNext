package zw.gov.mohcc.impilo.rito.persistence.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * One score for one measure within one of the four non-blendable rating domains
 * (CLIENT_EXPERIENCE / ACCESS_PROCESS / PROFESSIONAL_QUALITY / SAFETY_ACCOUNTABILITY).
 * Domains are stored separately and never collapsed into a single number.
 */
@Entity
@Table(name = "rit_rating_domain_score", schema = "rito")
public class RatingDomainScoreEntity {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "rating_id", nullable = false)
    private UUID ratingId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "domain", nullable = false, length = 32)
    private String domain;

    @Column(name = "measure", nullable = false, length = 64)
    private String measure;

    @Column(name = "score", nullable = false)
    private BigDecimal score;

    @Column(name = "scale", nullable = false, length = 16)
    private String scale = "1-5";

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getRatingId() { return ratingId; }
    public void setRatingId(UUID ratingId) { this.ratingId = ratingId; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public String getDomain() { return domain; }
    public void setDomain(String domain) { this.domain = domain; }
    public String getMeasure() { return measure; }
    public void setMeasure(String measure) { this.measure = measure; }
    public BigDecimal getScore() { return score; }
    public void setScore(BigDecimal score) { this.score = score; }
    public String getScale() { return scale; }
    public void setScale(String scale) { this.scale = scale; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
