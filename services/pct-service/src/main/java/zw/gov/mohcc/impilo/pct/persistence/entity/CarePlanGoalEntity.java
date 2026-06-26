package zw.gov.mohcc.impilo.pct.persistence.entity;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/** A goal attached to an outpatient care plan (PCT). */
@Entity
@Table(name = "pct_care_plan_goals")
public class CarePlanGoalEntity {

    @Id
    @Column(name = "goal_id")
    private UUID goalId;

    @Column(name = "plan_id", nullable = false)
    private UUID planId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "category", nullable = false)
    private String category = "CLINICAL";

    @Column(name = "description", nullable = false)
    private String description;

    @Column(name = "target_value")
    private String targetValue;

    @Column(name = "current_value")
    private String currentValue;

    @Column(name = "priority", nullable = false)
    private String priority = "MEDIUM";

    @Column(name = "status", nullable = false)
    private String status = "IN_PROGRESS";

    @Column(name = "target_date")
    private LocalDate targetDate;

    @Column(name = "achieved_at")
    private OffsetDateTime achievedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (goalId == null) goalId = UUID.randomUUID();
        if (createdAt == null) createdAt = OffsetDateTime.now();
    }

    public UUID getGoalId() { return goalId; }
    public void setGoalId(UUID goalId) { this.goalId = goalId; }

    public UUID getPlanId() { return planId; }
    public void setPlanId(UUID planId) { this.planId = planId; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getTargetValue() { return targetValue; }
    public void setTargetValue(String targetValue) { this.targetValue = targetValue; }

    public String getCurrentValue() { return currentValue; }
    public void setCurrentValue(String currentValue) { this.currentValue = currentValue; }

    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public LocalDate getTargetDate() { return targetDate; }
    public void setTargetDate(LocalDate targetDate) { this.targetDate = targetDate; }

    public OffsetDateTime getAchievedAt() { return achievedAt; }
    public void setAchievedAt(OffsetDateTime achievedAt) { this.achievedAt = achievedAt; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
}
