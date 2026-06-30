package zw.gov.mohcc.impilo.daidzai.persistence.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "dai_emergency_incident", schema = "daidzai")
public class EmergencyIncidentEntity {

    @Id @Column(name = "id") private UUID id;
    @Column(name = "tenant_id", nullable = false) private UUID tenantId;
    @Column(name = "incident_reference", nullable = false, length = 48) private String incidentReference;
    @Column(name = "incident_type", nullable = false, length = 24) private String incidentType = "INDIVIDUAL";
    @Column(name = "emergency_category", nullable = false, length = 48) private String emergencyCategory;
    @Column(name = "severity", nullable = false, length = 16) private String severity = "UNKNOWN";
    @Column(name = "triage_category", length = 16) private String triageCategory;
    @Column(name = "sensitive", nullable = false) private Boolean sensitive = Boolean.FALSE;
    @Column(name = "status", nullable = false, length = 24) private String status = "NEW";
    @Column(name = "title", length = 512) private String title;
    @Column(name = "description", columnDefinition = "TEXT") private String description;
    @Column(name = "subject_identity_mode", length = 24) private String subjectIdentityMode;
    @Column(name = "subject_health_id", length = 128) private String subjectHealthId;
    @Column(name = "subject_temp_ref", length = 128) private String subjectTempRef;
    @Column(name = "location_lat") private Double locationLat;
    @Column(name = "location_lng") private Double locationLng;
    @Column(name = "location_description", length = 512) private String locationDescription;
    @Column(name = "affected_area_ref", length = 255) private String affectedAreaRef;
    @Column(name = "facility_id") private UUID facilityId;
    @Column(name = "ndila_route_ref", length = 128) private String ndilaRouteRef;
    @Column(name = "nhume_mission_ref", length = 128) private String nhumeMissionRef;
    @Column(name = "pct_encounter_ref", length = 128) private String pctEncounterRef;
    @Column(name = "rito_case_ref", length = 128) private String ritoCaseRef;
    @Column(name = "command_post", length = 255) private String commandPost;
    @Column(name = "incident_commander", length = 128) private String incidentCommander;
    @Column(name = "opened_at", nullable = false) private OffsetDateTime openedAt;
    @Column(name = "closed_at") private OffsetDateTime closedAt;
    @Column(name = "created_at", nullable = false) private OffsetDateTime createdAt;
    @Column(name = "updated_at", nullable = false) private OffsetDateTime updatedAt;

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
    public String getIncidentReference() { return incidentReference; }
    public void setIncidentReference(String v) { this.incidentReference = v; }
    public String getIncidentType() { return incidentType; }
    public void setIncidentType(String v) { this.incidentType = v; }
    public String getEmergencyCategory() { return emergencyCategory; }
    public void setEmergencyCategory(String v) { this.emergencyCategory = v; }
    public String getSeverity() { return severity; }
    public void setSeverity(String v) { this.severity = v; }
    public String getTriageCategory() { return triageCategory; }
    public void setTriageCategory(String v) { this.triageCategory = v; }
    public Boolean getSensitive() { return sensitive; }
    public void setSensitive(Boolean v) { this.sensitive = v; }
    public String getStatus() { return status; }
    public void setStatus(String v) { this.status = v; }
    public String getTitle() { return title; }
    public void setTitle(String v) { this.title = v; }
    public String getDescription() { return description; }
    public void setDescription(String v) { this.description = v; }
    public String getSubjectIdentityMode() { return subjectIdentityMode; }
    public void setSubjectIdentityMode(String v) { this.subjectIdentityMode = v; }
    public String getSubjectHealthId() { return subjectHealthId; }
    public void setSubjectHealthId(String v) { this.subjectHealthId = v; }
    public String getSubjectTempRef() { return subjectTempRef; }
    public void setSubjectTempRef(String v) { this.subjectTempRef = v; }
    public Double getLocationLat() { return locationLat; }
    public void setLocationLat(Double v) { this.locationLat = v; }
    public Double getLocationLng() { return locationLng; }
    public void setLocationLng(Double v) { this.locationLng = v; }
    public String getLocationDescription() { return locationDescription; }
    public void setLocationDescription(String v) { this.locationDescription = v; }
    public String getAffectedAreaRef() { return affectedAreaRef; }
    public void setAffectedAreaRef(String v) { this.affectedAreaRef = v; }
    public UUID getFacilityId() { return facilityId; }
    public void setFacilityId(UUID v) { this.facilityId = v; }
    public String getNdilaRouteRef() { return ndilaRouteRef; }
    public void setNdilaRouteRef(String v) { this.ndilaRouteRef = v; }
    public String getNhumeMissionRef() { return nhumeMissionRef; }
    public void setNhumeMissionRef(String v) { this.nhumeMissionRef = v; }
    public String getPctEncounterRef() { return pctEncounterRef; }
    public void setPctEncounterRef(String v) { this.pctEncounterRef = v; }
    public String getRitoCaseRef() { return ritoCaseRef; }
    public void setRitoCaseRef(String v) { this.ritoCaseRef = v; }
    public String getCommandPost() { return commandPost; }
    public void setCommandPost(String v) { this.commandPost = v; }
    public String getIncidentCommander() { return incidentCommander; }
    public void setIncidentCommander(String v) { this.incidentCommander = v; }
    public OffsetDateTime getOpenedAt() { return openedAt; }
    public void setOpenedAt(OffsetDateTime v) { this.openedAt = v; }
    public OffsetDateTime getClosedAt() { return closedAt; }
    public void setClosedAt(OffsetDateTime v) { this.closedAt = v; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
