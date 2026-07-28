package zw.gov.mohcc.impilo.pct.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

import zw.gov.mohcc.impilo.pct.domain.EmergencyAlertType;

/** A time-based safety alert over an open emergency episode (pct V203). */
@Entity
@Table(name = "emergency_alert")
public class EmergencyAlertEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "alert_id")
    private UUID alertId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "facility_id")
    private UUID facilityId;

    @Column(name = "episode_id", nullable = false)
    private UUID episodeId;

    @Column(name = "subject_cpid")
    private String subjectCpid;

    @Column(name = "alert_type", nullable = false)
    private String alertType;

    @Column(name = "severity", nullable = false)
    private String severity = "HIGH";

    @Column(name = "status", nullable = false)
    private String status = "RAISED";

    @Column(name = "reason", nullable = false, columnDefinition = "TEXT")
    private String reason;

    /** The clock reading that triggered the raise, so a responder sees the evidence. */
    @Column(name = "detected_value_at")
    private OffsetDateTime detectedValueAt;

    @Column(name = "raised_at", nullable = false)
    private OffsetDateTime raisedAt;

    @Column(name = "raised_by", nullable = false)
    private String raisedBy = EmergencyAlertType.SYSTEM_ACTOR;

    @Column(name = "response_due_at", nullable = false)
    private OffsetDateTime responseDueAt;

    @Column(name = "acknowledged_by")
    private String acknowledgedBy;

    @Column(name = "acknowledged_at")
    private OffsetDateTime acknowledgedAt;

    @Column(name = "responded_by")
    private String respondedBy;

    @Column(name = "responded_at")
    private OffsetDateTime respondedAt;

    @Column(name = "response_note", columnDefinition = "TEXT")
    private String responseNote;

    @Column(name = "closed_at")
    private OffsetDateTime closedAt;

    @Column(name = "close_reason")
    private String closeReason;

    /** Link only — rito owns the safety case. */
    @Column(name = "rito_case_ref")
    private String ritoCaseRef;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        if (createdAt == null) createdAt = now;
        if (raisedAt == null) raisedAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    public UUID getAlertId() { return alertId; }
    public void setAlertId(UUID alertId) { this.alertId = alertId; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public UUID getFacilityId() { return facilityId; }
    public void setFacilityId(UUID facilityId) { this.facilityId = facilityId; }
    public UUID getEpisodeId() { return episodeId; }
    public void setEpisodeId(UUID episodeId) { this.episodeId = episodeId; }
    public String getSubjectCpid() { return subjectCpid; }
    public void setSubjectCpid(String subjectCpid) { this.subjectCpid = subjectCpid; }
    public String getAlertType() { return alertType; }
    public void setAlertType(String alertType) { this.alertType = alertType; }
    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public OffsetDateTime getDetectedValueAt() { return detectedValueAt; }
    public void setDetectedValueAt(OffsetDateTime detectedValueAt) { this.detectedValueAt = detectedValueAt; }
    public OffsetDateTime getRaisedAt() { return raisedAt; }
    public void setRaisedAt(OffsetDateTime raisedAt) { this.raisedAt = raisedAt; }
    public String getRaisedBy() { return raisedBy; }
    public void setRaisedBy(String raisedBy) { this.raisedBy = raisedBy; }
    public OffsetDateTime getResponseDueAt() { return responseDueAt; }
    public void setResponseDueAt(OffsetDateTime responseDueAt) { this.responseDueAt = responseDueAt; }
    public String getAcknowledgedBy() { return acknowledgedBy; }
    public void setAcknowledgedBy(String acknowledgedBy) { this.acknowledgedBy = acknowledgedBy; }
    public OffsetDateTime getAcknowledgedAt() { return acknowledgedAt; }
    public void setAcknowledgedAt(OffsetDateTime acknowledgedAt) { this.acknowledgedAt = acknowledgedAt; }
    public String getRespondedBy() { return respondedBy; }
    public void setRespondedBy(String respondedBy) { this.respondedBy = respondedBy; }
    public OffsetDateTime getRespondedAt() { return respondedAt; }
    public void setRespondedAt(OffsetDateTime respondedAt) { this.respondedAt = respondedAt; }
    public String getResponseNote() { return responseNote; }
    public void setResponseNote(String responseNote) { this.responseNote = responseNote; }
    public OffsetDateTime getClosedAt() { return closedAt; }
    public void setClosedAt(OffsetDateTime closedAt) { this.closedAt = closedAt; }
    public String getCloseReason() { return closeReason; }
    public void setCloseReason(String closeReason) { this.closeReason = closeReason; }
    public String getRitoCaseRef() { return ritoCaseRef; }
    public void setRitoCaseRef(String ritoCaseRef) { this.ritoCaseRef = ritoCaseRef; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
