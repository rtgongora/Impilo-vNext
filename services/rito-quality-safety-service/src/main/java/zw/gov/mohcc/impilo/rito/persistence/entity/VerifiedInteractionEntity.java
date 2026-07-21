package zw.gov.mohcc.impilo.rito.persistence.entity;

import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * A completed PCT encounter recorded as an eligible-for-feedback interaction (RW2).
 * A rating that references this {@code encounterRef} is stamped verified (RW3). This is
 * the PCT verification gate for the Experience & Reputation capability.
 */
@Entity
@Table(name = "rit_verified_interaction", schema = "rito")
public class VerifiedInteractionEntity {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "encounter_ref", nullable = false, length = 128)
    private String encounterRef;

    @Column(name = "attending_provider_id", length = 64)
    private String attendingProviderId;

    @Column(name = "patient_cpid", length = 64)
    private String patientCpid;

    @Column(name = "facility_id")
    private UUID facilityId;

    @Column(name = "encounter_type", length = 64)
    private String encounterType;

    @Column(name = "modality", length = 24)
    private String modality;

    @Column(name = "ended_at")
    private OffsetDateTime endedAt;

    @Column(name = "source_system", nullable = false, length = 24)
    private String sourceSystem = "PCT";

    @Column(name = "feedback_requested", nullable = false)
    private Boolean feedbackRequested = false;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public String getEncounterRef() { return encounterRef; }
    public void setEncounterRef(String encounterRef) { this.encounterRef = encounterRef; }
    public String getAttendingProviderId() { return attendingProviderId; }
    public void setAttendingProviderId(String attendingProviderId) { this.attendingProviderId = attendingProviderId; }
    public String getPatientCpid() { return patientCpid; }
    public void setPatientCpid(String patientCpid) { this.patientCpid = patientCpid; }
    public UUID getFacilityId() { return facilityId; }
    public void setFacilityId(UUID facilityId) { this.facilityId = facilityId; }
    public String getEncounterType() { return encounterType; }
    public void setEncounterType(String encounterType) { this.encounterType = encounterType; }
    public String getModality() { return modality; }
    public void setModality(String modality) { this.modality = modality; }
    public OffsetDateTime getEndedAt() { return endedAt; }
    public void setEndedAt(OffsetDateTime endedAt) { this.endedAt = endedAt; }
    public String getSourceSystem() { return sourceSystem; }
    public void setSourceSystem(String sourceSystem) { this.sourceSystem = sourceSystem; }
    public Boolean getFeedbackRequested() { return feedbackRequested; }
    public void setFeedbackRequested(Boolean feedbackRequested) { this.feedbackRequested = feedbackRequested; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
