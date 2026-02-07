package zw.gov.mohcc.impilo.pct.persistence.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Represents a death case record linked to a patient journey.
 * Tracks pronouncement details, UBOMI (civil registry) notification status,
 * and the associated death certificate document reference.
 */
@Entity
@Table(name = "pct_death_cases")
public class DeathCaseEntity {

    @Id
    @Column(name = "case_id", nullable = false)
    private UUID caseId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "facility_id")
    private UUID facilityId;

    @Column(name = "journey_id", nullable = false)
    private String journeyId;

    @Column(name = "patient_cpid")
    private String patientCpid;

    @Column(name = "status", nullable = false)
    private String status = "RECORDED";

    @Column(name = "pronounced_by")
    private String pronouncedBy;

    @Column(name = "pronounced_at")
    private OffsetDateTime pronouncedAt;

    @Column(name = "ubomi_notification_id")
    private String ubomiNotificationId;

    @Column(name = "ubomi_status")
    private String ubomiStatus;

    @Column(name = "cert_doc_id")
    private String certDocId;

    @Column(name = "closed_at")
    private OffsetDateTime closedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
    }

    // Getters and setters

    public UUID getId() { return caseId; }
    public void setId(UUID id) { this.caseId = id; }

    public UUID getCaseId() { return caseId; }
    public void setCaseId(UUID caseId) { this.caseId = caseId; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public UUID getFacilityId() { return facilityId; }
    public void setFacilityId(UUID facilityId) { this.facilityId = facilityId; }

    public String getJourneyId() { return journeyId; }
    public void setJourneyId(String journeyId) { this.journeyId = journeyId; }

    public String getPatientCpid() { return patientCpid; }
    public void setPatientCpid(String patientCpid) { this.patientCpid = patientCpid; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getPronouncedBy() { return pronouncedBy; }
    public void setPronouncedBy(String pronouncedBy) { this.pronouncedBy = pronouncedBy; }

    public OffsetDateTime getPronouncedAt() { return pronouncedAt; }
    public void setPronouncedAt(OffsetDateTime pronouncedAt) { this.pronouncedAt = pronouncedAt; }

    public String getUbomiNotificationId() { return ubomiNotificationId; }
    public void setUbomiNotificationId(String ubomiNotificationId) { this.ubomiNotificationId = ubomiNotificationId; }

    public String getUbomiStatus() { return ubomiStatus; }
    public void setUbomiStatus(String ubomiStatus) { this.ubomiStatus = ubomiStatus; }

    public String getCertDocId() { return certDocId; }
    public void setCertDocId(String certDocId) { this.certDocId = certDocId; }

    public OffsetDateTime getClosedAt() { return closedAt; }
    public void setClosedAt(OffsetDateTime closedAt) { this.closedAt = closedAt; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

}
