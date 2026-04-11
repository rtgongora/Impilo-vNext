package zw.gov.mohcc.impilo.clinical.persistence.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(schema = "clinical", name = "override_records")
public class OverrideRecordEntity {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "recommendation_trace_id", nullable = false)
    private UUID recommendationTraceId;

    @Column(name = "override_reason", nullable = false, columnDefinition = "TEXT")
    private String overrideReason;

    @Column(name = "overridden_by", nullable = false)
    private String overriddenBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public void setRecommendationTraceId(UUID recommendationTraceId) {
        this.recommendationTraceId = recommendationTraceId;
    }

    public void setOverrideReason(String overrideReason) {
        this.overrideReason = overrideReason;
    }

    public void setOverriddenBy(String overriddenBy) {
        this.overriddenBy = overriddenBy;
    }

    public UUID getId() {
        return id;
    }

    public UUID getRecommendationTraceId() {
        return recommendationTraceId;
    }
}
