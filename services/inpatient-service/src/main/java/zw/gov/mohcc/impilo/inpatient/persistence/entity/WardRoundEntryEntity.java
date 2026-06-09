package zw.gov.mohcc.impilo.inpatient.persistence.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "ward_round_entry", schema = "inpatient")
public class WardRoundEntryEntity {

    @Id
    @Column(name = "entry_id", nullable = false)
    private UUID entryId;

    @Column(name = "round_id", nullable = false)
    private UUID roundId;

    @Column(name = "assessment")
    private String assessment;

    @Column(name = "plan")
    private String plan;

    @Column(name = "new_orders")
    private String newOrders;

    @Column(name = "escalation")
    private String escalation;

    @Column(name = "reviewed_by")
    private String reviewedBy;

    @Column(name = "reviewed_at", nullable = false)
    private OffsetDateTime reviewedAt;

    @PrePersist
    protected void onCreate() {
        if (entryId == null) {
            entryId = UUID.randomUUID();
        }
        if (reviewedAt == null) {
            reviewedAt = OffsetDateTime.now();
        }
    }

    public UUID getEntryId() { return entryId; }
    public void setEntryId(UUID entryId) { this.entryId = entryId; }

    public UUID getRoundId() { return roundId; }
    public void setRoundId(UUID roundId) { this.roundId = roundId; }

    public String getAssessment() { return assessment; }
    public void setAssessment(String assessment) { this.assessment = assessment; }

    public String getPlan() { return plan; }
    public void setPlan(String plan) { this.plan = plan; }

    public String getNewOrders() { return newOrders; }
    public void setNewOrders(String newOrders) { this.newOrders = newOrders; }

    public String getEscalation() { return escalation; }
    public void setEscalation(String escalation) { this.escalation = escalation; }

    public String getReviewedBy() { return reviewedBy; }
    public void setReviewedBy(String reviewedBy) { this.reviewedBy = reviewedBy; }

    public OffsetDateTime getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(OffsetDateTime reviewedAt) { this.reviewedAt = reviewedAt; }
}
