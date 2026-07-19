package zw.gov.mohcc.impilo.tuso.persistence.entity;

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

/**
 * A self-service facility registration case (FJ2, D-L5). Every submission
 * lands here with ONE generic registrant-facing shape; the outcome — silent
 * duplicate block vs provisional creation — and the match context are steward
 * material only.
 */
@Entity
@Table(name = "facility_registration_case", schema = "tuso")
public class FacilityRegistrationCaseEntity {

    /** Credible match found → creation silently blocked, steward reviews. */
    public static final String OUTCOME_DUPLICATE_REVIEW = "DUPLICATE_REVIEW";
    /** No credible match → provisional (non-discoverable) facility created. */
    public static final String OUTCOME_PROVISIONAL_CREATED = "PROVISIONAL_CREATED";
    /** Steward resolved the case onto an existing facility. */
    public static final String OUTCOME_MATCHED_EXISTING = "MATCHED_EXISTING";
    public static final String OUTCOME_REJECTED = "REJECTED";
    public static final String OUTCOME_CONFIRMED = "CONFIRMED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "case_ref", nullable = false, length = 40)
    private String caseRef;

    @Column(name = "applicant_health_id", nullable = false, length = 128)
    private String applicantHealthId;

    @Column(name = "submitted_name", nullable = false)
    private String submittedName;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "submitted_payload", nullable = false, columnDefinition = "jsonb")
    private String submittedPayload;

    @Column(name = "evidence_ref", nullable = false, length = 512)
    private String evidenceRef;

    @Column(name = "outcome", nullable = false, length = 32)
    private String outcome;

    @Column(name = "matched_facility_uuid")
    private UUID matchedFacilityUuid;

    /** Steward-only: candidate uuids + match types + scores. Never serialised to registrants. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "match_context", columnDefinition = "jsonb")
    private String matchContext;

    @Column(name = "provisional_facility_uuid")
    private UUID provisionalFacilityUuid;

    @Column(name = "reviewed_by")
    private String reviewedBy;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "review_note", columnDefinition = "text")
    private String reviewNote;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public String getCaseRef() { return caseRef; }
    public void setCaseRef(String caseRef) { this.caseRef = caseRef; }
    public String getApplicantHealthId() { return applicantHealthId; }
    public void setApplicantHealthId(String applicantHealthId) { this.applicantHealthId = applicantHealthId; }
    public String getSubmittedName() { return submittedName; }
    public void setSubmittedName(String submittedName) { this.submittedName = submittedName; }
    public String getSubmittedPayload() { return submittedPayload; }
    public void setSubmittedPayload(String submittedPayload) { this.submittedPayload = submittedPayload; }
    public String getEvidenceRef() { return evidenceRef; }
    public void setEvidenceRef(String evidenceRef) { this.evidenceRef = evidenceRef; }
    public String getOutcome() { return outcome; }
    public void setOutcome(String outcome) { this.outcome = outcome; }
    public UUID getMatchedFacilityUuid() { return matchedFacilityUuid; }
    public void setMatchedFacilityUuid(UUID matchedFacilityUuid) { this.matchedFacilityUuid = matchedFacilityUuid; }
    public String getMatchContext() { return matchContext; }
    public void setMatchContext(String matchContext) { this.matchContext = matchContext; }
    public UUID getProvisionalFacilityUuid() { return provisionalFacilityUuid; }
    public void setProvisionalFacilityUuid(UUID provisionalFacilityUuid) { this.provisionalFacilityUuid = provisionalFacilityUuid; }
    public String getReviewedBy() { return reviewedBy; }
    public void setReviewedBy(String reviewedBy) { this.reviewedBy = reviewedBy; }
    public Instant getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(Instant reviewedAt) { this.reviewedAt = reviewedAt; }
    public String getReviewNote() { return reviewNote; }
    public void setReviewNote(String reviewNote) { this.reviewNote = reviewNote; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
