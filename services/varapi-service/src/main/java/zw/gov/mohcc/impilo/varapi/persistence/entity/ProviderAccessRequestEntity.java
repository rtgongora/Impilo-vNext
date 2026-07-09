package zw.gov.mohcc.impilo.varapi.persistence.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/**
 * A durable self-service provider-access request (IATG Journey D).
 *
 * <p>The numeric {@code id} is internal; {@code publicId} is the opaque handle
 * the applicant uses to check status. Evidence identifiers (council/EC) are
 * stored masked — the raw values live only in the registries of record.</p>
 */
@Entity
@Table(name = "provider_access_request", schema = "varapi")
public class ProviderAccessRequestEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, length = 40, unique = true)
    private String publicId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "applicant_health_id", nullable = false)
    private UUID applicantHealthId;

    /** {@link zw.gov.mohcc.impilo.varapi.enums.ProviderAccessRequestType} name. */
    @Column(name = "request_type", nullable = false, length = 40)
    private String requestType;

    /** {@link zw.gov.mohcc.impilo.varapi.enums.ProviderAccessRequestStatus} name. */
    @Column(name = "status", nullable = false, length = 40)
    private String status;

    /** The actor role responsible for the next decision (e.g. NATIONAL_ADMINISTRATOR). */
    @Column(name = "next_actor", length = 80)
    private String nextActor;

    @Column(name = "profession", length = 120)
    private String profession;

    @Column(name = "council_code", length = 40)
    private String councilCode;

    @Column(name = "council_number_masked", length = 40)
    private String councilNumberMasked;

    @Column(name = "ec_number_masked", length = 40)
    private String ecNumberMasked;

    @Column(name = "organization_ref", length = 120)
    private String organizationRef;

    @Column(name = "evidence_summary", length = 500)
    private String evidenceSummary;

    /** Human-readable reason for a pending / rejected / needs-info state. */
    @Column(name = "reason", length = 500)
    private String reason;

    /** Set (masked) only if/when a Provider ID is linked/issued as a result. */
    @Column(name = "provider_public_id", length = 40)
    private String providerPublicId;

    /** Reviewer (actor id) who recorded the decision — Trust Console review lane. */
    @Column(name = "decided_by", length = 255)
    private String decidedBy;

    @Column(name = "decided_at")
    private Instant decidedAt;

    /** Reviewer-supplied note explaining the decision. */
    @Column(name = "decision_note", length = 500)
    private String decisionNote;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by", length = 255)
    private String createdBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public Long getId() { return id; }

    public String getPublicId() { return publicId; }
    public void setPublicId(String publicId) { this.publicId = publicId; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public UUID getApplicantHealthId() { return applicantHealthId; }
    public void setApplicantHealthId(UUID applicantHealthId) { this.applicantHealthId = applicantHealthId; }

    public String getRequestType() { return requestType; }
    public void setRequestType(String requestType) { this.requestType = requestType; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getNextActor() { return nextActor; }
    public void setNextActor(String nextActor) { this.nextActor = nextActor; }

    public String getProfession() { return profession; }
    public void setProfession(String profession) { this.profession = profession; }

    public String getCouncilCode() { return councilCode; }
    public void setCouncilCode(String councilCode) { this.councilCode = councilCode; }

    public String getCouncilNumberMasked() { return councilNumberMasked; }
    public void setCouncilNumberMasked(String councilNumberMasked) { this.councilNumberMasked = councilNumberMasked; }

    public String getEcNumberMasked() { return ecNumberMasked; }
    public void setEcNumberMasked(String ecNumberMasked) { this.ecNumberMasked = ecNumberMasked; }

    public String getOrganizationRef() { return organizationRef; }
    public void setOrganizationRef(String organizationRef) { this.organizationRef = organizationRef; }

    public String getEvidenceSummary() { return evidenceSummary; }
    public void setEvidenceSummary(String evidenceSummary) { this.evidenceSummary = evidenceSummary; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public String getProviderPublicId() { return providerPublicId; }
    public void setProviderPublicId(String providerPublicId) { this.providerPublicId = providerPublicId; }

    public String getDecidedBy() { return decidedBy; }
    public void setDecidedBy(String decidedBy) { this.decidedBy = decidedBy; }

    public Instant getDecidedAt() { return decidedAt; }
    public void setDecidedAt(Instant decidedAt) { this.decidedAt = decidedAt; }

    public String getDecisionNote() { return decisionNote; }
    public void setDecisionNote(String decisionNote) { this.decisionNote = decisionNote; }

    public Instant getCreatedAt() { return createdAt; }

    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }

    public Instant getUpdatedAt() { return updatedAt; }
}
