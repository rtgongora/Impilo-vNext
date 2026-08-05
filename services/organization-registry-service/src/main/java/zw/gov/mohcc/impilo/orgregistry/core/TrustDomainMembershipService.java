package zw.gov.mohcc.impilo.orgregistry.core;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.orgregistry.persistence.entity.TrustDomainMembershipEntity;
import zw.gov.mohcc.impilo.orgregistry.persistence.repository.TrustDomainMembershipRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Trust-domain membership (ADR-0055 decisions 2, 3, 4 and 6).
 *
 * <p>Membership becomes ACTIVE on evidence and nothing else. There is deliberately no
 * method here that derives a trust domain from an organisation's type, name, host,
 * operator, owner, tenant default or regulator status — the absence is the control.
 * v1.3.8's blanket backfill would have recorded every organisation as MoHCC, including
 * the private-provider groups and local authorities that plainly are not.
 *
 * <p>{@code UNMAPPED} is a state, not a blank. An operation needing an active trust
 * domain is refused with a code naming which of the four non-active conditions applies
 * and what would clear it. Draft and review work is never refused: making an open
 * question unrecordable is how it stays open forever.
 */
@Service
public class TrustDomainMembershipService {

    /** Evidence classes that may make a membership ACTIVE (ADR-0055 d4). */
    private static final List<String> RECOGNISED_SOURCES = List.of(
            "MOHCC_REGISTRY", "GOVERNED_HIERARCHY", "ACCREDITATION_DECISION", "ONBOARDING_DECISION");

    private final TrustDomainMembershipRepository memberships;
    private final GovernanceAuditService audit;

    public TrustDomainMembershipService(TrustDomainMembershipRepository memberships,
                                        GovernanceAuditService audit) {
        this.memberships = memberships;
        this.audit = audit;
    }

    /**
     * The membership state of a subject, as a first-class answer. {@code UNMAPPED} when
     * no record exists at all — absence is never silently read as "belongs to MoHCC".
     */
    public record MembershipState(String status, UUID trustDomainId, UUID membershipId,
                                  String sourceAuthority, OffsetDateTime effectiveFrom) {

        public static MembershipState unmapped() {
            return new MembershipState("UNMAPPED", null, null, null, null);
        }

        public boolean isActive() {
            return "ACTIVE".equals(status);
        }
    }

    public MembershipState stateOf(String subjectType, UUID subjectId, OffsetDateTime at) {
        Optional<TrustDomainMembershipEntity> active =
                memberships.findActiveFor(subjectType, subjectId, at);
        if (active.isPresent()) {
            TrustDomainMembershipEntity m = active.get();
            return new MembershipState("ACTIVE", m.getTrustDomainId(), m.getMembershipId(),
                    m.getSourceAuthority(), m.getEffectiveFrom());
        }
        // Fall back to the most recent record so the caller learns PENDING_REVIEW,
        // SUSPENDED or ENDED rather than a flat "no".
        List<TrustDomainMembershipEntity> all =
                memberships.findBySubjectTypeAndSubjectIdOrderByCreatedAtDesc(subjectType, subjectId);
        if (all.isEmpty()) {
            return MembershipState.unmapped();
        }
        TrustDomainMembershipEntity latest = all.get(0);
        return new MembershipState(latest.getStatus(), null, latest.getMembershipId(),
                latest.getSourceAuthority(), latest.getEffectiveFrom());
    }

    /**
     * Gate an operation that requires an active trust domain (ADR-0055 d6).
     *
     * @throws GovernanceRefusal naming which condition applies and what clears it. The
     *         caller gets no substitute domain, because there isn't one.
     */
    public UUID requireActiveTrustDomain(String subjectType, UUID subjectId, String operation,
                                         ResponsibilityGovernanceService.GovernanceActor actor) {
        MembershipState state = stateOf(subjectType, subjectId, OffsetDateTime.now());
        if (state.isActive()) {
            return state.trustDomainId();
        }
        String code = switch (state.status()) {
            case "PENDING_REVIEW" -> "TRUST_DOMAIN_MEMBERSHIP_PENDING";
            case "SUSPENDED", "ENDED" -> "TRUST_DOMAIN_MEMBERSHIP_INACTIVE";
            default -> "TRUST_DOMAIN_UNMAPPED";
        };
        String action = switch (state.status()) {
            case "PENDING_REVIEW" -> "An authorised reviewer must approve the pending membership";
            case "SUSPENDED" -> "Reinstate the suspended membership, or record a new one with evidence";
            case "ENDED" -> "Record a new membership citing the authority that establishes it";
            default -> "Record a trust-domain membership citing MOHCC_REGISTRY, GOVERNED_HIERARCHY, "
                    + "ACCREDITATION_DECISION or ONBOARDING_DECISION as its source. Membership is "
                    + "never inferred from organisation type, hosting, operator, name or ownership.";
        };
        audit.recordRefusal(new GovernanceAuditService.Entry(operation, subjectType, "REFUSED")
                .actor(actor.actorId(), actor.authority(), actor.scopeType(), actor.scopeId())
                .target(subjectId)
                .reason(code, "Membership status is " + state.status())
                .correlation(actor.correlationId(), actor.requestId()));
        throw new GovernanceRefusal(code,
                "Subject " + subjectId + " has trust-domain membership status " + state.status(),
                subjectType.toLowerCase() + ":" + subjectId, action);
    }

    /** Record a membership for review. Permitted at any state — this is how UNMAPPED gets fixed. */
    @Transactional
    public TrustDomainMembershipEntity proposeMembership(
            TrustDomainMembershipEntity request,
            ResponsibilityGovernanceService.GovernanceActor actor) {
        if (!RECOGNISED_SOURCES.contains(request.getSourceAuthority())) {
            throw new GovernanceRefusal("UNRECOGNISED_SOURCE_AUTHORITY",
                    "Source authority '" + request.getSourceAuthority() + "' is not a recognised "
                            + "evidence class",
                    "trust_domain_membership",
                    "Cite one of: " + String.join(", ", RECOGNISED_SOURCES));
        }
        if (request.getProvenance() == null || request.getProvenance().isBlank()) {
            throw new GovernanceRefusal("MEMBERSHIP_EVIDENCE_REQUIRED",
                    "A membership must record the evidence relied on",
                    "trust_domain_membership",
                    "Supply provenance describing the register entry, decision or instrument");
        }
        request.setMembershipId(UUID.randomUUID());
        request.setStatus("PENDING_REVIEW");
        request.setEffectiveFrom(null);          // only an ACTIVE membership carries a period
        request.setCreatedBy(actor.actorId());
        TrustDomainMembershipEntity saved = memberships.saveAndFlush(request);

        audit.recordOrFail(new GovernanceAuditService.Entry(
                "PROPOSE_TRUST_DOMAIN_MEMBERSHIP", "TRUST_DOMAIN_MEMBERSHIP", "PERMITTED")
                .actor(actor.actorId(), actor.authority(), actor.scopeType(), actor.scopeId())
                .target(saved.getMembershipId())
                .trustDomain(saved.getTrustDomainId())
                .reason("PENDING_REVIEW", "Source authority: " + saved.getSourceAuthority())
                .correlation(actor.correlationId(), actor.requestId()));
        return saved;
    }

    /** Approve a pending membership, making it ACTIVE and dated. */
    @Transactional
    public TrustDomainMembershipEntity approveMembership(
            UUID membershipId, ResponsibilityGovernanceService.GovernanceActor actor) {
        TrustDomainMembershipEntity m = memberships.findById(membershipId)
                .orElseThrow(() -> new GovernanceRefusal("MEMBERSHIP_NOT_FOUND",
                        "No such membership", "trust_domain_membership:" + membershipId,
                        "Propose the membership first"));
        if (!"PENDING_REVIEW".equals(m.getStatus())) {
            throw new GovernanceRefusal("INVALID_LIFECYCLE_TRANSITION",
                    "Only a PENDING_REVIEW membership may be approved; this one is " + m.getStatus(),
                    "trust_domain_membership:" + membershipId,
                    "Propose a new membership instead of re-approving a settled one");
        }
        m.setStatus("ACTIVE");
        m.setEffectiveFrom(OffsetDateTime.now());
        m.setReviewedBy(actor.actorId());
        m.setReviewedAt(OffsetDateTime.now());
        TrustDomainMembershipEntity saved = memberships.saveAndFlush(m);

        audit.recordOrFail(new GovernanceAuditService.Entry(
                "APPROVE_TRUST_DOMAIN_MEMBERSHIP", "TRUST_DOMAIN_MEMBERSHIP", "PERMITTED")
                .actor(actor.actorId(), actor.authority(), actor.scopeType(), actor.scopeId())
                .target(membershipId).trustDomain(saved.getTrustDomainId())
                .reason("ACTIVATED", "Evidence: " + saved.getSourceAuthority()
                        + ". Membership grants no clinical access and settles no controllership.")
                .correlation(actor.correlationId(), actor.requestId()));
        return saved;
    }

    /** Full history for a subject. Nothing is deleted; supersession is how change is recorded. */
    public List<TrustDomainMembershipEntity> historyOf(String subjectType, UUID subjectId) {
        return memberships.findBySubjectTypeAndSubjectIdOrderByCreatedAtDesc(subjectType, subjectId);
    }

    /** The review queue the governance workspace reads. */
    public List<TrustDomainMembershipEntity> pendingReview() {
        return memberships.findByStatus("PENDING_REVIEW");
    }
}
