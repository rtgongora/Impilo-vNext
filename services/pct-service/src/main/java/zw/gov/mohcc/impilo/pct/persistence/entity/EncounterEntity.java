package zw.gov.mohcc.impilo.pct.persistence.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Represents a clinical encounter within a patient journey.
 * Each encounter is linked to a journey and optionally to a BUTANO FHIR encounter reference.
 */
@Entity
@Table(name = "pct_encounters")
public class EncounterEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "encounter_ref")
    private UUID encounterRef;

    @Column(name = "journey_id", nullable = false)
    private String journeyId;

    @Column(name = "subject_cpid")
    private String subjectCpid;

    @Column(name = "facility_id")
    private UUID facilityId;

    @Column(name = "butano_encounter_ref")
    private String butanoEncounterRef;

    @Column(name = "status", nullable = false)
    private String status = "STARTED";

    @Column(name = "workspace_id")
    private UUID workspaceId;

    @Column(name = "shift_id")
    private String shiftId;

    @Column(name = "assigned_provider_id")
    private String assignedProviderId;

    @Column(name = "encounter_type")
    private String encounterType;

    @Column(name = "encounter_context")
    private String encounterContext;

    @Column(name = "entry_point")
    private String entryPoint;

    @Column(name = "modality", nullable = false)
    private String modality = "in_person";

    @Column(name = "virtual_mode")
    private String virtualMode;

    @Column(name = "care_setting")
    private String careSetting;

    @Column(name = "priority")
    private String priority;

    @Column(name = "triage_category")
    private String triageCategory;

    @Column(name = "pathway_ref")
    private String pathwayRef;

    @Column(name = "protocol_ref")
    private String protocolRef;

    @Column(name = "started_at")
    private OffsetDateTime startedAt;

    @Column(name = "ended_at")
    private OffsetDateTime endedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
        if (startedAt == null) {
            startedAt = OffsetDateTime.now();
        }
    }

    // Getters and setters

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public UUID getEncounterRef() { return encounterRef; }
    public void setEncounterRef(UUID encounterRef) { this.encounterRef = encounterRef; }

    public String getJourneyId() { return journeyId; }
    public void setJourneyId(String journeyId) { this.journeyId = journeyId; }

    public String getSubjectCpid() { return subjectCpid; }
    public void setSubjectCpid(String subjectCpid) { this.subjectCpid = subjectCpid; }

    public UUID getFacilityId() { return facilityId; }
    public void setFacilityId(UUID facilityId) { this.facilityId = facilityId; }

    public String getButanoEncounterRef() { return butanoEncounterRef; }
    public void setButanoEncounterRef(String butanoEncounterRef) { this.butanoEncounterRef = butanoEncounterRef; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public UUID getWorkspaceId() { return workspaceId; }
    public void setWorkspaceId(UUID workspaceId) { this.workspaceId = workspaceId; }

    public String getShiftId() { return shiftId; }
    public void setShiftId(String shiftId) { this.shiftId = shiftId; }

    public String getAssignedProviderId() { return assignedProviderId; }
    public void setAssignedProviderId(String assignedProviderId) { this.assignedProviderId = assignedProviderId; }

    public String getEncounterType() { return encounterType; }
    public void setEncounterType(String encounterType) { this.encounterType = encounterType; }

    public String getEncounterContext() { return encounterContext; }
    public void setEncounterContext(String encounterContext) { this.encounterContext = encounterContext; }

    public String getEntryPoint() { return entryPoint; }
    public void setEntryPoint(String entryPoint) { this.entryPoint = entryPoint; }

    public String getModality() { return modality; }
    public void setModality(String modality) { this.modality = modality; }

    public String getVirtualMode() { return virtualMode; }
    public void setVirtualMode(String virtualMode) { this.virtualMode = virtualMode; }

    public String getCareSetting() { return careSetting; }
    public void setCareSetting(String careSetting) { this.careSetting = careSetting; }

    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }

    public String getTriageCategory() { return triageCategory; }
    public void setTriageCategory(String triageCategory) { this.triageCategory = triageCategory; }

    public String getPathwayRef() { return pathwayRef; }
    public void setPathwayRef(String pathwayRef) { this.pathwayRef = pathwayRef; }

    public String getProtocolRef() { return protocolRef; }
    public void setProtocolRef(String protocolRef) { this.protocolRef = protocolRef; }

    public OffsetDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(OffsetDateTime startedAt) { this.startedAt = startedAt; }

    public OffsetDateTime getEndedAt() { return endedAt; }
    public void setEndedAt(OffsetDateTime endedAt) { this.endedAt = endedAt; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
}
