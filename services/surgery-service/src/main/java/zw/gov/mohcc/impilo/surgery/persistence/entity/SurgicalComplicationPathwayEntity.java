package zw.gov.mohcc.impilo.surgery.persistence.entity;

import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * A complication pathway instance (SB-1, surgical domain pack §15) — recognised, graded, owned,
 * investigated, treated, disclosed and closed. See V005's own header for the boundary against
 * procedures-service's {@code complication_class} (content) and the CC-2 contribution into
 * {@code pct_problems}.
 */
@Entity
@Table(name = "surgical_complication_pathway", schema = "surgery")
public class SurgicalComplicationPathwayEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "surgical_episode_id", nullable = false)
    private UUID surgicalEpisodeId;

    @Column(name = "complication_type", nullable = false, length = 48)
    private String complicationType;

    @Column(name = "recognised_at", nullable = false)
    private OffsetDateTime recognisedAt;

    @Column(name = "recognised_by", nullable = false)
    private String recognisedBy;

    @Column(name = "grade_code", length = 8)
    private String gradeCode;

    @Column(name = "graded_by")
    private String gradedBy;

    @Column(name = "graded_at")
    private OffsetDateTime gradedAt;

    @Column(name = "owned_by")
    private String ownedBy;

    @Column(name = "investigation", columnDefinition = "text")
    private String investigation;

    @Column(name = "treatment", columnDefinition = "text")
    private String treatment;

    @Column(name = "disclosed_at")
    private OffsetDateTime disclosedAt;

    @Column(name = "disclosed_by")
    private String disclosedBy;

    @Column(name = "disclosure_notes", columnDefinition = "text")
    private String disclosureNotes;

    @Column(name = "outcome", columnDefinition = "text")
    private String outcome;

    @Column(nullable = false, length = 16)
    private String status = "OPEN";

    @Column(name = "closed_at")
    private OffsetDateTime closedAt;

    @Column(name = "closed_by")
    private String closedBy;

    @Column(name = "pct_problem_ref")
    private UUID pctProblemRef;

    @Column(name = "pct_problem_contributed_at")
    private OffsetDateTime pctProblemContributedAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    void prePersist() {
        OffsetDateTime now = OffsetDateTime.now();
        if (createdAt == null) createdAt = now;
        if (recognisedAt == null) recognisedAt = now;
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
    public UUID getSurgicalEpisodeId() { return surgicalEpisodeId; }
    public void setSurgicalEpisodeId(UUID v) { this.surgicalEpisodeId = v; }
    public String getComplicationType() { return complicationType; }
    public void setComplicationType(String v) { this.complicationType = v; }
    public OffsetDateTime getRecognisedAt() { return recognisedAt; }
    public void setRecognisedAt(OffsetDateTime v) { this.recognisedAt = v; }
    public String getRecognisedBy() { return recognisedBy; }
    public void setRecognisedBy(String v) { this.recognisedBy = v; }
    public String getGradeCode() { return gradeCode; }
    public void setGradeCode(String v) { this.gradeCode = v; }
    public String getGradedBy() { return gradedBy; }
    public void setGradedBy(String v) { this.gradedBy = v; }
    public OffsetDateTime getGradedAt() { return gradedAt; }
    public void setGradedAt(OffsetDateTime v) { this.gradedAt = v; }
    public String getOwnedBy() { return ownedBy; }
    public void setOwnedBy(String v) { this.ownedBy = v; }
    public String getInvestigation() { return investigation; }
    public void setInvestigation(String v) { this.investigation = v; }
    public String getTreatment() { return treatment; }
    public void setTreatment(String v) { this.treatment = v; }
    public OffsetDateTime getDisclosedAt() { return disclosedAt; }
    public void setDisclosedAt(OffsetDateTime v) { this.disclosedAt = v; }
    public String getDisclosedBy() { return disclosedBy; }
    public void setDisclosedBy(String v) { this.disclosedBy = v; }
    public String getDisclosureNotes() { return disclosureNotes; }
    public void setDisclosureNotes(String v) { this.disclosureNotes = v; }
    public String getOutcome() { return outcome; }
    public void setOutcome(String v) { this.outcome = v; }
    public String getStatus() { return status; }
    public void setStatus(String v) { this.status = v; }
    public OffsetDateTime getClosedAt() { return closedAt; }
    public void setClosedAt(OffsetDateTime v) { this.closedAt = v; }
    public String getClosedBy() { return closedBy; }
    public void setClosedBy(String v) { this.closedBy = v; }
    public UUID getPctProblemRef() { return pctProblemRef; }
    public void setPctProblemRef(UUID v) { this.pctProblemRef = v; }
    public OffsetDateTime getPctProblemContributedAt() { return pctProblemContributedAt; }
    public void setPctProblemContributedAt(OffsetDateTime v) { this.pctProblemContributedAt = v; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
