package zw.gov.mohcc.impilo.coverage.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Authorisation header — the full §17 state machine. */
@Entity
@Table(name = "cv_authorisations")
public class AuthorisationEntity {

    @Id @Column(name = "id", nullable = false)
    private UUID id;
    @Column(name = "tenant_id", nullable = false) private UUID tenantId;
    @Column(name = "pod_id", nullable = false, length = 64) private String podId = "national-spine";
    @Column(name = "coverage_id", nullable = false) private UUID coverageId;
    @Column(name = "member_cpid", nullable = false, length = 255) private String memberCpid;
    @Column(name = "auth_type", nullable = false, length = 32) private String authType = "PRIOR";
    @Column(name = "requesting_provider_id", length = 128) private String requestingProviderId;
    @Column(name = "facility_id", length = 128) private String facilityId;
    @Column(name = "referral_id") private UUID referralId;
    @Column(name = "diagnosis_system", length = 64) private String diagnosisSystem;
    @Column(name = "diagnosis_code", length = 64) private String diagnosisCode;
    @Column(name = "clinical_justification", columnDefinition = "TEXT") private String clinicalJustification;
    @Column(name = "urgency", nullable = false, length = 16) private String urgency = "ROUTINE";
    @Column(name = "proposed_service_date") private LocalDate proposedServiceDate;
    @Column(name = "estimated_amount") private BigDecimal estimatedAmount;
    @Column(name = "status", nullable = false, length = 24) private String status = "DRAFT";
    @Column(name = "reviewer_id", length = 128) private String reviewerId;
    @Column(name = "decision_at") private OffsetDateTime decisionAt;
    @Column(name = "validity_from") private LocalDate validityFrom;
    @Column(name = "validity_to") private LocalDate validityTo;
    @Column(name = "denial_reason", length = 255) private String denialReason;
    @Column(name = "information_request", columnDefinition = "TEXT") private String informationRequest;
    @Column(name = "conditions", columnDefinition = "TEXT") private String conditions;
    @Column(name = "correlation_id") private UUID correlationId;
    @Column(name = "created_at", nullable = false) private OffsetDateTime createdAt = OffsetDateTime.now();
    @Column(name = "updated_at", nullable = false) private OffsetDateTime updatedAt = OffsetDateTime.now();

    public AuthorisationEntity() {}

    public static AuthorisationEntity create(UUID tenantId, String podId, UUID coverageId, String memberCpid) {
        AuthorisationEntity a = new AuthorisationEntity();
        a.id = UUID.randomUUID();
        a.tenantId = tenantId;
        a.podId = podId != null ? podId : "national-spine";
        a.coverageId = coverageId;
        a.memberCpid = memberCpid;
        return a;
    }

    public void touch() { this.updatedAt = OffsetDateTime.now(); }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public String getPodId() { return podId; }
    public UUID getCoverageId() { return coverageId; }
    public String getMemberCpid() { return memberCpid; }
    public String getAuthType() { return authType; }
    public void setAuthType(String v) { this.authType = v; }
    public String getRequestingProviderId() { return requestingProviderId; }
    public void setRequestingProviderId(String v) { this.requestingProviderId = v; }
    public String getFacilityId() { return facilityId; }
    public void setFacilityId(String v) { this.facilityId = v; }
    public UUID getReferralId() { return referralId; }
    public void setReferralId(UUID v) { this.referralId = v; }
    public String getDiagnosisSystem() { return diagnosisSystem; }
    public void setDiagnosisSystem(String v) { this.diagnosisSystem = v; }
    public String getDiagnosisCode() { return diagnosisCode; }
    public void setDiagnosisCode(String v) { this.diagnosisCode = v; }
    public String getClinicalJustification() { return clinicalJustification; }
    public void setClinicalJustification(String v) { this.clinicalJustification = v; }
    public String getUrgency() { return urgency; }
    public void setUrgency(String v) { this.urgency = v; }
    public LocalDate getProposedServiceDate() { return proposedServiceDate; }
    public void setProposedServiceDate(LocalDate v) { this.proposedServiceDate = v; }
    public BigDecimal getEstimatedAmount() { return estimatedAmount; }
    public void setEstimatedAmount(BigDecimal v) { this.estimatedAmount = v; }
    public String getStatus() { return status; }
    public void setStatus(String v) { this.status = v; this.updatedAt = OffsetDateTime.now(); }
    public String getReviewerId() { return reviewerId; }
    public void setReviewerId(String v) { this.reviewerId = v; }
    public OffsetDateTime getDecisionAt() { return decisionAt; }
    public void setDecisionAt(OffsetDateTime v) { this.decisionAt = v; }
    public LocalDate getValidityFrom() { return validityFrom; }
    public void setValidityFrom(LocalDate v) { this.validityFrom = v; }
    public LocalDate getValidityTo() { return validityTo; }
    public void setValidityTo(LocalDate v) { this.validityTo = v; }
    public String getDenialReason() { return denialReason; }
    public void setDenialReason(String v) { this.denialReason = v; }
    public String getInformationRequest() { return informationRequest; }
    public void setInformationRequest(String v) { this.informationRequest = v; }
    public String getConditions() { return conditions; }
    public void setConditions(String v) { this.conditions = v; }
    public UUID getCorrelationId() { return correlationId; }
    public void setCorrelationId(UUID v) { this.correlationId = v; }
}
