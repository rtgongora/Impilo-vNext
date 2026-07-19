package zw.gov.mohcc.impilo.indawo.domain;

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

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * SJ2 (D-L5): a site registration case — ONE generic registrant receipt; the
 * silent-block-vs-provisional outcome and the match context are steward-only.
 */
@Entity
@Table(name = "ind_site_registration_case")
public class SiteRegistrationCaseEntity {

    public static final String OUTCOME_DUPLICATE_REVIEW = "DUPLICATE_REVIEW";
    public static final String OUTCOME_PROVISIONAL_CREATED = "PROVISIONAL_CREATED";
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

    @Column(name = "site_category", length = 64)
    private String siteCategory;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "submitted_payload", nullable = false, columnDefinition = "jsonb")
    private String submittedPayload;

    @Column(name = "evidence_ref", nullable = false, length = 512)
    private String evidenceRef;

    @Column(name = "outcome", nullable = false, length = 32)
    private String outcome;

    @Column(name = "matched_site_id")
    private UUID matchedSiteId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "match_context", columnDefinition = "jsonb")
    private String matchContext;

    @Column(name = "provisional_site_id")
    private UUID provisionalSiteId;

    @Column(name = "reviewed_by")
    private String reviewedBy;

    @Column(name = "reviewed_at")
    private OffsetDateTime reviewedAt;

    @Column(name = "review_note", columnDefinition = "TEXT")
    private String reviewNote;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
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
    public String getSiteCategory() { return siteCategory; }
    public void setSiteCategory(String siteCategory) { this.siteCategory = siteCategory; }
    public String getSubmittedPayload() { return submittedPayload; }
    public void setSubmittedPayload(String submittedPayload) { this.submittedPayload = submittedPayload; }
    public String getEvidenceRef() { return evidenceRef; }
    public void setEvidenceRef(String evidenceRef) { this.evidenceRef = evidenceRef; }
    public String getOutcome() { return outcome; }
    public void setOutcome(String outcome) { this.outcome = outcome; }
    public UUID getMatchedSiteId() { return matchedSiteId; }
    public void setMatchedSiteId(UUID matchedSiteId) { this.matchedSiteId = matchedSiteId; }
    public String getMatchContext() { return matchContext; }
    public void setMatchContext(String matchContext) { this.matchContext = matchContext; }
    public UUID getProvisionalSiteId() { return provisionalSiteId; }
    public void setProvisionalSiteId(UUID provisionalSiteId) { this.provisionalSiteId = provisionalSiteId; }
    public String getReviewedBy() { return reviewedBy; }
    public void setReviewedBy(String reviewedBy) { this.reviewedBy = reviewedBy; }
    public OffsetDateTime getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(OffsetDateTime reviewedAt) { this.reviewedAt = reviewedAt; }
    public String getReviewNote() { return reviewNote; }
    public void setReviewNote(String reviewNote) { this.reviewNote = reviewNote; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
