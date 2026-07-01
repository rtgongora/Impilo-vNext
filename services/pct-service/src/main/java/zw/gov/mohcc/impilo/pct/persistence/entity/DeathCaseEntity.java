package zw.gov.mohcc.impilo.pct.persistence.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Represents a death case record linked to a patient journey.
 * Tracks pronouncement details, UBOMI (civil registry) notification status,
 * and the associated death certificate document reference.
 */
@Entity
@Table(name = "pct_death_cases")
public class DeathCaseEntity {

    @Id
    @Column(name = "case_id", nullable = false)
    private UUID caseId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "facility_id")
    private UUID facilityId;

    @Column(name = "journey_id", nullable = false)
    private String journeyId;

    @Column(name = "patient_cpid")
    private String patientCpid;

    @Column(name = "status", nullable = false)
    private String status = "RECORDED";

    @Column(name = "pronounced_by")
    private String pronouncedBy;

    @Column(name = "pronounced_at")
    private OffsetDateTime pronouncedAt;

    @Column(name = "ubomi_notification_id")
    private String ubomiNotificationId;

    @Column(name = "ubomi_status")
    private String ubomiStatus;

    @Column(name = "cert_doc_id")
    private String certDocId;

    @Column(name = "closed_at")
    private OffsetDateTime closedAt;

    // ── WS#8 death-spine: confirmation / place / context ──
    @Column(name = "death_datetime")
    private OffsetDateTime deathDatetime;

    @Column(name = "place_of_death_context")
    private String placeOfDeathContext;

    @Column(name = "place_of_death_location")
    private String placeOfDeathLocation;

    @Column(name = "resuscitation_attempted")
    private Boolean resuscitationAttempted;

    @Column(name = "present_at_death")
    private String presentAtDeath;

    @Column(name = "confirmed_by")
    private String confirmedBy;

    @Column(name = "confirmed_by_role")
    private String confirmedByRole;

    @Column(name = "confirmed_at")
    private OffsetDateTime confirmedAt;

    @Column(name = "deceased_identity_status")
    private String deceasedIdentityStatus = "KNOWN";

    @Column(name = "temporary_identity_ref")
    private String temporaryIdentityRef;

    // ── medico-legal screening ──
    @Column(name = "coroner_referral_required")
    private boolean coronerReferralRequired = false;

    @Column(name = "medico_legal_triggers")
    private String medicoLegalTriggers;

    @Column(name = "coroner_referral_status")
    private String coronerReferralStatus;

    // ── public-health screening ──
    @Column(name = "public_health_flags")
    private String publicHealthFlags;

    @Column(name = "death_review_required")
    private boolean deathReviewRequired = false;

    @Column(name = "rito_review_ref")
    private String ritoReviewRef;

    // ── WHO cause-of-death certification draft ──
    @Column(name = "cod_immediate")
    private String codImmediate;

    @Column(name = "cod_antecedent")
    private String codAntecedent;

    @Column(name = "cod_underlying")
    private String codUnderlying;

    @Column(name = "cod_contributory")
    private String codContributory;

    @Column(name = "cod_manner")
    private String codManner;

    @Column(name = "cod_external_cause")
    private String codExternalCause;

    @Column(name = "certifier_id")
    private String certifierId;

    @Column(name = "certifier_role")
    private String certifierRole;

    @Column(name = "certifier_licence")
    private String certifierLicence;

    @Column(name = "certification_status")
    private String certificationStatus = "NOT_STARTED";

    @Column(name = "certified_at")
    private OffsetDateTime certifiedAt;

    @Column(name = "cert_warnings")
    private String certWarnings;

    @Column(name = "cert_composition_ref")
    private String certCompositionRef;

    // ── body / mortuary release gating ──
    @Column(name = "body_release_blocked")
    private boolean bodyReleaseBlocked = false;

    // ── CRVS handoff ──
    @Column(name = "civil_registration_status")
    private String civilRegistrationStatus = "NOT_STARTED";

    // ── source seam provenance ──
    @Column(name = "source_context")
    private String sourceContext;

    @Column(name = "source_ref")
    private String sourceRef;

    // ── community/field pathway refinement (V027) ──
    /** Where the body actually goes; a case no longer requires facility mortuary custody. */
    @Column(name = "body_disposition_context")
    private String bodyDispositionContext;

    /** Certainty class of the assigned cause — keeps VA-probable distinct from medically certified. */
    @Column(name = "cause_of_death_basis")
    private String causeOfDeathBasis;

    // Unverified community-report provenance (distinct from a provider confirmation).
    @Column(name = "reporter_name")
    private String reporterName;

    @Column(name = "reporter_role")
    private String reporterRole;

    @Column(name = "reporter_contact")
    private String reporterContact;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
    }

    // Getters and setters

    public UUID getId() { return caseId; }
    public void setId(UUID id) { this.caseId = id; }

    public UUID getCaseId() { return caseId; }
    public void setCaseId(UUID caseId) { this.caseId = caseId; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public UUID getFacilityId() { return facilityId; }
    public void setFacilityId(UUID facilityId) { this.facilityId = facilityId; }

    public String getJourneyId() { return journeyId; }
    public void setJourneyId(String journeyId) { this.journeyId = journeyId; }

    public String getPatientCpid() { return patientCpid; }
    public void setPatientCpid(String patientCpid) { this.patientCpid = patientCpid; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getPronouncedBy() { return pronouncedBy; }
    public void setPronouncedBy(String pronouncedBy) { this.pronouncedBy = pronouncedBy; }

    public OffsetDateTime getPronouncedAt() { return pronouncedAt; }
    public void setPronouncedAt(OffsetDateTime pronouncedAt) { this.pronouncedAt = pronouncedAt; }

    public String getUbomiNotificationId() { return ubomiNotificationId; }
    public void setUbomiNotificationId(String ubomiNotificationId) { this.ubomiNotificationId = ubomiNotificationId; }

    public String getUbomiStatus() { return ubomiStatus; }
    public void setUbomiStatus(String ubomiStatus) { this.ubomiStatus = ubomiStatus; }

    public String getCertDocId() { return certDocId; }
    public void setCertDocId(String certDocId) { this.certDocId = certDocId; }

    public OffsetDateTime getClosedAt() { return closedAt; }
    public void setClosedAt(OffsetDateTime closedAt) { this.closedAt = closedAt; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    public OffsetDateTime getDeathDatetime() { return deathDatetime; }
    public void setDeathDatetime(OffsetDateTime deathDatetime) { this.deathDatetime = deathDatetime; }

    public String getPlaceOfDeathContext() { return placeOfDeathContext; }
    public void setPlaceOfDeathContext(String placeOfDeathContext) { this.placeOfDeathContext = placeOfDeathContext; }

    public String getPlaceOfDeathLocation() { return placeOfDeathLocation; }
    public void setPlaceOfDeathLocation(String placeOfDeathLocation) { this.placeOfDeathLocation = placeOfDeathLocation; }

    public Boolean getResuscitationAttempted() { return resuscitationAttempted; }
    public void setResuscitationAttempted(Boolean resuscitationAttempted) { this.resuscitationAttempted = resuscitationAttempted; }

    public String getPresentAtDeath() { return presentAtDeath; }
    public void setPresentAtDeath(String presentAtDeath) { this.presentAtDeath = presentAtDeath; }

    public String getConfirmedBy() { return confirmedBy; }
    public void setConfirmedBy(String confirmedBy) { this.confirmedBy = confirmedBy; }

    public String getConfirmedByRole() { return confirmedByRole; }
    public void setConfirmedByRole(String confirmedByRole) { this.confirmedByRole = confirmedByRole; }

    public OffsetDateTime getConfirmedAt() { return confirmedAt; }
    public void setConfirmedAt(OffsetDateTime confirmedAt) { this.confirmedAt = confirmedAt; }

    public String getDeceasedIdentityStatus() { return deceasedIdentityStatus; }
    public void setDeceasedIdentityStatus(String deceasedIdentityStatus) { this.deceasedIdentityStatus = deceasedIdentityStatus; }

    public String getTemporaryIdentityRef() { return temporaryIdentityRef; }
    public void setTemporaryIdentityRef(String temporaryIdentityRef) { this.temporaryIdentityRef = temporaryIdentityRef; }

    public boolean isCoronerReferralRequired() { return coronerReferralRequired; }
    public void setCoronerReferralRequired(boolean coronerReferralRequired) { this.coronerReferralRequired = coronerReferralRequired; }

    public String getMedicoLegalTriggers() { return medicoLegalTriggers; }
    public void setMedicoLegalTriggers(String medicoLegalTriggers) { this.medicoLegalTriggers = medicoLegalTriggers; }

    public String getCoronerReferralStatus() { return coronerReferralStatus; }
    public void setCoronerReferralStatus(String coronerReferralStatus) { this.coronerReferralStatus = coronerReferralStatus; }

    public String getPublicHealthFlags() { return publicHealthFlags; }
    public void setPublicHealthFlags(String publicHealthFlags) { this.publicHealthFlags = publicHealthFlags; }

    public boolean isDeathReviewRequired() { return deathReviewRequired; }
    public void setDeathReviewRequired(boolean deathReviewRequired) { this.deathReviewRequired = deathReviewRequired; }

    public String getRitoReviewRef() { return ritoReviewRef; }
    public void setRitoReviewRef(String ritoReviewRef) { this.ritoReviewRef = ritoReviewRef; }

    public String getCodImmediate() { return codImmediate; }
    public void setCodImmediate(String codImmediate) { this.codImmediate = codImmediate; }

    public String getCodAntecedent() { return codAntecedent; }
    public void setCodAntecedent(String codAntecedent) { this.codAntecedent = codAntecedent; }

    public String getCodUnderlying() { return codUnderlying; }
    public void setCodUnderlying(String codUnderlying) { this.codUnderlying = codUnderlying; }

    public String getCodContributory() { return codContributory; }
    public void setCodContributory(String codContributory) { this.codContributory = codContributory; }

    public String getCodManner() { return codManner; }
    public void setCodManner(String codManner) { this.codManner = codManner; }

    public String getCodExternalCause() { return codExternalCause; }
    public void setCodExternalCause(String codExternalCause) { this.codExternalCause = codExternalCause; }

    public String getCertifierId() { return certifierId; }
    public void setCertifierId(String certifierId) { this.certifierId = certifierId; }

    public String getCertifierRole() { return certifierRole; }
    public void setCertifierRole(String certifierRole) { this.certifierRole = certifierRole; }

    public String getCertifierLicence() { return certifierLicence; }
    public void setCertifierLicence(String certifierLicence) { this.certifierLicence = certifierLicence; }

    public String getCertificationStatus() { return certificationStatus; }
    public void setCertificationStatus(String certificationStatus) { this.certificationStatus = certificationStatus; }

    public OffsetDateTime getCertifiedAt() { return certifiedAt; }
    public void setCertifiedAt(OffsetDateTime certifiedAt) { this.certifiedAt = certifiedAt; }

    public String getCertWarnings() { return certWarnings; }
    public void setCertWarnings(String certWarnings) { this.certWarnings = certWarnings; }

    public String getCertCompositionRef() { return certCompositionRef; }
    public void setCertCompositionRef(String certCompositionRef) { this.certCompositionRef = certCompositionRef; }

    public boolean isBodyReleaseBlocked() { return bodyReleaseBlocked; }
    public void setBodyReleaseBlocked(boolean bodyReleaseBlocked) { this.bodyReleaseBlocked = bodyReleaseBlocked; }

    public String getCivilRegistrationStatus() { return civilRegistrationStatus; }
    public void setCivilRegistrationStatus(String civilRegistrationStatus) { this.civilRegistrationStatus = civilRegistrationStatus; }

    public String getSourceContext() { return sourceContext; }
    public void setSourceContext(String sourceContext) { this.sourceContext = sourceContext; }

    public String getSourceRef() { return sourceRef; }
    public void setSourceRef(String sourceRef) { this.sourceRef = sourceRef; }

    public String getBodyDispositionContext() { return bodyDispositionContext; }
    public void setBodyDispositionContext(String bodyDispositionContext) { this.bodyDispositionContext = bodyDispositionContext; }

    public String getCauseOfDeathBasis() { return causeOfDeathBasis; }
    public void setCauseOfDeathBasis(String causeOfDeathBasis) { this.causeOfDeathBasis = causeOfDeathBasis; }

    public String getReporterName() { return reporterName; }
    public void setReporterName(String reporterName) { this.reporterName = reporterName; }

    public String getReporterRole() { return reporterRole; }
    public void setReporterRole(String reporterRole) { this.reporterRole = reporterRole; }

    public String getReporterContact() { return reporterContact; }
    public void setReporterContact(String reporterContact) { this.reporterContact = reporterContact; }

}
