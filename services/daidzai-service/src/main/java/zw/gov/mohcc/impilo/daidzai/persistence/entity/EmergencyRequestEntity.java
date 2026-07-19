package zw.gov.mohcc.impilo.daidzai.persistence.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "dai_emergency_request", schema = "daidzai")
public class EmergencyRequestEntity {

    @Id @Column(name = "id") private UUID id;
    @Column(name = "tenant_id", nullable = false) private UUID tenantId;
    @Column(name = "request_reference", nullable = false, length = 48) private String requestReference;
    @Column(name = "requester_type", nullable = false, length = 32) private String requesterType;
    @Column(name = "requester_actor_id", length = 128) private String requesterActorId;
    @Column(name = "subject_identity_mode", nullable = false, length = 24) private String subjectIdentityMode;
    @Column(name = "subject_cpid", length = 128) private String subjectCpid;
    @Column(name = "subject_temp_ref", length = 128) private String subjectTempRef;
    @Column(name = "subject_label", length = 255) private String subjectLabel;
    @Column(name = "emergency_category", nullable = false, length = 48) private String emergencyCategory;
    @Column(name = "sensitive", nullable = false) private Boolean sensitive = Boolean.FALSE;
    @Column(name = "severity", nullable = false, length = 16) private String severity = "UNKNOWN";
    @Column(name = "description", columnDefinition = "TEXT") private String description;
    @Column(name = "location_lat") private Double locationLat;
    @Column(name = "location_lng") private Double locationLng;
    @Column(name = "location_description", length = 512) private String locationDescription;
    @Column(name = "attachments_ref", length = 255) private String attachmentsRef;
    @Column(name = "channel", nullable = false, length = 32) private String channel = "WEB";
    @Column(name = "status", nullable = false, length = 24) private String status = "RECEIVED";
    // PD-3 callback verification: a public-anonymous request is captured immediately but dispatch
    // is gated on a dispatcher/responder reaching this number first (see V002, EmergencyService).
    @Column(name = "callback_number", length = 32) private String callbackNumber;
    @Column(name = "callback_verified", nullable = false) private Boolean callbackVerified = Boolean.FALSE;
    @Column(name = "callback_verified_at") private OffsetDateTime callbackVerifiedAt;
    @Column(name = "callback_verified_by", length = 128) private String callbackVerifiedBy;
    @Column(name = "incident_id") private UUID incidentId;
    @Column(name = "created_at", nullable = false) private OffsetDateTime createdAt;
    @Column(name = "updated_at", nullable = false) private OffsetDateTime updatedAt;

    @PrePersist void onCreate() {
        if (id == null) id = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }
    @PreUpdate void onUpdate() { updatedAt = OffsetDateTime.now(); }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public String getRequestReference() { return requestReference; }
    public void setRequestReference(String v) { this.requestReference = v; }
    public String getRequesterType() { return requesterType; }
    public void setRequesterType(String v) { this.requesterType = v; }
    public String getRequesterActorId() { return requesterActorId; }
    public void setRequesterActorId(String v) { this.requesterActorId = v; }
    public String getSubjectIdentityMode() { return subjectIdentityMode; }
    public void setSubjectIdentityMode(String v) { this.subjectIdentityMode = v; }
    public String getSubjectCpid() { return subjectCpid; }
    public void setSubjectCpid(String v) { this.subjectCpid = v; }
    public String getSubjectTempRef() { return subjectTempRef; }
    public void setSubjectTempRef(String v) { this.subjectTempRef = v; }
    public String getSubjectLabel() { return subjectLabel; }
    public void setSubjectLabel(String v) { this.subjectLabel = v; }
    public String getEmergencyCategory() { return emergencyCategory; }
    public void setEmergencyCategory(String v) { this.emergencyCategory = v; }
    public Boolean getSensitive() { return sensitive; }
    public void setSensitive(Boolean v) { this.sensitive = v; }
    public String getSeverity() { return severity; }
    public void setSeverity(String v) { this.severity = v; }
    public String getDescription() { return description; }
    public void setDescription(String v) { this.description = v; }
    public Double getLocationLat() { return locationLat; }
    public void setLocationLat(Double v) { this.locationLat = v; }
    public Double getLocationLng() { return locationLng; }
    public void setLocationLng(Double v) { this.locationLng = v; }
    public String getLocationDescription() { return locationDescription; }
    public void setLocationDescription(String v) { this.locationDescription = v; }
    public String getAttachmentsRef() { return attachmentsRef; }
    public void setAttachmentsRef(String v) { this.attachmentsRef = v; }
    public String getChannel() { return channel; }
    public void setChannel(String v) { this.channel = v; }
    public String getStatus() { return status; }
    public void setStatus(String v) { this.status = v; }
    public String getCallbackNumber() { return callbackNumber; }
    public void setCallbackNumber(String v) { this.callbackNumber = v; }
    public Boolean getCallbackVerified() { return callbackVerified; }
    public void setCallbackVerified(Boolean v) { this.callbackVerified = v; }
    public OffsetDateTime getCallbackVerifiedAt() { return callbackVerifiedAt; }
    public void setCallbackVerifiedAt(OffsetDateTime v) { this.callbackVerifiedAt = v; }
    public String getCallbackVerifiedBy() { return callbackVerifiedBy; }
    public void setCallbackVerifiedBy(String v) { this.callbackVerifiedBy = v; }
    public UUID getIncidentId() { return incidentId; }
    public void setIncidentId(UUID v) { this.incidentId = v; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
