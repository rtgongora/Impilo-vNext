package zw.gov.mohcc.impilo.pct.persistence.entity;

import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * A Sorting Desk decision (D5): the visit-type assigned to a journey at the explicit pre-queue sort step.
 */
@Entity
@Table(name = "pct_sorting_records")
public class SortingRecordEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "journey_id", nullable = false)
    private String journeyId;

    @Column(name = "visit_type", nullable = false)
    private String visitType;

    @Column(name = "context", nullable = false)
    private String context;

    @Column(name = "arrival_reason")
    private String arrivalReason;

    @Column(name = "fast_track", nullable = false)
    private boolean fastTrack;

    @Column(name = "sorted_by", nullable = false)
    private String sortedBy;

    @Column(name = "correlation_id")
    private UUID correlationId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public String getJourneyId() { return journeyId; }
    public void setJourneyId(String journeyId) { this.journeyId = journeyId; }

    public String getVisitType() { return visitType; }
    public void setVisitType(String visitType) { this.visitType = visitType; }

    public String getContext() { return context; }
    public void setContext(String context) { this.context = context; }

    public String getArrivalReason() { return arrivalReason; }
    public void setArrivalReason(String arrivalReason) { this.arrivalReason = arrivalReason; }

    public boolean isFastTrack() { return fastTrack; }
    public void setFastTrack(boolean fastTrack) { this.fastTrack = fastTrack; }

    public String getSortedBy() { return sortedBy; }
    public void setSortedBy(String sortedBy) { this.sortedBy = sortedBy; }

    public UUID getCorrelationId() { return correlationId; }
    public void setCorrelationId(UUID correlationId) { this.correlationId = correlationId; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
}
