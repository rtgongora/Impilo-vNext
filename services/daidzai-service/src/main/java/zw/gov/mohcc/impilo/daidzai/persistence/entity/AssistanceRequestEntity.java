package zw.gov.mohcc.impilo.daidzai.persistence.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Assistance request — the crowdfunding CASE aggregate (V003). Daidzai owns the ask and
 * its verification lifecycle; money truth lives in mushe (referenced by
 * {@code contributionRequestId}), evidence in credential-verification, consent in tshepo.
 * {@code raisedAmount}/{@code donorCount} are an event-driven projection — never written
 * by API handlers.
 */
@Entity
@Table(name = "dai_assistance_request", schema = "daidzai")
public class AssistanceRequestEntity {

    @Id @Column(name = "id") private UUID id;
    @Column(name = "tenant_id", nullable = false) private UUID tenantId;
    @Column(name = "assistance_reference", nullable = false, length = 48) private String assistanceReference;
    @Column(name = "requester_type", nullable = false, length = 32) private String requesterType;
    @Column(name = "requester_actor_id", length = 128) private String requesterActorId;
    @Column(name = "subject_identity_mode", nullable = false, length = 24) private String subjectIdentityMode = "ANONYMOUS";
    @Column(name = "subject_cpid", length = 128) private String subjectCpid;
    @Column(name = "category", nullable = false, length = 48) private String category = "MEDICAL_COSTS";
    @Column(name = "title", nullable = false, length = 255) private String title;
    @Column(name = "story", columnDefinition = "TEXT") private String story;
    @Column(name = "target_amount", nullable = false, precision = 14, scale = 2) private BigDecimal targetAmount;
    @Column(name = "currency", nullable = false, length = 8) private String currency = "USD";
    @Column(name = "ends_at") private OffsetDateTime endsAt;
    @Column(name = "verification_status", nullable = false, length = 24) private String verificationStatus = "DRAFT";
    @Column(name = "contribution_request_id") private UUID contributionRequestId;
    @Column(name = "share_token", length = 64) private String shareToken;
    @Column(name = "consent_ref", length = 128) private String consentRef;
    @Column(name = "attestation_credential_id", length = 128) private String attestationCredentialId;
    @Column(name = "attestation_public_token", length = 128) private String attestationPublicToken;
    @Column(name = "verifying_org_ref", length = 128) private String verifyingOrgRef;
    @Column(name = "verified_at") private OffsetDateTime verifiedAt;
    @Column(name = "raised_amount", nullable = false, precision = 14, scale = 2) private BigDecimal raisedAmount = BigDecimal.ZERO;
    @Column(name = "donor_count", nullable = false) private Integer donorCount = 0;
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
    public String getAssistanceReference() { return assistanceReference; }
    public void setAssistanceReference(String assistanceReference) { this.assistanceReference = assistanceReference; }
    public String getRequesterType() { return requesterType; }
    public void setRequesterType(String requesterType) { this.requesterType = requesterType; }
    public String getRequesterActorId() { return requesterActorId; }
    public void setRequesterActorId(String requesterActorId) { this.requesterActorId = requesterActorId; }
    public String getSubjectIdentityMode() { return subjectIdentityMode; }
    public void setSubjectIdentityMode(String subjectIdentityMode) { this.subjectIdentityMode = subjectIdentityMode; }
    public String getSubjectCpid() { return subjectCpid; }
    public void setSubjectCpid(String subjectCpid) { this.subjectCpid = subjectCpid; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getStory() { return story; }
    public void setStory(String story) { this.story = story; }
    public BigDecimal getTargetAmount() { return targetAmount; }
    public void setTargetAmount(BigDecimal targetAmount) { this.targetAmount = targetAmount; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public OffsetDateTime getEndsAt() { return endsAt; }
    public void setEndsAt(OffsetDateTime endsAt) { this.endsAt = endsAt; }
    public String getVerificationStatus() { return verificationStatus; }
    public void setVerificationStatus(String verificationStatus) { this.verificationStatus = verificationStatus; }
    public UUID getContributionRequestId() { return contributionRequestId; }
    public void setContributionRequestId(UUID contributionRequestId) { this.contributionRequestId = contributionRequestId; }
    public String getShareToken() { return shareToken; }
    public void setShareToken(String shareToken) { this.shareToken = shareToken; }
    public String getConsentRef() { return consentRef; }
    public void setConsentRef(String consentRef) { this.consentRef = consentRef; }
    public String getAttestationCredentialId() { return attestationCredentialId; }
    public void setAttestationCredentialId(String attestationCredentialId) { this.attestationCredentialId = attestationCredentialId; }
    public String getAttestationPublicToken() { return attestationPublicToken; }
    public void setAttestationPublicToken(String attestationPublicToken) { this.attestationPublicToken = attestationPublicToken; }
    public String getVerifyingOrgRef() { return verifyingOrgRef; }
    public void setVerifyingOrgRef(String verifyingOrgRef) { this.verifyingOrgRef = verifyingOrgRef; }
    public OffsetDateTime getVerifiedAt() { return verifiedAt; }
    public void setVerifiedAt(OffsetDateTime verifiedAt) { this.verifiedAt = verifiedAt; }
    public BigDecimal getRaisedAmount() { return raisedAmount; }
    public void setRaisedAmount(BigDecimal raisedAmount) { this.raisedAmount = raisedAmount; }
    public Integer getDonorCount() { return donorCount; }
    public void setDonorCount(Integer donorCount) { this.donorCount = donorCount; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
