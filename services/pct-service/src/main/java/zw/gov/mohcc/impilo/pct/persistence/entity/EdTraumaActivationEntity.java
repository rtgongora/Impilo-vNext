package zw.gov.mohcc.impilo.pct.persistence.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "ed_trauma_activation")
public class EdTraumaActivationEntity {

    @Id
    @Column(name = "trauma_id")
    private UUID traumaId;

    @Column(name = "visit_id", nullable = false)
    private UUID visitId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "trauma_level", nullable = false)
    private int traumaLevel;

    @Column(name = "mechanism", nullable = false)
    private String mechanism;

    @Column(name = "team_leader")
    private String teamLeader;

    // Bind the String as JSON so PostgreSQL accepts it into the jsonb column. Without this
    // Hibernate binds a varchar and PG refuses the implicit cast (SQLSTATE 42804), 500-ing every
    // trauma-team activation on Postgres — a latent bug H2 (varchar-tolerant) never surfaced.
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "team_assignments", columnDefinition = "jsonb")
    private String teamAssignmentsJson = "[]";

    @Column(name = "status", nullable = false)
    private String status = "ACTIVE";

    @Column(name = "activated_by", nullable = false)
    private String activatedBy;

    @Column(name = "activated_at", nullable = false)
    private OffsetDateTime activatedAt;

    @Column(name = "ended_at")
    private OffsetDateTime endedAt;

    @Column(name = "outcome")
    private String outcome;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @PrePersist
    protected void onCreate() {
        if (traumaId == null) traumaId = UUID.randomUUID();
        if (activatedAt == null) activatedAt = OffsetDateTime.now();
    }

    public UUID getTraumaId() { return traumaId; }
    public void setTraumaId(UUID traumaId) { this.traumaId = traumaId; }
    public UUID getVisitId() { return visitId; }
    public void setVisitId(UUID visitId) { this.visitId = visitId; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public int getTraumaLevel() { return traumaLevel; }
    public void setTraumaLevel(int traumaLevel) { this.traumaLevel = traumaLevel; }
    public String getMechanism() { return mechanism; }
    public void setMechanism(String mechanism) { this.mechanism = mechanism; }
    public String getTeamLeader() { return teamLeader; }
    public void setTeamLeader(String teamLeader) { this.teamLeader = teamLeader; }
    public String getTeamAssignmentsJson() { return teamAssignmentsJson; }
    public void setTeamAssignmentsJson(String teamAssignmentsJson) { this.teamAssignmentsJson = teamAssignmentsJson; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getActivatedBy() { return activatedBy; }
    public void setActivatedBy(String activatedBy) { this.activatedBy = activatedBy; }
    public OffsetDateTime getActivatedAt() { return activatedAt; }
    public OffsetDateTime getEndedAt() { return endedAt; }
    public void setEndedAt(OffsetDateTime endedAt) { this.endedAt = endedAt; }
    public String getOutcome() { return outcome; }
    public void setOutcome(String outcome) { this.outcome = outcome; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
}
