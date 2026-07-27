package zw.gov.mohcc.impilo.pct.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Governed enrolment in a health programme — HIV care or TB treatment.
 *
 * <p>Anchors to the diagnosis in {@code pct_problems} ({@link #anchorProblemId}) rather than
 * re-recording it, and layers a programme lifecycle and outcome on top of the medical episode it may
 * point at ({@link #episodeId}). See {@code V108__programme_enrolment.sql} for the full reasoning.</p>
 */
@Entity
@Table(name = "pct_programme_enrolments")
public class ProgrammeEnrolmentEntity {

    @Id
    @Column(name = "enrolment_id")
    private UUID enrolmentId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "subject_cpid", nullable = false)
    private String subjectCpid;

    @Column(name = "programme", nullable = false)
    private String programme;

    @Column(name = "status", nullable = false)
    private String status = "SCREENING";

    /** NULL unless the enrolment has EXITED. */
    @Column(name = "exit_reason")
    private String exitReason;

    /** The diagnosis this programme treats. Hard FK — an enrolment cannot exist without its problem. */
    @Column(name = "anchor_problem_id", nullable = false)
    private UUID anchorProblemId;

    /** The managing course of care, when one has been opened. */
    @Column(name = "episode_id")
    private UUID episodeId;

    /**
     * PMTCT seam (V111): read-only soft link to the RMNP pregnancy episode this HIV_CARE enrolment
     * relates to. Set on the HIV side only; never written back to the episode. HIV sensitivity
     * travels with this enrolment, not with the pregnancy episode.
     */
    @Column(name = "pregnancy_episode_id")
    private UUID pregnancyEpisodeId;

    @Column(name = "programme_number")
    private String programmeNumber;

    @Column(name = "enrolled_on", nullable = false)
    private LocalDate enrolledOn;

    @Column(name = "exit_on")
    private LocalDate exitOn;

    @Column(name = "managing_facility_id")
    private String managingFacilityId;

    @Column(name = "responsible_actor")
    private String responsibleActor;

    @Column(name = "notes")
    private String notes;

    @Column(name = "client_offline_id")
    private String clientOfflineId;

    @Column(name = "created_by", nullable = false)
    private String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (enrolmentId == null) enrolmentId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    public UUID getEnrolmentId() { return enrolmentId; }
    public void setEnrolmentId(UUID enrolmentId) { this.enrolmentId = enrolmentId; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public String getSubjectCpid() { return subjectCpid; }
    public void setSubjectCpid(String subjectCpid) { this.subjectCpid = subjectCpid; }

    public String getProgramme() { return programme; }
    public void setProgramme(String programme) { this.programme = programme; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getExitReason() { return exitReason; }
    public void setExitReason(String exitReason) { this.exitReason = exitReason; }

    public UUID getAnchorProblemId() { return anchorProblemId; }
    public void setAnchorProblemId(UUID anchorProblemId) { this.anchorProblemId = anchorProblemId; }

    public UUID getEpisodeId() { return episodeId; }
    public void setEpisodeId(UUID episodeId) { this.episodeId = episodeId; }

    public UUID getPregnancyEpisodeId() { return pregnancyEpisodeId; }
    public void setPregnancyEpisodeId(UUID pregnancyEpisodeId) { this.pregnancyEpisodeId = pregnancyEpisodeId; }

    public String getProgrammeNumber() { return programmeNumber; }
    public void setProgrammeNumber(String programmeNumber) { this.programmeNumber = programmeNumber; }

    public LocalDate getEnrolledOn() { return enrolledOn; }
    public void setEnrolledOn(LocalDate enrolledOn) { this.enrolledOn = enrolledOn; }

    public LocalDate getExitOn() { return exitOn; }
    public void setExitOn(LocalDate exitOn) { this.exitOn = exitOn; }

    public String getManagingFacilityId() { return managingFacilityId; }
    public void setManagingFacilityId(String managingFacilityId) { this.managingFacilityId = managingFacilityId; }

    public String getResponsibleActor() { return responsibleActor; }
    public void setResponsibleActor(String responsibleActor) { this.responsibleActor = responsibleActor; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public String getClientOfflineId() { return clientOfflineId; }
    public void setClientOfflineId(String clientOfflineId) { this.clientOfflineId = clientOfflineId; }

    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
