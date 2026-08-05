package zw.gov.mohcc.impilo.orgregistry.core;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.orgregistry.persistence.entity.ServiceAgreementEntity;
import zw.gov.mohcc.impilo.orgregistry.persistence.entity.ServiceResponsibilityProfileEntity;
import zw.gov.mohcc.impilo.orgregistry.persistence.entity.TrustDomainEntity;
import zw.gov.mohcc.impilo.orgregistry.persistence.repository.ServiceAgreementRepository;
import zw.gov.mohcc.impilo.orgregistry.persistence.repository.ServiceResponsibilityProfileRepository;
import zw.gov.mohcc.impilo.orgregistry.persistence.repository.TrustDomainRepository;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Lifecycle governance for trust domains, responsibility profiles and service agreements
 * (v1.3.8 §3.3, §3A.1, §Scope C).
 *
 * <p>The rule that shapes every method here: <strong>an active versioned record is not
 * edited</strong>. Change produces a successor version carrying {@code supersedes}, an
 * effective date, the actor and a reason, and the predecessor is marked SUPERSEDED. History
 * is never deleted, because a governance record whose past can be rewritten cannot answer
 * "who was responsible on the day this happened".
 *
 * <p>Note what is <em>not</em> required: a resolved controller is not needed to create or
 * validate a draft. Refusal is operation-specific. Blocking draft creation on an undecided
 * legal question would make the undecided question unrecordable, which is exactly backwards.
 */
@Service
public class ResponsibilityGovernanceService {

    private final TrustDomainRepository trustDomains;
    private final ServiceResponsibilityProfileRepository profiles;
    private final ServiceAgreementRepository agreements;
    private final GovernanceAuditService audit;

    public ResponsibilityGovernanceService(TrustDomainRepository trustDomains,
                                           ServiceResponsibilityProfileRepository profiles,
                                           ServiceAgreementRepository agreements,
                                           GovernanceAuditService audit) {
        this.trustDomains = trustDomains;
        this.profiles = profiles;
        this.agreements = agreements;
        this.audit = audit;
    }

    /** The actor performing a governance act, derived server-side and never from the client. */
    public record GovernanceActor(String actorId, String authority, String scopeType, UUID scopeId,
                                  String correlationId, String requestId) {}

    // ── Trust domains ───────────────────────────────────────────────────────

    @Transactional
    public TrustDomainEntity createTrustDomain(TrustDomainEntity request, GovernanceActor actor) {
        if (trustDomains.existsByCode(request.getCode())) {
            refuse("TRUST_DOMAIN_CODE_IN_USE",
                    "A trust domain with code " + request.getCode() + " already exists",
                    "trust_domain:" + request.getCode(),
                    "Choose a distinct code, or amend the existing domain",
                    actor, "CREATE_TRUST_DOMAIN", "TRUST_DOMAIN", null);
        }
        request.setTrustDomainId(UUID.randomUUID());
        request.setCreatedByActor(actor.actorId());
        request.setAccreditationStatus("PROVISIONAL");
        // A new domain never arrives with a determined controller. Declaring one is a
        // separate, evidenced governance act.
        request.setControllerDeclarationState("UNDETERMINED");
        request.setDataControllerLegalName(null);
        TrustDomainEntity saved = trustDomains.saveAndFlush(request);

        audit.recordOrFail(new GovernanceAuditService.Entry("CREATE_TRUST_DOMAIN", "TRUST_DOMAIN", "PERMITTED")
                .actor(actor.actorId(), actor.authority(), actor.scopeType(), actor.scopeId())
                .target(saved.getTrustDomainId())
                .trustDomain(saved.getTrustDomainId())
                .versions(null, saved.getVersion())
                .reason("CREATED", "Trust domain created in PROVISIONAL with controller UNDETERMINED")
                .correlation(actor.correlationId(), actor.requestId()));
        return saved;
    }

    @Transactional
    public TrustDomainEntity accreditTrustDomain(UUID trustDomainId, GovernanceActor actor) {
        TrustDomainEntity domain = trustDomains.findById(trustDomainId)
                .orElseThrow(() -> new GovernanceRefusal("TRUST_DOMAIN_NOT_FOUND",
                        "No such trust domain", "trust_domain:" + trustDomainId,
                        "Create the trust domain first"));
        // §3.3: PROVISIONAL -> ACCREDITED, or reinstatement from SUSPENDED.
        if (!List.of("PROVISIONAL", "SUSPENDED").contains(domain.getAccreditationStatus())) {
            refuse("INVALID_LIFECYCLE_TRANSITION",
                    "Cannot accredit a trust domain in state " + domain.getAccreditationStatus(),
                    "trust_domain:" + domain.getCode(),
                    "Only PROVISIONAL or SUSPENDED domains may be accredited",
                    actor, "ACCREDIT_TRUST_DOMAIN", "TRUST_DOMAIN", trustDomainId);
        }
        Integer previous = domain.getVersion();
        domain.setAccreditationStatus("ACCREDITED");
        domain.setAccreditedAt(OffsetDateTime.now());
        domain.setUpdatedAt(OffsetDateTime.now());
        TrustDomainEntity saved = trustDomains.saveAndFlush(domain);

        audit.recordOrFail(new GovernanceAuditService.Entry("ACCREDIT_TRUST_DOMAIN", "TRUST_DOMAIN", "PERMITTED")
                .actor(actor.actorId(), actor.authority(), actor.scopeType(), actor.scopeId())
                .target(trustDomainId).trustDomain(trustDomainId)
                .versions(previous, saved.getVersion())
                .reason("ACCREDITED", "Domain accredited; this grants no clinical access")
                .correlation(actor.correlationId(), actor.requestId()));
        return saved;
    }

    // ── Responsibility profiles ─────────────────────────────────────────────

    /**
     * Create a profile draft. Permitted with the controller undetermined — recording that a
     * question is open is precisely what this model is for.
     */
    @Transactional
    public ServiceResponsibilityProfileEntity createProfileDraft(
            ServiceResponsibilityProfileEntity request, GovernanceActor actor) {
        request.setResponsibilityProfileId(UUID.randomUUID());
        request.setStatus("DRAFT");
        request.setVersion(1);
        request.setCreatedByActor(actor.actorId());
        if (request.getDataControllerId() == null) {
            request.setControllerResolutionState("UNDETERMINED");
        }
        ServiceResponsibilityProfileEntity saved = profiles.saveAndFlush(request);

        audit.recordOrFail(new GovernanceAuditService.Entry("CREATE_PROFILE_DRAFT", "RESPONSIBILITY_PROFILE", "PERMITTED")
                .actor(actor.actorId(), actor.authority(), actor.scopeType(), actor.scopeId())
                .target(saved.getResponsibilityProfileId())
                .versions(null, saved.getVersion())
                .reason("DRAFT_CREATED", "Controller state: " + saved.getControllerResolutionState())
                .correlation(actor.correlationId(), actor.requestId()));
        return saved;
    }

    /** Validation findings; an empty list means the profile may be activated. */
    public List<String> validateProfile(ServiceResponsibilityProfileEntity profile) {
        List<String> findings = new ArrayList<>();
        if (profile.getHostingModel() == null) {
            findings.add("hosting_model is required");
        }
        if (profile.getClinicalGovernanceAuthorityId() == null) {
            // Clinical governance IS settled per consumption profile (§4A.0); unlike the
            // controller, leaving it blank is an omission rather than an open question.
            findings.add("clinical_governance_authority_id is required and is not an open [L] matter");
        }
        if (profile.getDataProcessorId() == null) {
            findings.add("data_processor_id is required");
        }
        if ("RESOLVED".equals(profile.getControllerResolutionState())
                && profile.getDataControllerId() == null) {
            findings.add("controller_resolution_state=RESOLVED requires data_controller_id");
        }
        return findings;
    }

    /**
     * Activate a profile. This is the point at which it becomes authority-bearing, so it is
     * also the point at which incomplete responsibility must stop it — but only for the
     * fields that are genuinely required, never for the open [L] controller question.
     */
    @Transactional
    public ServiceResponsibilityProfileEntity activateProfile(UUID profileId, GovernanceActor actor) {
        ServiceResponsibilityProfileEntity profile = profiles.findById(profileId)
                .orElseThrow(() -> new GovernanceRefusal("PROFILE_NOT_FOUND", "No such profile",
                        "responsibility_profile:" + profileId, "Create the profile first"));
        if (!profile.isMutable()) {
            refuse("PROFILE_IMMUTABLE",
                    "Profile is " + profile.getStatus() + " and cannot be activated again",
                    "responsibility_profile:" + profile.getProfileCode(),
                    "Create a successor version instead of editing an active profile",
                    actor, "ACTIVATE_PROFILE", "RESPONSIBILITY_PROFILE", profileId);
        }
        List<String> findings = validateProfile(profile);
        if (!findings.isEmpty()) {
            refuse("RESPONSIBILITY_PROFILE_INCOMPLETE",
                    "Profile cannot be activated: " + String.join("; ", findings),
                    "responsibility_profile:" + profile.getProfileCode(),
                    "Complete the listed responsibility fields, then activate",
                    actor, "ACTIVATE_PROFILE", "RESPONSIBILITY_PROFILE", profileId);
        }
        Integer previous = profile.getVersion();
        profile.setStatus("ACTIVE");
        profile.setEffectiveFrom(OffsetDateTime.now());
        profile.setApprovedByActor(actor.actorId());
        profile.setApprovedAt(OffsetDateTime.now());
        profile.setUpdatedAt(OffsetDateTime.now());
        ServiceResponsibilityProfileEntity saved = profiles.saveAndFlush(profile);

        audit.recordOrFail(new GovernanceAuditService.Entry("ACTIVATE_PROFILE", "RESPONSIBILITY_PROFILE", "PERMITTED")
                .actor(actor.actorId(), actor.authority(), actor.scopeType(), actor.scopeId())
                .target(profileId).versions(previous, saved.getVersion())
                .reason("ACTIVATED", "Controller state at activation: " + saved.getControllerResolutionState())
                .correlation(actor.correlationId(), actor.requestId()));
        return saved;
    }

    /**
     * Supersede an active profile with a successor version. The predecessor is retained and
     * marked SUPERSEDED — never edited, never removed.
     */
    @Transactional
    public ServiceResponsibilityProfileEntity supersedeProfile(
            UUID currentProfileId, ServiceResponsibilityProfileEntity successor,
            String reason, GovernanceActor actor) {
        ServiceResponsibilityProfileEntity current = profiles.findById(currentProfileId)
                .orElseThrow(() -> new GovernanceRefusal("PROFILE_NOT_FOUND", "No such profile",
                        "responsibility_profile:" + currentProfileId, "Create the profile first"));
        if (!"ACTIVE".equals(current.getStatus())) {
            refuse("INVALID_LIFECYCLE_TRANSITION",
                    "Only an ACTIVE profile may be superseded; this one is " + current.getStatus(),
                    "responsibility_profile:" + current.getProfileCode(),
                    "Activate the profile first, or amend the draft directly",
                    actor, "SUPERSEDE_PROFILE", "RESPONSIBILITY_PROFILE", currentProfileId);
        }
        if (reason == null || reason.isBlank()) {
            refuse("SUPERSESSION_REASON_REQUIRED",
                    "A supersession must state why",
                    "responsibility_profile:" + current.getProfileCode(),
                    "Supply a reason describing what changed and under what authority",
                    actor, "SUPERSEDE_PROFILE", "RESPONSIBILITY_PROFILE", currentProfileId);
        }

        successor.setResponsibilityProfileId(UUID.randomUUID());
        successor.setProfileCode(current.getProfileCode());
        successor.setVersion(current.getVersion() + 1);
        successor.setSupersedesProfileId(current.getResponsibilityProfileId());
        successor.setSupersessionReason(reason);
        successor.setStatus("DRAFT");
        successor.setCreatedByActor(actor.actorId());
        ServiceResponsibilityProfileEntity saved = profiles.saveAndFlush(successor);

        current.setStatus("SUPERSEDED");
        current.setEffectiveTo(OffsetDateTime.now());
        current.setUpdatedAt(OffsetDateTime.now());
        profiles.saveAndFlush(current);

        audit.recordOrFail(new GovernanceAuditService.Entry("SUPERSEDE_PROFILE", "RESPONSIBILITY_PROFILE", "PERMITTED")
                .actor(actor.actorId(), actor.authority(), actor.scopeType(), actor.scopeId())
                .target(saved.getResponsibilityProfileId())
                .versions(current.getVersion(), saved.getVersion())
                .reason("SUPERSEDED", reason)
                .correlation(actor.correlationId(), actor.requestId()));
        return saved;
    }

    // ── Service agreements ──────────────────────────────────────────────────

    @Transactional
    public ServiceAgreementEntity createAgreementDraft(ServiceAgreementEntity request,
                                                       GovernanceActor actor) {
        request.setServiceAgreementId(UUID.randomUUID());
        request.setStatus("DRAFT");
        request.setVersion(1);
        request.setCreatedByActor(actor.actorId());
        ServiceAgreementEntity saved = agreements.saveAndFlush(request);

        audit.recordOrFail(new GovernanceAuditService.Entry("CREATE_AGREEMENT_DRAFT", "SERVICE_AGREEMENT", "PERMITTED")
                .actor(actor.actorId(), actor.authority(), actor.scopeType(), actor.scopeId())
                .target(saved.getServiceAgreementId())
                .trustDomain(saved.getTrustDomainId()).organisation(saved.getOrganisationId())
                .versions(null, saved.getVersion())
                .reason("DRAFT_CREATED", null)
                .correlation(actor.correlationId(), actor.requestId()));
        return saved;
    }

    /**
     * Activate an agreement. The database exclusion constraint is the real guard against a
     * second contradictory active agreement; this check exists to turn that into a governed
     * refusal with a reason rather than a raw constraint violation.
     */
    @Transactional
    public ServiceAgreementEntity activateAgreement(UUID agreementId, GovernanceActor actor) {
        ServiceAgreementEntity agreement = agreements.findById(agreementId)
                .orElseThrow(() -> new GovernanceRefusal("AGREEMENT_NOT_FOUND", "No such agreement",
                        "service_agreement:" + agreementId, "Create the agreement first"));
        if (!agreement.isMutable() && !"PENDING_SIGNATURE".equals(agreement.getStatus())) {
            refuse("AGREEMENT_IMMUTABLE",
                    "Agreement is " + agreement.getStatus() + " and cannot be activated",
                    "service_agreement:" + agreementId,
                    "Create a successor agreement instead of editing an active one",
                    actor, "ACTIVATE_AGREEMENT", "SERVICE_AGREEMENT", agreementId);
        }
        ServiceResponsibilityProfileEntity profile =
                profiles.findById(agreement.getResponsibilityProfileId())
                        .orElseThrow(() -> new GovernanceRefusal("PROFILE_NOT_FOUND",
                                "Agreement references a profile that does not exist",
                                "service_agreement:" + agreementId,
                                "Point the agreement at an active responsibility profile"));
        if (!"ACTIVE".equals(profile.getStatus())) {
            refuse("RESPONSIBILITY_PROFILE_INCOMPLETE",
                    "An agreement may only activate against an ACTIVE responsibility profile; "
                            + "profile " + profile.getProfileCode() + " is " + profile.getStatus(),
                    "service_agreement:" + agreementId,
                    "Activate the responsibility profile first",
                    actor, "ACTIVATE_AGREEMENT", "SERVICE_AGREEMENT", agreementId);
        }
        agreements.findFirstByTrustDomainIdAndOrganisationIdAndStatus(
                agreement.getTrustDomainId(), agreement.getOrganisationId(), "ACTIVE")
                .ifPresent(existing -> refuse("CONFLICTING_ACTIVE_AGREEMENT",
                        "Organisation already has an active agreement in this trust domain",
                        "service_agreement:" + existing.getServiceAgreementId(),
                        "Supersede or terminate the existing agreement before activating another",
                        actor, "ACTIVATE_AGREEMENT", "SERVICE_AGREEMENT", agreementId));

        Integer previous = agreement.getVersion();
        agreement.setStatus("ACTIVE");
        agreement.setUpdatedAt(OffsetDateTime.now());
        ServiceAgreementEntity saved = agreements.saveAndFlush(agreement);

        audit.recordOrFail(new GovernanceAuditService.Entry("ACTIVATE_AGREEMENT", "SERVICE_AGREEMENT", "PERMITTED")
                .actor(actor.actorId(), actor.authority(), actor.scopeType(), actor.scopeId())
                .target(agreementId).trustDomain(saved.getTrustDomainId())
                .organisation(saved.getOrganisationId())
                .versions(previous, saved.getVersion())
                .reason("ACTIVATED", "Activated against profile " + profile.getProfileCode())
                .correlation(actor.correlationId(), actor.requestId()));
        return saved;
    }

    /** Audit the refusal, then throw it. Both, always, in that order. */
    private void refuse(String code, String message, String scope, String action,
                        GovernanceActor actor, String operation, String targetType, UUID targetId) {
        audit.recordRefusal(new GovernanceAuditService.Entry(operation, targetType, "REFUSED")
                .actor(actor.actorId(), actor.authority(), actor.scopeType(), actor.scopeId())
                .target(targetId)
                .reason(code, message)
                .correlation(actor.correlationId(), actor.requestId()));
        throw new GovernanceRefusal(code, message, scope, action);
    }
}
