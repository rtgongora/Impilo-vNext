package zw.gov.mohcc.impilo.inpatient.persistence.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "care_plan_intervention", schema = "inpatient")
public class CarePlanInterventionEntity {

    @Id
    @Column(name = "intervention_id")
    private UUID interventionId;

    @Column(name = "plan_id", nullable = false)
    private UUID planId;

    @Column(name = "goal_id")
    private UUID goalId;

    @Column(name = "category", nullable = false)
    private String category = "ASSESSMENT";

    @Column(name = "description", nullable = false)
    private String description;

    @Column(name = "frequency")
    private String frequency;

    @Column(name = "responsible_role")
    private String responsibleRole;

    @Column(name = "status", nullable = false)
    private String status = "ACTIVE";

    @Column(name = "last_performed")
    private OffsetDateTime lastPerformed;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (interventionId == null) interventionId = UUID.randomUUID();
        if (createdAt == null) createdAt = OffsetDateTime.now();
    }

    public UUID getInterventionId() { return interventionId; }
    public void setInterventionId(UUID interventionId) { this.interventionId = interventionId; }
    public UUID getPlanId() { return planId; }
    public void setPlanId(UUID planId) { this.planId = planId; }
    public UUID getGoalId() { return goalId; }
    public void setGoalId(UUID goalId) { this.goalId = goalId; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getFrequency() { return frequency; }
    public void setFrequency(String frequency) { this.frequency = frequency; }
    public String getResponsibleRole() { return responsibleRole; }
    public void setResponsibleRole(String responsibleRole) { this.responsibleRole = responsibleRole; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public OffsetDateTime getLastPerformed() { return lastPerformed; }
    public void setLastPerformed(OffsetDateTime lastPerformed) { this.lastPerformed = lastPerformed; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
