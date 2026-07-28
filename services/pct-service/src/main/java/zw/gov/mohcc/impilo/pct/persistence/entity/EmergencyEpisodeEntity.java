package zw.gov.mohcc.impilo.pct.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * The canonical emergency episode. See V200__emergency_episode.sql for the full rationale.
 *
 * <p>Two things this entity deliberately does not carry: acuity, which lives in
 * {@code ed_triage_assessment}, and disposition detail, which lives in {@code ed_disposition}. The
 * episode composes them. Two copies of an acuity is two answers to how sick a patient is.
 *
 * <p>{@code subjectCpid} is an attribute, never the anchor — emergency clinical rows hang off
 * {@code episodeId}, so a VITO merge repoints this field and never re-parents a record.
 */
@Entity
@Table(name = "emergency_episode", schema = "pct")
public class EmergencyEpisodeEntity {

    @Id
    @Column(name = "episode_id")
    private UUID episodeId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "facility_id", nullable = false)
    private UUID facilityId;

    @Column(name = "episode_reference", nullable = false)
    private String episodeReference;

    // ── CC-5 anchor ──────────────────────────────────────────────────────────────────────────
    @Column(name = "journey_id")
    private String journeyId;

    @Column(name = "encounter_id")
    private Long encounterId;

    @Column(name = "trauma_episode_id")
    private UUID traumaEpisodeId;

    @Column(name = "anchor_resolved_at")
    private OffsetDateTime anchorResolvedAt;

    // ── identity ─────────────────────────────────────────────────────────────────────────────
    @Column(name = "subject_cpid")
    private String subjectCpid;

    @Column(name = "identity_mode", nullable = false)
    private String identityMode;

    @Column(name = "emergency_case_id")
    private UUID emergencyCaseId;

    @Column(name = "identity_resolved_at")
    private OffsetDateTime identityResolvedAt;

    // ── entry ────────────────────────────────────────────────────────────────────────────────
    @Column(name = "entry_route", nullable = false)
    private String entryRoute;

    @Column(name = "entry_source_service")
    private String entrySourceService;

    @Column(name = "entry_source_ref")
    private String entrySourceRef;

    @Column(name = "entry_context_json", columnDefinition = "jsonb")
    private String entryContextJson;

    @Column(name = "arrived_at", nullable = false)
    private OffsetDateTime arrivedAt;

    @Column(name = "episode_class", nullable = false)
    private String episodeClass;

    @Column(name = "sensitive", nullable = false)
    private boolean sensitive;

    @Column(name = "state", nullable = false)
    private String state;

    @Column(name = "current_location")
    private String currentLocation;

    /** Set on entering OPEN_AWAITING_ACCEPTANCE, cleared on leaving. Not updated_at — see V203. */
    @Column(name = "awaiting_acceptance_since")
    private OffsetDateTime awaitingAcceptanceSince;

    @Column(name = "location_confirmed_at")
    private OffsetDateTime locationConfirmedAt;

    // ── derived projections; systems of record named in the migration comments ───────────────
    @Column(name = "triaged_at")
    private OffsetDateTime triagedAt;

    @Column(name = "target_first_contact_at")
    private OffsetDateTime targetFirstContactAt;

    @Column(name = "first_contact_at")
    private OffsetDateTime firstContactAt;

    @Column(name = "reassessment_due_at")
    private OffsetDateTime reassessmentDueAt;

    @Column(name = "last_observation_at")
    private OffsetDateTime lastObservationAt;

    // ── closure ──────────────────────────────────────────────────────────────────────────────
    @Column(name = "handover_id")
    private UUID handoverId;

    @Column(name = "outcome")
    private String outcome;

    @Column(name = "closed_at")
    private OffsetDateTime closedAt;

    @Column(name = "merged_into_id")
    private UUID mergedIntoId;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "created_by")
    private String createdBy;

    public UUID getEpisodeId() { return episodeId; }
    public void setEpisodeId(UUID v) { this.episodeId = v; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID v) { this.tenantId = v; }
    public UUID getFacilityId() { return facilityId; }
    public void setFacilityId(UUID v) { this.facilityId = v; }
    public String getEpisodeReference() { return episodeReference; }
    public void setEpisodeReference(String v) { this.episodeReference = v; }
    public String getJourneyId() { return journeyId; }
    public void setJourneyId(String v) { this.journeyId = v; }
    public Long getEncounterId() { return encounterId; }
    public void setEncounterId(Long v) { this.encounterId = v; }
    public UUID getTraumaEpisodeId() { return traumaEpisodeId; }
    public void setTraumaEpisodeId(UUID v) { this.traumaEpisodeId = v; }
    public OffsetDateTime getAnchorResolvedAt() { return anchorResolvedAt; }
    public void setAnchorResolvedAt(OffsetDateTime v) { this.anchorResolvedAt = v; }
    public String getSubjectCpid() { return subjectCpid; }
    public void setSubjectCpid(String v) { this.subjectCpid = v; }
    public String getIdentityMode() { return identityMode; }
    public void setIdentityMode(String v) { this.identityMode = v; }
    public UUID getEmergencyCaseId() { return emergencyCaseId; }
    public void setEmergencyCaseId(UUID v) { this.emergencyCaseId = v; }
    public OffsetDateTime getIdentityResolvedAt() { return identityResolvedAt; }
    public void setIdentityResolvedAt(OffsetDateTime v) { this.identityResolvedAt = v; }
    public String getEntryRoute() { return entryRoute; }
    public void setEntryRoute(String v) { this.entryRoute = v; }
    public String getEntrySourceService() { return entrySourceService; }
    public void setEntrySourceService(String v) { this.entrySourceService = v; }
    public String getEntrySourceRef() { return entrySourceRef; }
    public void setEntrySourceRef(String v) { this.entrySourceRef = v; }
    public String getEntryContextJson() { return entryContextJson; }
    public void setEntryContextJson(String v) { this.entryContextJson = v; }
    public OffsetDateTime getArrivedAt() { return arrivedAt; }
    public void setArrivedAt(OffsetDateTime v) { this.arrivedAt = v; }
    public String getEpisodeClass() { return episodeClass; }
    public void setEpisodeClass(String v) { this.episodeClass = v; }
    public boolean isSensitive() { return sensitive; }
    public void setSensitive(boolean v) { this.sensitive = v; }
    public String getState() { return state; }
    public void setState(String v) { this.state = v; }
    public String getCurrentLocation() { return currentLocation; }
    public void setCurrentLocation(String v) { this.currentLocation = v; }
    public OffsetDateTime getLocationConfirmedAt() { return locationConfirmedAt; }
    public void setLocationConfirmedAt(OffsetDateTime v) { this.locationConfirmedAt = v; }
    public OffsetDateTime getTriagedAt() { return triagedAt; }
    public void setTriagedAt(OffsetDateTime v) { this.triagedAt = v; }
    public OffsetDateTime getTargetFirstContactAt() { return targetFirstContactAt; }
    public void setTargetFirstContactAt(OffsetDateTime v) { this.targetFirstContactAt = v; }
    public OffsetDateTime getFirstContactAt() { return firstContactAt; }
    public void setFirstContactAt(OffsetDateTime v) { this.firstContactAt = v; }
    public OffsetDateTime getReassessmentDueAt() { return reassessmentDueAt; }
    public void setReassessmentDueAt(OffsetDateTime v) { this.reassessmentDueAt = v; }
    public OffsetDateTime getLastObservationAt() { return lastObservationAt; }
    public void setLastObservationAt(OffsetDateTime v) { this.lastObservationAt = v; }
    public UUID getHandoverId() { return handoverId; }
    public void setHandoverId(UUID v) { this.handoverId = v; }
    public String getOutcome() { return outcome; }
    public void setOutcome(String v) { this.outcome = v; }
    public OffsetDateTime getClosedAt() { return closedAt; }
    public void setClosedAt(OffsetDateTime v) { this.closedAt = v; }
    public UUID getMergedIntoId() { return mergedIntoId; }
    public void setMergedIntoId(UUID v) { this.mergedIntoId = v; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime v) { this.createdAt = v; }
    public OffsetDateTime getAwaitingAcceptanceSince() { return awaitingAcceptanceSince; }
    public void setAwaitingAcceptanceSince(OffsetDateTime v) { this.awaitingAcceptanceSince = v; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime v) { this.updatedAt = v; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String v) { this.createdBy = v; }
}
