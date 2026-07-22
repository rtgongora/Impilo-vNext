package zw.gov.mohcc.impilo.pct.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "pct_referrals")
public class ReferralEntity {

    @Id
    @Column(name = "referral_id", nullable = false)
    private UUID referralId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    /** Optimistic-concurrency guard (TM-B6). A concurrent edit of the same referral
     *  (double-accept, provider-complete vs timer-expire) is detected as a stale write
     *  and surfaced as 409 STALE_WRITE rather than silently last-write-wins. */
    @Version
    @Column(name = "version", nullable = false)
    private Long version = 0L;

    @Column(name = "patient_cpid", nullable = false)
    private String patientCpid;

    @Column(name = "encounter_id")
    private String encounterId;

    @Column(name = "provider_id")
    private String providerId;

    @Column(name = "facility_id")
    private String facilityId;

    @Column(name = "referral_type", nullable = false)
    private String referralType = "SPECIALIST";

    @Column(name = "specialty")
    private String specialty;

    @Column(name = "urgency", nullable = false)
    private String urgency = "ROUTINE";

    @Column(name = "reason", nullable = false)
    private String reason;

    @Column(name = "clinical_summary")
    private String clinicalSummary;

    @Column(name = "status", nullable = false)
    private String status = "DRAFT";

    @Column(name = "stage", nullable = false)
    private Integer stage = 1;

    @Column(name = "preferred_mode")
    private String preferredMode;

    @Column(name = "modality")
    private String modality;

    @Column(name = "virtual_mode")
    private String virtualMode;

    // TM-B5 (B5-2): runtime effective media rung (VIDEO|AUDIO|ASYNC), distinct from the requested
    // virtual_mode. NULL = as requested. Stepped down the downgrade ladder on media failure/bandwidth.
    @Column(name = "media_modality")
    private String mediaModality;

    // Billing context captured at referral time (composed by the BFF from coverage/registry)
    // and propagated to COSTA on the teleconsult value-trigger so charging rules can fire.
    @Column(name = "patient_category")
    private String patientCategory;

    @Column(name = "facility_category")
    private String facilityCategory;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "routing_target", columnDefinition = "jsonb")
    private String routingTarget = "{}";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "attachment_document_ids", columnDefinition = "jsonb")
    private String attachmentDocumentIds = "[]";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "messages", columnDefinition = "jsonb")
    private String messages = "[]";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "responses", columnDefinition = "jsonb")
    private String responses = "[]";

    @Column(name = "consent_type")
    private String consentType;

    @Column(name = "consent_status")
    private String consentStatus;

    @Column(name = "consent_reference")
    private String consentReference;

    @Column(name = "mvumo_session_id")
    private String mvumoSessionId;

    @Column(name = "tshepo_decision_id")
    private String tshepoDecisionId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "completion_payload", columnDefinition = "jsonb")
    private String completionPayload = "{}";

    /** Stage-6 structured clinical response (C7): {diagnosis, actionPlan, redFlags, followUp}. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "structured_response", columnDefinition = "jsonb")
    private String structuredResponse;

    @Column(name = "routing_kind")
    private String routingKind;

    @Column(name = "routing_pool_id")
    private String routingPoolId;

    @Column(name = "routed_at")
    private OffsetDateTime routedAt;

    // Booking↔referral back-link (G18): the scheduling-service appointment for a scheduled
    // teleconsult, persisted here so the referral is the durable join (was only an appointment note).
    @Column(name = "appointment_id")
    private String appointmentId;

    @Column(name = "scheduled_at")
    private OffsetDateTime scheduledAt;

    // Recording writeback (G20): the rtc-gateway egress reference for this teleconsult, set by the
    // pct consumer of impilo.rtc.recording.available.v1 (matched via owningRef = referralId).
    @Column(name = "recording_ref")
    private String recordingRef;

    @Column(name = "recording_storage_key")
    private String recordingStorageKey;

    @Column(name = "submitted_at")
    private OffsetDateTime submittedAt;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (referralId == null) {
            referralId = UUID.randomUUID();
        }
        OffsetDateTime now = OffsetDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    public UUID getReferralId() { return referralId; }
    public void setReferralId(UUID referralId) { this.referralId = referralId; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
    public String getPatientCpid() { return patientCpid; }
    public void setPatientCpid(String patientCpid) { this.patientCpid = patientCpid; }
    public String getEncounterId() { return encounterId; }
    public void setEncounterId(String encounterId) { this.encounterId = encounterId; }
    public String getProviderId() { return providerId; }
    public void setProviderId(String providerId) { this.providerId = providerId; }
    public String getFacilityId() { return facilityId; }
    public void setFacilityId(String facilityId) { this.facilityId = facilityId; }
    public String getReferralType() { return referralType; }
    public void setReferralType(String referralType) { this.referralType = referralType; }
    public String getSpecialty() { return specialty; }
    public void setSpecialty(String specialty) { this.specialty = specialty; }
    public String getUrgency() { return urgency; }
    public void setUrgency(String urgency) { this.urgency = urgency; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getClinicalSummary() { return clinicalSummary; }
    public void setClinicalSummary(String clinicalSummary) { this.clinicalSummary = clinicalSummary; }
    public String getAppointmentId() { return appointmentId; }
    public void setAppointmentId(String appointmentId) { this.appointmentId = appointmentId; }
    public OffsetDateTime getScheduledAt() { return scheduledAt; }
    public void setScheduledAt(OffsetDateTime scheduledAt) { this.scheduledAt = scheduledAt; }
    public String getRecordingRef() { return recordingRef; }
    public void setRecordingRef(String recordingRef) { this.recordingRef = recordingRef; }
    public String getRecordingStorageKey() { return recordingStorageKey; }
    public void setRecordingStorageKey(String recordingStorageKey) { this.recordingStorageKey = recordingStorageKey; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Integer getStage() { return stage; }
    public void setStage(Integer stage) { this.stage = stage; }
    public String getPreferredMode() { return preferredMode; }
    public void setPreferredMode(String preferredMode) { this.preferredMode = preferredMode; }
    public String getModality() { return modality; }
    public void setModality(String modality) { this.modality = modality; }
    public String getMediaModality() { return mediaModality; }
    public void setMediaModality(String mediaModality) { this.mediaModality = mediaModality; }

    public String getVirtualMode() { return virtualMode; }
    public void setVirtualMode(String virtualMode) { this.virtualMode = virtualMode; }
    public String getPatientCategory() { return patientCategory; }
    public void setPatientCategory(String patientCategory) { this.patientCategory = patientCategory; }
    public String getFacilityCategory() { return facilityCategory; }
    public void setFacilityCategory(String facilityCategory) { this.facilityCategory = facilityCategory; }
    public String getRoutingTarget() { return routingTarget; }
    public void setRoutingTarget(String routingTarget) { this.routingTarget = routingTarget; }
    public String getAttachmentDocumentIds() { return attachmentDocumentIds; }
    public void setAttachmentDocumentIds(String attachmentDocumentIds) { this.attachmentDocumentIds = attachmentDocumentIds; }
    public String getMessages() { return messages; }
    public void setMessages(String messages) { this.messages = messages; }
    public String getResponses() { return responses; }
    public void setResponses(String responses) { this.responses = responses; }
    public String getConsentType() { return consentType; }
    public void setConsentType(String consentType) { this.consentType = consentType; }
    public String getConsentStatus() { return consentStatus; }
    public void setConsentStatus(String consentStatus) { this.consentStatus = consentStatus; }
    public String getConsentReference() { return consentReference; }
    public void setConsentReference(String consentReference) { this.consentReference = consentReference; }
    public String getMvumoSessionId() { return mvumoSessionId; }
    public void setMvumoSessionId(String mvumoSessionId) { this.mvumoSessionId = mvumoSessionId; }
    public String getTshepoDecisionId() { return tshepoDecisionId; }
    public void setTshepoDecisionId(String tshepoDecisionId) { this.tshepoDecisionId = tshepoDecisionId; }
    public String getCompletionPayload() { return completionPayload; }
    public void setCompletionPayload(String completionPayload) { this.completionPayload = completionPayload; }
    public String getStructuredResponse() { return structuredResponse; }
    public void setStructuredResponse(String structuredResponse) { this.structuredResponse = structuredResponse; }
    public String getRoutingKind() { return routingKind; }
    public void setRoutingKind(String routingKind) { this.routingKind = routingKind; }
    public String getRoutingPoolId() { return routingPoolId; }
    public void setRoutingPoolId(String routingPoolId) { this.routingPoolId = routingPoolId; }
    public OffsetDateTime getRoutedAt() { return routedAt; }
    public void setRoutedAt(OffsetDateTime routedAt) { this.routedAt = routedAt; }
    public OffsetDateTime getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(OffsetDateTime submittedAt) { this.submittedAt = submittedAt; }
    public OffsetDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(OffsetDateTime completedAt) { this.completedAt = completedAt; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
