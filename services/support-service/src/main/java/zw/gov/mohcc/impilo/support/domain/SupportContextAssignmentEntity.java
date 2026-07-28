package zw.gov.mohcc.impilo.support.domain;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * A person's scoped support posting (support-service SoR). Distinct from
 * {@link AssignmentEntity}, which is a ticket assignment, not a work-context
 * assignment. Vashandi's vsh_workforce_assignment.support_team_id points at
 * the team this row belongs to; this table records the support authority
 * itself (role, tier via the team, on-call status, dates).
 */
@Entity
@Table(name = "sup_support_assignment")
public class SupportContextAssignmentEntity {
    @Id @Column(name = "assignment_id") private UUID assignmentId;
    @Column(name = "tenant_id", nullable = false) private UUID tenantId;
    @Column(name = "team_id", nullable = false) private UUID teamId;
    @Column(name = "person_health_id", nullable = false, length = 128) private String personHealthId;
    @Column(name = "support_role", nullable = false, length = 64) private String supportRole = "SUPPORT_OFFICER";
    @Column(name = "on_call", nullable = false) private boolean onCall = false;
    @Column(nullable = false, length = 32) private String status = "ACTIVE";
    @Column(name = "start_date", nullable = false) private LocalDate startDate;
    @Column(name = "end_date") private LocalDate endDate;
    @Column(name = "created_at", nullable = false) private OffsetDateTime createdAt;
    @Column(name = "updated_at", nullable = false) private OffsetDateTime updatedAt;
    @Column(name = "created_by") private String createdBy;
    @Column(name = "updated_by") private String updatedBy;

    protected SupportContextAssignmentEntity() {}

    public SupportContextAssignmentEntity(UUID assignmentId, UUID tenantId, UUID teamId, String personHealthId) {
        this.assignmentId = assignmentId;
        this.tenantId = tenantId;
        this.teamId = teamId;
        this.personHealthId = personHealthId;
        this.startDate = LocalDate.now();
        OffsetDateTime now = OffsetDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    public UUID getAssignmentId() { return assignmentId; }
    public UUID getTenantId() { return tenantId; }
    public UUID getTeamId() { return teamId; }
    public String getPersonHealthId() { return personHealthId; }
    public String getSupportRole() { return supportRole; }
    public void setSupportRole(String v) { this.supportRole = v; }
    public boolean isOnCall() { return onCall; }
    public void setOnCall(boolean v) { this.onCall = v; }
    public String getStatus() { return status; }
    public void setStatus(String v) { this.status = v; }
    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate v) { this.startDate = v; }
    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate v) { this.endDate = v; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime v) { this.updatedAt = v; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String v) { this.createdBy = v; }
    public String getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(String v) { this.updatedBy = v; }
}
