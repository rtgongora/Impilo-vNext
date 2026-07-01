package zw.gov.mohcc.impilo.pct.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Encounter-bound structured form response (PCT SoR). Definition lives in forms-service; this row
 * version-locks against the immutable {@code formSchemaVersionId} captured at DRAFT.
 */
@Entity
@Table(name = "pct_form_response")
public class FormResponseEntity {

    @Id
    @Column(name = "response_id", nullable = false)
    private UUID responseId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "subject_cpid", nullable = false)
    private String subjectCpid;

    @Column(name = "journey_id")
    private String journeyId;

    @Column(name = "encounter_id")
    private String encounterId;

    @Column(name = "encounter_ref")
    private UUID encounterRef;

    @Column(name = "butano_encounter_ref")
    private String butanoEncounterRef;

    @Column(name = "form_key", nullable = false)
    private String formKey;

    @Column(name = "form_schema_id", nullable = false)
    private String formSchemaId;

    @Column(name = "form_version", nullable = false)
    private int formVersion;

    @Column(name = "form_schema_version_id", nullable = false)
    private String formSchemaVersionId;

    @Column(name = "form_title")
    private String formTitle;

    @Column(name = "status", nullable = false)
    private String status = "DRAFT";

    @Column(name = "obligation")
    private String obligation;

    @Column(name = "countersign_required", nullable = false)
    private boolean countersignRequired = false;

    @Column(name = "resolver_audit_ref")
    private UUID resolverAuditRef;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "answers", nullable = false, columnDefinition = "jsonb")
    private String answers = "{}";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "questionnaire_response", columnDefinition = "jsonb")
    private String questionnaireResponse;

    @Column(name = "provider_id")
    private String providerId;

    @Column(name = "provider_cadre")
    private String providerCadre;

    @Column(name = "provider_role")
    private String providerRole;

    @Column(name = "facility_id")
    private UUID facilityId;

    @Column(name = "workspace_id")
    private UUID workspaceId;

    @Column(name = "device_source")
    private String deviceSource;

    @Column(name = "offline_sync_status")
    private String offlineSyncStatus;

    @Column(name = "supersedes_response_id")
    private UUID supersedesResponseId;

    @Column(name = "superseded_by_response_id")
    private UUID supersededByResponseId;

    @Column(name = "created_by", nullable = false)
    private String createdBy;

    @Column(name = "submitted_by")
    private String submittedBy;

    @Column(name = "submitted_at")
    private OffsetDateTime submittedAt;

    @Column(name = "voided_by")
    private String voidedBy;

    @Column(name = "voided_at")
    private OffsetDateTime voidedAt;

    @Column(name = "void_reason")
    private String voidReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (responseId == null) {
            responseId = UUID.randomUUID();
        }
        OffsetDateTime now = OffsetDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    public UUID getResponseId() { return responseId; }
    public void setResponseId(UUID responseId) { this.responseId = responseId; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public String getSubjectCpid() { return subjectCpid; }
    public void setSubjectCpid(String subjectCpid) { this.subjectCpid = subjectCpid; }

    public String getJourneyId() { return journeyId; }
    public void setJourneyId(String journeyId) { this.journeyId = journeyId; }

    public String getEncounterId() { return encounterId; }
    public void setEncounterId(String encounterId) { this.encounterId = encounterId; }

    public UUID getEncounterRef() { return encounterRef; }
    public void setEncounterRef(UUID encounterRef) { this.encounterRef = encounterRef; }

    public String getButanoEncounterRef() { return butanoEncounterRef; }
    public void setButanoEncounterRef(String butanoEncounterRef) { this.butanoEncounterRef = butanoEncounterRef; }

    public String getFormKey() { return formKey; }
    public void setFormKey(String formKey) { this.formKey = formKey; }

    public String getFormSchemaId() { return formSchemaId; }
    public void setFormSchemaId(String formSchemaId) { this.formSchemaId = formSchemaId; }

    public int getFormVersion() { return formVersion; }
    public void setFormVersion(int formVersion) { this.formVersion = formVersion; }

    public String getFormSchemaVersionId() { return formSchemaVersionId; }
    public void setFormSchemaVersionId(String formSchemaVersionId) { this.formSchemaVersionId = formSchemaVersionId; }

    public String getFormTitle() { return formTitle; }
    public void setFormTitle(String formTitle) { this.formTitle = formTitle; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getObligation() { return obligation; }
    public void setObligation(String obligation) { this.obligation = obligation; }

    public boolean isCountersignRequired() { return countersignRequired; }
    public void setCountersignRequired(boolean countersignRequired) { this.countersignRequired = countersignRequired; }

    public UUID getResolverAuditRef() { return resolverAuditRef; }
    public void setResolverAuditRef(UUID resolverAuditRef) { this.resolverAuditRef = resolverAuditRef; }

    public String getAnswers() { return answers; }
    public void setAnswers(String answers) { this.answers = answers; }

    public String getQuestionnaireResponse() { return questionnaireResponse; }
    public void setQuestionnaireResponse(String questionnaireResponse) { this.questionnaireResponse = questionnaireResponse; }

    public String getProviderId() { return providerId; }
    public void setProviderId(String providerId) { this.providerId = providerId; }

    public String getProviderCadre() { return providerCadre; }
    public void setProviderCadre(String providerCadre) { this.providerCadre = providerCadre; }

    public String getProviderRole() { return providerRole; }
    public void setProviderRole(String providerRole) { this.providerRole = providerRole; }

    public UUID getFacilityId() { return facilityId; }
    public void setFacilityId(UUID facilityId) { this.facilityId = facilityId; }

    public UUID getWorkspaceId() { return workspaceId; }
    public void setWorkspaceId(UUID workspaceId) { this.workspaceId = workspaceId; }

    public String getDeviceSource() { return deviceSource; }
    public void setDeviceSource(String deviceSource) { this.deviceSource = deviceSource; }

    public String getOfflineSyncStatus() { return offlineSyncStatus; }
    public void setOfflineSyncStatus(String offlineSyncStatus) { this.offlineSyncStatus = offlineSyncStatus; }

    public UUID getSupersedesResponseId() { return supersedesResponseId; }
    public void setSupersedesResponseId(UUID supersedesResponseId) { this.supersedesResponseId = supersedesResponseId; }

    public UUID getSupersededByResponseId() { return supersededByResponseId; }
    public void setSupersededByResponseId(UUID supersededByResponseId) { this.supersededByResponseId = supersededByResponseId; }

    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }

    public String getSubmittedBy() { return submittedBy; }
    public void setSubmittedBy(String submittedBy) { this.submittedBy = submittedBy; }

    public OffsetDateTime getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(OffsetDateTime submittedAt) { this.submittedAt = submittedAt; }

    public String getVoidedBy() { return voidedBy; }
    public void setVoidedBy(String voidedBy) { this.voidedBy = voidedBy; }

    public OffsetDateTime getVoidedAt() { return voidedAt; }
    public void setVoidedAt(OffsetDateTime voidedAt) { this.voidedAt = voidedAt; }

    public String getVoidReason() { return voidReason; }
    public void setVoidReason(String voidReason) { this.voidReason = voidReason; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
