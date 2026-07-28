package zw.gov.mohcc.impilo.mvumo.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "consent_request", schema = "mvumo")
public class ConsentRequestEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(nullable = false, length = 64)
    private String state;

    @Column(name = "subject_patient_ref", nullable = false)
    private String subjectPatientRef;

    @Column(name = "consent_type", nullable = false, length = 96)
    private String consentType;

    @Column(name = "template_id")
    private UUID templateId;

    @Column(name = "required_assurance", nullable = false, length = 32)
    private String requiredAssurance;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "context_json", nullable = false, columnDefinition = "jsonb")
    private String contextJson = "{}";

    @Column(name = "workflow_ref", length = 255)
    private String workflowRef;

    @Column(name = "encounter_ref", length = 255)
    private String encounterRef;

    @Column(name = "actual_method", length = 64)
    private String actualMethod;

    @Column(name = "actual_assurance", length = 32)
    private String actualAssurance;

    @Column(name = "proof_ref", length = 512)
    private String proofRef;

    @Column(name = "explainer_ref", length = 255)
    private String explainerRef;

    @Column(name = "granted_by_ref", length = 255)
    private String grantedByRef;

    @Column(name = "refused_reason", columnDefinition = "text")
    private String refusedReason;

    /** Trust-layer {@code tshepo-consent-service} directive id (FHIR Consent row), when materialised. */
    @Column(name = "tshepo_consent_id")
    private UUID tshepoConsentId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    // ── Wave P15 (pipeline §28 demonstration 3) — child assent, distinct from guardian consent.
    // Columns added by V300 (Wave P5) but never mapped onto this entity — the same
    // "a column with nothing resolving it" state V005/V007 each found and fixed for a different
    // service. GUARDIAN/decision-maker fields were already unmapped for the same reason; both
    // are added together here since demonstration 3 needs guardian consent AND child assent
    // recorded as two distinct, coexisting facts.
    @Column(name = "decision_maker_type", length = 32)
    private String decisionMakerType;

    @Column(name = "decision_maker_ref")
    private String decisionMakerRef;

    @Column(name = "decision_maker_basis", columnDefinition = "text")
    private String decisionMakerBasis;

    @Column(name = "assent_sought")
    private Boolean assentSought;

    @Column(name = "assent_outcome", length = 24)
    private String assentOutcome;

    @Column(name = "assent_notes", columnDefinition = "text")
    private String assentNotes;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public String getSubjectPatientRef() {
        return subjectPatientRef;
    }

    public void setSubjectPatientRef(String subjectPatientRef) {
        this.subjectPatientRef = subjectPatientRef;
    }

    public String getConsentType() {
        return consentType;
    }

    public void setConsentType(String consentType) {
        this.consentType = consentType;
    }

    public UUID getTemplateId() {
        return templateId;
    }

    public void setTemplateId(UUID templateId) {
        this.templateId = templateId;
    }

    public String getRequiredAssurance() {
        return requiredAssurance;
    }

    public void setRequiredAssurance(String requiredAssurance) {
        this.requiredAssurance = requiredAssurance;
    }

    public String getContextJson() {
        return contextJson;
    }

    public void setContextJson(String contextJson) {
        this.contextJson = contextJson;
    }

    public String getWorkflowRef() {
        return workflowRef;
    }

    public void setWorkflowRef(String workflowRef) {
        this.workflowRef = workflowRef;
    }

    public String getEncounterRef() {
        return encounterRef;
    }

    public void setEncounterRef(String encounterRef) {
        this.encounterRef = encounterRef;
    }

    public String getActualMethod() {
        return actualMethod;
    }

    public void setActualMethod(String actualMethod) {
        this.actualMethod = actualMethod;
    }

    public String getActualAssurance() {
        return actualAssurance;
    }

    public void setActualAssurance(String actualAssurance) {
        this.actualAssurance = actualAssurance;
    }

    public String getProofRef() {
        return proofRef;
    }

    public void setProofRef(String proofRef) {
        this.proofRef = proofRef;
    }

    public String getExplainerRef() {
        return explainerRef;
    }

    public void setExplainerRef(String explainerRef) {
        this.explainerRef = explainerRef;
    }

    public String getGrantedByRef() {
        return grantedByRef;
    }

    public void setGrantedByRef(String grantedByRef) {
        this.grantedByRef = grantedByRef;
    }

    public String getRefusedReason() {
        return refusedReason;
    }

    public void setRefusedReason(String refusedReason) {
        this.refusedReason = refusedReason;
    }

    public UUID getTshepoConsentId() {
        return tshepoConsentId;
    }

    public void setTshepoConsentId(UUID tshepoConsentId) {
        this.tshepoConsentId = tshepoConsentId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public String getDecisionMakerType() {
        return decisionMakerType;
    }

    public void setDecisionMakerType(String decisionMakerType) {
        this.decisionMakerType = decisionMakerType;
    }

    public String getDecisionMakerRef() {
        return decisionMakerRef;
    }

    public void setDecisionMakerRef(String decisionMakerRef) {
        this.decisionMakerRef = decisionMakerRef;
    }

    public String getDecisionMakerBasis() {
        return decisionMakerBasis;
    }

    public void setDecisionMakerBasis(String decisionMakerBasis) {
        this.decisionMakerBasis = decisionMakerBasis;
    }

    public Boolean getAssentSought() {
        return assentSought;
    }

    public void setAssentSought(Boolean assentSought) {
        this.assentSought = assentSought;
    }

    public String getAssentOutcome() {
        return assentOutcome;
    }

    public void setAssentOutcome(String assentOutcome) {
        this.assentOutcome = assentOutcome;
    }

    public String getAssentNotes() {
        return assentNotes;
    }

    public void setAssentNotes(String assentNotes) {
        this.assentNotes = assentNotes;
    }
}
