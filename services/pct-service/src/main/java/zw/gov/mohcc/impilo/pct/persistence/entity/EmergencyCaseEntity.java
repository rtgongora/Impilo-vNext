package zw.gov.mohcc.impilo.pct.persistence.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "emergency_cases")
public class EmergencyCaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "emergency_case_id", nullable = false, unique = true)
    private UUID emergencyCaseId;

    @Column(name = "temp_person_ref")
    private String tempPersonRef;

    @Column(name = "known_subject_cpid")
    private String knownSubjectCpid;

    @Column(name = "triage_category", nullable = false)
    private String triageCategory = "RED";

    @Column(name = "presenting_complaint", nullable = false, columnDefinition = "TEXT")
    private String presentingComplaint;

    @Column(name = "opened_by_actor_id", nullable = false)
    private String openedByActorId;

    @Column(name = "opened_by_provider_id")
    private String openedByProviderId;

    @Column(name = "facility_id")
    private UUID facilityId;

    @Column(name = "location_description")
    private String locationDescription;

    @Column(name = "status", nullable = false)
    private String status = "ACTIVE";

    @Column(name = "reconciled_cpid")
    private String reconciledCpid;

    @Column(name = "reconciled_at")
    private OffsetDateTime reconciledAt;

    @Column(name = "reconciled_by")
    private String reconciledBy;

    @Column(name = "journey_id")
    private String journeyId;

    @Column(name = "visit_id")
    private UUID visitId;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (emergencyCaseId == null) emergencyCaseId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    public Long getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public UUID getEmergencyCaseId() { return emergencyCaseId; }
    public String getTempPersonRef() { return tempPersonRef; }
    public void setTempPersonRef(String tempPersonRef) { this.tempPersonRef = tempPersonRef; }
    public String getKnownSubjectCpid() { return knownSubjectCpid; }
    public void setKnownSubjectCpid(String knownSubjectCpid) { this.knownSubjectCpid = knownSubjectCpid; }
    public String getTriageCategory() { return triageCategory; }
    public void setTriageCategory(String triageCategory) { this.triageCategory = triageCategory; }
    public String getPresentingComplaint() { return presentingComplaint; }
    public void setPresentingComplaint(String presentingComplaint) { this.presentingComplaint = presentingComplaint; }
    public String getOpenedByActorId() { return openedByActorId; }
    public void setOpenedByActorId(String openedByActorId) { this.openedByActorId = openedByActorId; }
    public String getOpenedByProviderId() { return openedByProviderId; }
    public void setOpenedByProviderId(String openedByProviderId) { this.openedByProviderId = openedByProviderId; }
    public UUID getFacilityId() { return facilityId; }
    public void setFacilityId(UUID facilityId) { this.facilityId = facilityId; }
    public String getLocationDescription() { return locationDescription; }
    public void setLocationDescription(String locationDescription) { this.locationDescription = locationDescription; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getReconciledCpid() { return reconciledCpid; }
    public void setReconciledCpid(String reconciledCpid) { this.reconciledCpid = reconciledCpid; }
    public OffsetDateTime getReconciledAt() { return reconciledAt; }
    public void setReconciledAt(OffsetDateTime reconciledAt) { this.reconciledAt = reconciledAt; }
    public String getReconciledBy() { return reconciledBy; }
    public void setReconciledBy(String reconciledBy) { this.reconciledBy = reconciledBy; }
    public String getJourneyId() { return journeyId; }
    public void setJourneyId(String journeyId) { this.journeyId = journeyId; }
    public UUID getVisitId() { return visitId; }
    public void setVisitId(UUID visitId) { this.visitId = visitId; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
}
