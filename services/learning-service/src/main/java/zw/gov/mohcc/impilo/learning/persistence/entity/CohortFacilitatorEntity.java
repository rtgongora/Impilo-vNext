package zw.gov.mohcc.impilo.learning.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Cohort ↔ facilitator assignment (V016): the missing class-teacher linkage. */
@Entity
@Table(
        name = "lrn_cohort_facilitator",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_lrn_cohort_facilitator",
                        columnNames = {"cohort_id", "facilitator_id", "role_in_cohort"})
        })
public class CohortFacilitatorEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "cohort_id", nullable = false)
    private UUID cohortId;

    @Column(name = "facilitator_id", nullable = false)
    private UUID facilitatorId;

    @Column(name = "role_in_cohort", nullable = false, length = 32)
    private String roleInCohort = "LEAD";

    @Column(name = "assigned_by", length = 255)
    private String assignedBy;

    @Column(name = "assigned_at", nullable = false)
    private OffsetDateTime assignedAt;

    @PrePersist
    void prePersist() {
        if (id == null) id = UUID.randomUUID();
        if (assignedAt == null) assignedAt = OffsetDateTime.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public UUID getCohortId() { return cohortId; }
    public void setCohortId(UUID cohortId) { this.cohortId = cohortId; }
    public UUID getFacilitatorId() { return facilitatorId; }
    public void setFacilitatorId(UUID facilitatorId) { this.facilitatorId = facilitatorId; }
    public String getRoleInCohort() { return roleInCohort; }
    public void setRoleInCohort(String roleInCohort) { this.roleInCohort = roleInCohort; }
    public String getAssignedBy() { return assignedBy; }
    public void setAssignedBy(String assignedBy) { this.assignedBy = assignedBy; }
    public OffsetDateTime getAssignedAt() { return assignedAt; }
    public void setAssignedAt(OffsetDateTime assignedAt) { this.assignedAt = assignedAt; }
}
