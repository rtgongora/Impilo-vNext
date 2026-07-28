package zw.gov.mohcc.impilo.surgery.persistence.entity;

import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * A surgical episode (S1) — a management-course record that ATTACHES TO PCT journeys and never
 * contains them. See V002's own header for the full CC-2 amendment rationale.
 */
@Entity
@Table(name = "surgical_episode", schema = "surgery")
public class SurgicalEpisodeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "subject_cpid", nullable = false, length = 64)
    private String subjectCpid;

    @Column(name = "journey_id", length = 64)
    private String journeyId;

    @Column(name = "encounter_id")
    private UUID encounterId;

    @Column(name = "procedure_episode_ref")
    private UUID procedureEpisodeRef;

    @Column(name = "pct_problem_ref")
    private UUID pctProblemRef;

    @Column(name = "pct_problem_contributed_at")
    private OffsetDateTime pctProblemContributedAt;

    @Column(name = "operative_indication", nullable = false, columnDefinition = "text")
    private String operativeIndication;

    @Column(name = "non_operative_options_considered", columnDefinition = "text")
    private String nonOperativeOptionsConsidered;

    @Column(nullable = false, length = 24)
    private String status = "ASSESSMENT";

    @Column(length = 48)
    private String specialty;

    @Column(name = "created_by")
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    void prePersist() {
        OffsetDateTime now = OffsetDateTime.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID v) { this.id = v; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID v) { this.tenantId = v; }
    public String getSubjectCpid() { return subjectCpid; }
    public void setSubjectCpid(String v) { this.subjectCpid = v; }
    public String getJourneyId() { return journeyId; }
    public void setJourneyId(String v) { this.journeyId = v; }
    public UUID getEncounterId() { return encounterId; }
    public void setEncounterId(UUID v) { this.encounterId = v; }
    public UUID getProcedureEpisodeRef() { return procedureEpisodeRef; }
    public void setProcedureEpisodeRef(UUID v) { this.procedureEpisodeRef = v; }
    public UUID getPctProblemRef() { return pctProblemRef; }
    public void setPctProblemRef(UUID v) { this.pctProblemRef = v; }
    public OffsetDateTime getPctProblemContributedAt() { return pctProblemContributedAt; }
    public void setPctProblemContributedAt(OffsetDateTime v) { this.pctProblemContributedAt = v; }
    public String getOperativeIndication() { return operativeIndication; }
    public void setOperativeIndication(String v) { this.operativeIndication = v; }
    public String getNonOperativeOptionsConsidered() { return nonOperativeOptionsConsidered; }
    public void setNonOperativeOptionsConsidered(String v) { this.nonOperativeOptionsConsidered = v; }
    public String getStatus() { return status; }
    public void setStatus(String v) { this.status = v; }
    public String getSpecialty() { return specialty; }
    public void setSpecialty(String v) { this.specialty = v; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String v) { this.createdBy = v; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
