package zw.gov.mohcc.impilo.pct.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * One labour, one graph.
 *
 * <p>The alert reference anchors are stored rather than recomputed on read. The WHO partograph
 * plots an alert line rising 1 cm/hour from the point active labour is recognised, with the
 * action line four hours to its right; pinning that origin means the line does not move when a
 * historical observation is corrected or when the guideline's active-phase threshold is next
 * revised.</p>
 */
@Entity
@Table(name = "pct_partograph_sessions", schema = "pct")
public class PartographSessionEntity {

    @Id
    @Column(name = "session_id")
    private UUID sessionId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "subject_cpid", nullable = false)
    private String subjectCpid;

    @Column(name = "journey_id")
    private String journeyId;

    @Column(name = "encounter_id")
    private String encounterId;

    @Column(name = "facility_id")
    private UUID facilityId;

    @Column(name = "status", nullable = false)
    private String status = "ACTIVE";

    @Column(name = "labour_phase", nullable = false)
    private String labourPhase = "ACTIVE_LABOUR";

    @Column(name = "started_at", nullable = false)
    private OffsetDateTime startedAt;

    @Column(name = "started_by", nullable = false)
    private String startedBy;

    @Column(name = "closed_at")
    private OffsetDateTime closedAt;

    @Column(name = "closed_by")
    private String closedBy;

    @Column(name = "outcome")
    private String outcome;

    @Column(name = "summary_notes")
    private String summaryNotes;

    @Column(name = "alert_reference_at")
    private OffsetDateTime alertReferenceAt;

    @Column(name = "alert_reference_dilation_cm")
    private BigDecimal alertReferenceDilationCm;

    @Column(name = "partograph_content_version")
    private String partographContentVersion;

    @Column(name = "created_at")
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    public UUID getSessionId() {
        return sessionId;
    }

    public void setSessionId(UUID sessionId) {
        this.sessionId = sessionId;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }

    public String getSubjectCpid() {
        return subjectCpid;
    }

    public void setSubjectCpid(String subjectCpid) {
        this.subjectCpid = subjectCpid;
    }

    public String getJourneyId() {
        return journeyId;
    }

    public void setJourneyId(String journeyId) {
        this.journeyId = journeyId;
    }

    public String getEncounterId() {
        return encounterId;
    }

    public void setEncounterId(String encounterId) {
        this.encounterId = encounterId;
    }

    public UUID getFacilityId() {
        return facilityId;
    }

    public void setFacilityId(UUID facilityId) {
        this.facilityId = facilityId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getLabourPhase() {
        return labourPhase;
    }

    public void setLabourPhase(String labourPhase) {
        this.labourPhase = labourPhase;
    }

    public OffsetDateTime getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(OffsetDateTime startedAt) {
        this.startedAt = startedAt;
    }

    public String getStartedBy() {
        return startedBy;
    }

    public void setStartedBy(String startedBy) {
        this.startedBy = startedBy;
    }

    public OffsetDateTime getClosedAt() {
        return closedAt;
    }

    public void setClosedAt(OffsetDateTime closedAt) {
        this.closedAt = closedAt;
    }

    public String getClosedBy() {
        return closedBy;
    }

    public void setClosedBy(String closedBy) {
        this.closedBy = closedBy;
    }

    public String getOutcome() {
        return outcome;
    }

    public void setOutcome(String outcome) {
        this.outcome = outcome;
    }

    public String getSummaryNotes() {
        return summaryNotes;
    }

    public void setSummaryNotes(String summaryNotes) {
        this.summaryNotes = summaryNotes;
    }

    public OffsetDateTime getAlertReferenceAt() {
        return alertReferenceAt;
    }

    public void setAlertReferenceAt(OffsetDateTime alertReferenceAt) {
        this.alertReferenceAt = alertReferenceAt;
    }

    public BigDecimal getAlertReferenceDilationCm() {
        return alertReferenceDilationCm;
    }

    public void setAlertReferenceDilationCm(BigDecimal alertReferenceDilationCm) {
        this.alertReferenceDilationCm = alertReferenceDilationCm;
    }

    public String getPartographContentVersion() {
        return partographContentVersion;
    }

    public void setPartographContentVersion(String partographContentVersion) {
        this.partographContentVersion = partographContentVersion;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
