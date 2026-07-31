package zw.gov.mohcc.impilo.reporting.persistence.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Per-episode emergency metric projection (W17). Upserted by
 * {@code EmergencyReportingConsumer} from pct.emergency.* Kafka topics. Report
 * definitions aggregate from this table only — never from pct.*.
 */
@Entity
@Table(name = "rpt_emergency_episode_metric")
public class RptEmergencyEpisodeMetricEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "episode_id", nullable = false)
    private UUID episodeId;

    @Column(name = "facility_id")
    private UUID facilityId;

    @Column(name = "episode_reference", length = 32)
    private String episodeReference;

    @Column(name = "state", length = 48)
    private String state;

    @Column(name = "previous_state", length = 48)
    private String previousState;

    @Column(name = "entry_route", length = 48)
    private String entryRoute;

    @Column(name = "episode_class", length = 48)
    private String episodeClass;

    @Column(name = "identity_mode", length = 32)
    private String identityMode;

    @Column(name = "subject_cpid", length = 128)
    private String subjectCpid;

    @Column(name = "journey_id", length = 128)
    private String journeyId;

    @Column(name = "encounter_id", length = 128)
    private String encounterId;

    @Column(name = "outcome", length = 64)
    private String outcome;

    @Column(name = "time_to_triage_minutes")
    private Integer timeToTriageMinutes;

    @Column(name = "length_of_stay_minutes")
    private Integer lengthOfStayMinutes;

    @Column(name = "triage_acuity", length = 32)
    private String triageAcuity;

    @Column(name = "triage_system", length = 32)
    private String triageSystem;

    @Column(name = "disposition_type", length = 64)
    private String dispositionType;

    @Column(name = "closed", nullable = false)
    private boolean closed = false;

    @Column(name = "died", nullable = false)
    private boolean died = false;

    @Column(name = "left_without_being_seen", nullable = false)
    private boolean leftWithoutBeingSeen = false;

    @Column(name = "handover_count", nullable = false)
    private int handoverCount = 0;

    @Column(name = "handover_expired_count", nullable = false)
    private int handoverExpiredCount = 0;

    @Column(name = "alert_count", nullable = false)
    private int alertCount = 0;

    @Column(name = "alert_overdue_count", nullable = false)
    private int alertOverdueCount = 0;

    @Column(name = "rito_case_linked", nullable = false)
    private boolean ritoCaseLinked = false;

    @Column(name = "opened_at")
    private OffsetDateTime openedAt;

    @Column(name = "closed_at")
    private OffsetDateTime closedAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    @PreUpdate
    void touch() {
        updatedAt = OffsetDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long v) { this.id = v; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID v) { this.tenantId = v; }
    public UUID getEpisodeId() { return episodeId; }
    public void setEpisodeId(UUID v) { this.episodeId = v; }
    public UUID getFacilityId() { return facilityId; }
    public void setFacilityId(UUID v) { this.facilityId = v; }
    public String getEpisodeReference() { return episodeReference; }
    public void setEpisodeReference(String v) { this.episodeReference = v; }
    public String getState() { return state; }
    public void setState(String v) { this.state = v; }
    public String getPreviousState() { return previousState; }
    public void setPreviousState(String v) { this.previousState = v; }
    public String getEntryRoute() { return entryRoute; }
    public void setEntryRoute(String v) { this.entryRoute = v; }
    public String getEpisodeClass() { return episodeClass; }
    public void setEpisodeClass(String v) { this.episodeClass = v; }
    public String getIdentityMode() { return identityMode; }
    public void setIdentityMode(String v) { this.identityMode = v; }
    public String getSubjectCpid() { return subjectCpid; }
    public void setSubjectCpid(String v) { this.subjectCpid = v; }
    public String getJourneyId() { return journeyId; }
    public void setJourneyId(String v) { this.journeyId = v; }
    public String getEncounterId() { return encounterId; }
    public void setEncounterId(String v) { this.encounterId = v; }
    public String getOutcome() { return outcome; }
    public void setOutcome(String v) { this.outcome = v; }
    public Integer getTimeToTriageMinutes() { return timeToTriageMinutes; }
    public void setTimeToTriageMinutes(Integer v) { this.timeToTriageMinutes = v; }
    public Integer getLengthOfStayMinutes() { return lengthOfStayMinutes; }
    public void setLengthOfStayMinutes(Integer v) { this.lengthOfStayMinutes = v; }
    public String getTriageAcuity() { return triageAcuity; }
    public void setTriageAcuity(String v) { this.triageAcuity = v; }
    public String getTriageSystem() { return triageSystem; }
    public void setTriageSystem(String v) { this.triageSystem = v; }
    public String getDispositionType() { return dispositionType; }
    public void setDispositionType(String v) { this.dispositionType = v; }
    public boolean isClosed() { return closed; }
    public void setClosed(boolean v) { this.closed = v; }
    public boolean isDied() { return died; }
    public void setDied(boolean v) { this.died = v; }
    public boolean isLeftWithoutBeingSeen() { return leftWithoutBeingSeen; }
    public void setLeftWithoutBeingSeen(boolean v) { this.leftWithoutBeingSeen = v; }
    public int getHandoverCount() { return handoverCount; }
    public void setHandoverCount(int v) { this.handoverCount = v; }
    public int getHandoverExpiredCount() { return handoverExpiredCount; }
    public void setHandoverExpiredCount(int v) { this.handoverExpiredCount = v; }
    public int getAlertCount() { return alertCount; }
    public void setAlertCount(int v) { this.alertCount = v; }
    public int getAlertOverdueCount() { return alertOverdueCount; }
    public void setAlertOverdueCount(int v) { this.alertOverdueCount = v; }
    public boolean isRitoCaseLinked() { return ritoCaseLinked; }
    public void setRitoCaseLinked(boolean v) { this.ritoCaseLinked = v; }
    public OffsetDateTime getOpenedAt() { return openedAt; }
    public void setOpenedAt(OffsetDateTime v) { this.openedAt = v; }
    public OffsetDateTime getClosedAt() { return closedAt; }
    public void setClosedAt(OffsetDateTime v) { this.closedAt = v; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime v) { this.updatedAt = v; }
}
