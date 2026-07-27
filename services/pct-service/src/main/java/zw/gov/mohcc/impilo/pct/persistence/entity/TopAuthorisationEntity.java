package zw.gov.mohcc.impilo.pct.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * A lawful-termination authorisation. Authorisation (the state's decision on the grounds) is
 * recorded apart from consent (hers). An AUTHORISED row is the only kind a procedure may reference,
 * and it cannot leave that state while a procedure does — enforced by the composite FK in V435, not
 * by this entity.
 */
@Entity
@Table(name = "pct_top_authorisations")
public class TopAuthorisationEntity {

    public static final String STATUS_REQUESTED = "REQUESTED";
    public static final String STATUS_AUTHORISED = "AUTHORISED";
    public static final String STATUS_DECLINED = "DECLINED";
    public static final String STATUS_REFERRED_OBJECTION = "REFERRED_OBJECTION";
    public static final String STATUS_EXPIRED = "EXPIRED";

    @Id @Column(name = "authorisation_id") private UUID authorisationId;
    @Column(name = "tenant_id") private UUID tenantId;
    @Column(name = "subject_cpid") private String subjectCpid;
    @Column(name = "pregnancy_episode_id") private UUID pregnancyEpisodeId;
    @Column(name = "status") private String status;
    @Column(name = "legal_ground") private String legalGround;
    @Column(name = "legal_ground_detail") private String legalGroundDetail;
    @Column(name = "gestational_limit_weeks") private BigDecimal gestationalLimitWeeks;
    @Column(name = "gestation_weeks_at_request") private BigDecimal gestationWeeksAtRequest;
    @Column(name = "certifying_authority") private String certifyingAuthority;
    @Column(name = "certified_on") private LocalDate certifiedOn;
    @Column(name = "consent_given") private String consentGiven;
    @Column(name = "consent_recorded_on") private LocalDate consentRecordedOn;
    @Column(name = "objection_referral_ref") private String objectionReferralRef;
    @Column(name = "confidentiality_category") private String confidentialityCategory;
    @Column(name = "recorded_by") private String recordedBy;
    @Column(name = "recorded_at") private OffsetDateTime recordedAt;

    public UUID getAuthorisationId() { return authorisationId; }
    public void setAuthorisationId(UUID v) { this.authorisationId = v; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID v) { this.tenantId = v; }
    public String getSubjectCpid() { return subjectCpid; }
    public void setSubjectCpid(String v) { this.subjectCpid = v; }
    public UUID getPregnancyEpisodeId() { return pregnancyEpisodeId; }
    public void setPregnancyEpisodeId(UUID v) { this.pregnancyEpisodeId = v; }
    public String getStatus() { return status; }
    public void setStatus(String v) { this.status = v; }
    public String getLegalGround() { return legalGround; }
    public void setLegalGround(String v) { this.legalGround = v; }
    public String getLegalGroundDetail() { return legalGroundDetail; }
    public void setLegalGroundDetail(String v) { this.legalGroundDetail = v; }
    public BigDecimal getGestationalLimitWeeks() { return gestationalLimitWeeks; }
    public void setGestationalLimitWeeks(BigDecimal v) { this.gestationalLimitWeeks = v; }
    public BigDecimal getGestationWeeksAtRequest() { return gestationWeeksAtRequest; }
    public void setGestationWeeksAtRequest(BigDecimal v) { this.gestationWeeksAtRequest = v; }
    public String getCertifyingAuthority() { return certifyingAuthority; }
    public void setCertifyingAuthority(String v) { this.certifyingAuthority = v; }
    public LocalDate getCertifiedOn() { return certifiedOn; }
    public void setCertifiedOn(LocalDate v) { this.certifiedOn = v; }
    public String getConsentGiven() { return consentGiven; }
    public void setConsentGiven(String v) { this.consentGiven = v; }
    public LocalDate getConsentRecordedOn() { return consentRecordedOn; }
    public void setConsentRecordedOn(LocalDate v) { this.consentRecordedOn = v; }
    public String getObjectionReferralRef() { return objectionReferralRef; }
    public void setObjectionReferralRef(String v) { this.objectionReferralRef = v; }
    public String getConfidentialityCategory() { return confidentialityCategory; }
    public void setConfidentialityCategory(String v) { this.confidentialityCategory = v; }
    public String getRecordedBy() { return recordedBy; }
    public void setRecordedBy(String v) { this.recordedBy = v; }
    public OffsetDateTime getRecordedAt() { return recordedAt; }
    public void setRecordedAt(OffsetDateTime v) { this.recordedAt = v; }
}
