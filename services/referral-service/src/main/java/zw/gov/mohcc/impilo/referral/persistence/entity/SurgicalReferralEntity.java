package zw.gov.mohcc.impilo.referral.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Surgical referral companion (1:1 with {@link ReferralEntity}). Carries the
 * surgical-domain clinical detail and the receiving-team decision. The referral
 * lifecycle FSM stays on {@link ReferralEntity}; this is depth, never a copy.
 *
 * <p>{@code decision == ACCEPTED} is the only state that triggers the elective
 * funnel into scheduling (waitlist placement).
 */
@Entity
@Table(name = "surgical_referral", schema = "referral")
public class SurgicalReferralEntity {

    public static final String DECISION_PENDING = "PENDING";
    public static final String DECISION_ACCEPTED = "ACCEPTED";
    public static final String DECISION_DECLINED = "DECLINED";
    public static final String DECISION_REDIRECTED = "REDIRECTED";
    public static final String DECISION_INFO_REQUESTED = "INFO_REQUESTED";

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "referral_id", nullable = false)
    private UUID referralId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "patient_id", nullable = false)
    private String patientId;

    @Column(name = "indication")
    private String indication;

    @Column(name = "intended_procedure_zibo_code")
    private String intendedProcedureZiboCode;

    @Column(name = "urgency", nullable = false)
    private String urgency = "ROUTINE";

    @Column(name = "clinical_priority")
    private String clinicalPriority;

    @Column(name = "laterality")
    private String laterality;

    @Column(name = "anatomical_site")
    private String anatomicalSite;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "requested_investigations", columnDefinition = "jsonb", nullable = false)
    private List<String> requestedInvestigations = new ArrayList<>();

    @Column(name = "anaesthesia_review_requested", nullable = false)
    private boolean anaesthesiaReviewRequested;

    @Column(name = "specialist_review_requested", nullable = false)
    private boolean specialistReviewRequested;

    @Column(name = "target_specialty")
    private String targetSpecialty;

    @Column(name = "decision", nullable = false)
    private String decision = DECISION_PENDING;

    @Column(name = "decision_reason")
    private String decisionReason;

    @Column(name = "waitlist_entry_ref")
    private String waitlistEntryRef;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected SurgicalReferralEntity() {
    }

    public SurgicalReferralEntity(UUID id, UUID referralId, UUID tenantId, String patientId) {
        this.id = id;
        this.referralId = referralId;
        this.tenantId = tenantId;
        this.patientId = patientId;
        this.decision = DECISION_PENDING;
        this.createdAt = OffsetDateTime.now();
        this.updatedAt = this.createdAt;
    }

    @PrePersist
    @PreUpdate
    void touchTimestamps() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
        updatedAt = OffsetDateTime.now();
        if (requestedInvestigations == null) {
            requestedInvestigations = new ArrayList<>();
        }
    }

    /**
     * Records a receiving-team decision. Guards illegal transitions: a terminally
     * declined/redirected referral cannot be re-decided.
     */
    public void decide(String newDecision, String reason) {
        if (DECISION_DECLINED.equals(this.decision) || DECISION_REDIRECTED.equals(this.decision)) {
            throw new IllegalStateException("Surgical referral already terminal: " + this.decision);
        }
        this.decision = newDecision;
        this.decisionReason = reason;
    }

    public Map<String, Object> toEnvelope() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", id.toString());
        out.put("referralId", referralId.toString());
        out.put("tenantId", tenantId.toString());
        out.put("patientId", patientId);
        out.put("indication", indication);
        out.put("intendedProcedureZiboCode", intendedProcedureZiboCode);
        out.put("urgency", urgency);
        out.put("clinicalPriority", clinicalPriority);
        out.put("laterality", laterality);
        out.put("anatomicalSite", anatomicalSite);
        out.put("requestedInvestigations", requestedInvestigations);
        out.put("anaesthesiaReviewRequested", anaesthesiaReviewRequested);
        out.put("specialistReviewRequested", specialistReviewRequested);
        out.put("targetSpecialty", targetSpecialty);
        out.put("decision", decision);
        out.put("decisionReason", decisionReason);
        out.put("waitlistEntryRef", waitlistEntryRef);
        out.put("createdAt", createdAt != null ? createdAt.toString() : null);
        out.put("updatedAt", updatedAt != null ? updatedAt.toString() : null);
        return out;
    }

    public UUID getId() {
        return id;
    }

    public UUID getReferralId() {
        return referralId;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public String getPatientId() {
        return patientId;
    }

    public String getDecision() {
        return decision;
    }

    public String getIntendedProcedureZiboCode() {
        return intendedProcedureZiboCode;
    }

    public String getClinicalPriority() {
        return clinicalPriority;
    }

    public String getTargetSpecialty() {
        return targetSpecialty;
    }

    public String getWaitlistEntryRef() {
        return waitlistEntryRef;
    }

    public void setIndication(String indication) {
        this.indication = indication;
    }

    public void setIntendedProcedureZiboCode(String intendedProcedureZiboCode) {
        this.intendedProcedureZiboCode = intendedProcedureZiboCode;
    }

    public void setUrgency(String urgency) {
        if (urgency != null && !urgency.isBlank()) {
            this.urgency = urgency;
        }
    }

    public void setClinicalPriority(String clinicalPriority) {
        this.clinicalPriority = clinicalPriority;
    }

    public void setLaterality(String laterality) {
        this.laterality = laterality;
    }

    public void setAnatomicalSite(String anatomicalSite) {
        this.anatomicalSite = anatomicalSite;
    }

    public void setRequestedInvestigations(List<String> requestedInvestigations) {
        this.requestedInvestigations = requestedInvestigations != null ? requestedInvestigations : new ArrayList<>();
    }

    public void setAnaesthesiaReviewRequested(boolean anaesthesiaReviewRequested) {
        this.anaesthesiaReviewRequested = anaesthesiaReviewRequested;
    }

    public void setSpecialistReviewRequested(boolean specialistReviewRequested) {
        this.specialistReviewRequested = specialistReviewRequested;
    }

    public void setTargetSpecialty(String targetSpecialty) {
        this.targetSpecialty = targetSpecialty;
    }

    public void setWaitlistEntryRef(String waitlistEntryRef) {
        this.waitlistEntryRef = waitlistEntryRef;
    }
}
