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
 * A course of medical care over time.
 *
 * <p>Not an encounter (one contact), not a journey (one facility visit), and not
 * {@code pct.emergency_episode} (one presentation to emergency care) — which this links to via
 * {@link #emergencyEpisodeId} rather than replaces. See
 * {@code V101__medical_episode.sql} for the full reasoning.</p>
 */
@Entity
@Table(name = "pct_medical_episodes")
public class MedicalEpisodeEntity {

    @Id
    @Column(name = "episode_id")
    private UUID episodeId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "subject_cpid", nullable = false)
    private String subjectCpid;

    @Column(name = "episode_type", nullable = false)
    private String episodeType;

    @Column(name = "status", nullable = false)
    private String status = "ACTIVE";

    @Column(name = "title", nullable = false)
    private String title;

    /** NULL means nobody has taken this episode — a finding, not a blank. */
    @Column(name = "responsible_service")
    private String responsibleService;

    @Column(name = "responsible_actor")
    private String responsibleActor;

    @Column(name = "managing_facility_id")
    private String managingFacilityId;

    @Column(name = "started_on", nullable = false)
    private LocalDate startedOn;

    @Column(name = "ended_on")
    private LocalDate endedOn;

    @Column(name = "end_reason")
    private String endReason;

    /** Soft reference to the emergency lane's episode; no FK, by cross-pack agreement. */
    @Column(name = "emergency_episode_id")
    private UUID emergencyEpisodeId;

    @Column(name = "notes")
    private String notes;

    @Column(name = "created_by", nullable = false)
    private String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (episodeId == null) episodeId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    public UUID getEpisodeId() { return episodeId; }
    public void setEpisodeId(UUID episodeId) { this.episodeId = episodeId; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public String getSubjectCpid() { return subjectCpid; }
    public void setSubjectCpid(String subjectCpid) { this.subjectCpid = subjectCpid; }

    public String getEpisodeType() { return episodeType; }
    public void setEpisodeType(String episodeType) { this.episodeType = episodeType; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getResponsibleService() { return responsibleService; }
    public void setResponsibleService(String responsibleService) { this.responsibleService = responsibleService; }

    public String getResponsibleActor() { return responsibleActor; }
    public void setResponsibleActor(String responsibleActor) { this.responsibleActor = responsibleActor; }

    public String getManagingFacilityId() { return managingFacilityId; }
    public void setManagingFacilityId(String managingFacilityId) { this.managingFacilityId = managingFacilityId; }

    public LocalDate getStartedOn() { return startedOn; }
    public void setStartedOn(LocalDate startedOn) { this.startedOn = startedOn; }

    public LocalDate getEndedOn() { return endedOn; }
    public void setEndedOn(LocalDate endedOn) { this.endedOn = endedOn; }

    public String getEndReason() { return endReason; }
    public void setEndReason(String endReason) { this.endReason = endReason; }

    public UUID getEmergencyEpisodeId() { return emergencyEpisodeId; }
    public void setEmergencyEpisodeId(UUID emergencyEpisodeId) { this.emergencyEpisodeId = emergencyEpisodeId; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
