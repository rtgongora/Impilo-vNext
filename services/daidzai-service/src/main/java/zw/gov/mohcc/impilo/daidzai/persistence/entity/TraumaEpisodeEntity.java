package zw.gov.mohcc.impilo.daidzai.persistence.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Canonical trauma-episode correlation spine (architecture decision #1). One injured patient =
 * one episode; each phase owner keeps its own SoR rows and stamps them with {@code id}. DAIDZAI
 * holds only the correlation identity + subject anchor + lifecycle; peer clinical/blood/EMS truth
 * is referenced by id, never duplicated. Mint is idempotent on {@code (tenant_id, origin_key)}.
 */
@Entity
@Table(name = "dai_trauma_episode", schema = "daidzai",
        uniqueConstraints = @UniqueConstraint(name = "uq_dai_trauma_episode_origin",
                columnNames = {"tenant_id", "origin_key"}))
public class TraumaEpisodeEntity {

    @Id @Column(name = "id") private UUID id;
    @Column(name = "tenant_id", nullable = false) private UUID tenantId;
    @Column(name = "episode_reference", nullable = false, length = 48) private String episodeReference;
    @Column(name = "origin_service", nullable = false, length = 48) private String originService;
    @Column(name = "origin_kind", nullable = false, length = 32) private String originKind;
    @Column(name = "origin_key", nullable = false, length = 128) private String originKey;
    @Column(name = "incident_id") private UUID incidentId;
    @Column(name = "subject_identity_mode", nullable = false, length = 24) private String subjectIdentityMode = "UNKNOWN";
    @Column(name = "subject_cpid", length = 128) private String subjectCpid;
    @Column(name = "subject_temp_ref", length = 128) private String subjectTempRef;
    @Column(name = "status", nullable = false, length = 24) private String status = "OPEN";
    @Column(name = "current_phase", nullable = false, length = 32) private String currentPhase = "INCIDENT";
    @Column(name = "opened_at", nullable = false) private OffsetDateTime openedAt;
    @Column(name = "closed_at") private OffsetDateTime closedAt;
    @Column(name = "close_reason", length = 255) private String closeReason;
    @Column(name = "created_at", nullable = false) private OffsetDateTime createdAt;
    @Column(name = "updated_at", nullable = false) private OffsetDateTime updatedAt;

    // ── V200 acute-episode generalisation: the columns the migration added but the entity never
    //    mapped, which is why V-3 was only structurally closed — the back-link existed in the
    //    schema and no Java field could set it. episodeClass defaults to TRAUMA so existing
    //    create paths (which do not set it) keep their meaning under ddl-auto and Flyway alike.
    @Column(name = "episode_class", nullable = false, length = 24) private String episodeClass = "TRAUMA";
    @Column(name = "pct_journey_id", length = 26) private String pctJourneyId;
    @Column(name = "pct_emergency_episode_id") private UUID pctEmergencyEpisodeId;
    @Column(name = "continuum_linked_at") private OffsetDateTime continuumLinkedAt;
    @Column(name = "merged_into_id") private UUID mergedIntoId;
    @Column(name = "merged_at") private OffsetDateTime mergedAt;
    @Column(name = "merge_reason", length = 255) private String mergeReason;

    @PrePersist void onCreate() {
        if (id == null) id = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        if (createdAt == null) createdAt = now;
        if (openedAt == null) openedAt = now;
        updatedAt = now;
    }
    @PreUpdate void onUpdate() { updatedAt = OffsetDateTime.now(); }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID v) { this.tenantId = v; }
    public String getEpisodeReference() { return episodeReference; }
    public void setEpisodeReference(String v) { this.episodeReference = v; }
    public String getOriginService() { return originService; }
    public void setOriginService(String v) { this.originService = v; }
    public String getOriginKind() { return originKind; }
    public void setOriginKind(String v) { this.originKind = v; }
    public String getOriginKey() { return originKey; }
    public void setOriginKey(String v) { this.originKey = v; }
    public UUID getIncidentId() { return incidentId; }
    public void setIncidentId(UUID v) { this.incidentId = v; }
    public String getSubjectIdentityMode() { return subjectIdentityMode; }
    public void setSubjectIdentityMode(String v) { this.subjectIdentityMode = v; }
    public String getSubjectCpid() { return subjectCpid; }
    public void setSubjectCpid(String v) { this.subjectCpid = v; }
    public String getSubjectTempRef() { return subjectTempRef; }
    public void setSubjectTempRef(String v) { this.subjectTempRef = v; }
    public String getStatus() { return status; }
    public void setStatus(String v) { this.status = v; }
    public String getCurrentPhase() { return currentPhase; }
    public void setCurrentPhase(String v) { this.currentPhase = v; }
    public OffsetDateTime getOpenedAt() { return openedAt; }
    public void setOpenedAt(OffsetDateTime v) { this.openedAt = v; }
    public OffsetDateTime getClosedAt() { return closedAt; }
    public void setClosedAt(OffsetDateTime v) { this.closedAt = v; }
    public String getCloseReason() { return closeReason; }
    public void setCloseReason(String v) { this.closeReason = v; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }

    public String getEpisodeClass() { return episodeClass; }
    public void setEpisodeClass(String v) { this.episodeClass = v; }
    public String getPctJourneyId() { return pctJourneyId; }
    public void setPctJourneyId(String v) { this.pctJourneyId = v; }
    public UUID getPctEmergencyEpisodeId() { return pctEmergencyEpisodeId; }
    public void setPctEmergencyEpisodeId(UUID v) { this.pctEmergencyEpisodeId = v; }
    public OffsetDateTime getContinuumLinkedAt() { return continuumLinkedAt; }
    public void setContinuumLinkedAt(OffsetDateTime v) { this.continuumLinkedAt = v; }
    public UUID getMergedIntoId() { return mergedIntoId; }
    public void setMergedIntoId(UUID v) { this.mergedIntoId = v; }
    public OffsetDateTime getMergedAt() { return mergedAt; }
    public void setMergedAt(OffsetDateTime v) { this.mergedAt = v; }
    public String getMergeReason() { return mergeReason; }
    public void setMergeReason(String v) { this.mergeReason = v; }
}
