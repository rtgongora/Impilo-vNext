package zw.gov.mohcc.impilo.orgregistry.core;

import org.springframework.stereotype.Service;
import zw.gov.mohcc.impilo.orgregistry.persistence.entity.ProcessingRoleAssignmentEntity;
import zw.gov.mohcc.impilo.orgregistry.persistence.entity.ServiceResponsibilityProfileEntity;
import zw.gov.mohcc.impilo.orgregistry.persistence.repository.ProcessingRoleAssignmentRepository;
import zw.gov.mohcc.impilo.orgregistry.persistence.repository.ServiceAgreementRepository;
import zw.gov.mohcc.impilo.orgregistry.persistence.repository.ServiceResponsibilityProfileRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Resolves the legal controller for a governed operation (v1.3.8 §3A.4).
 *
 * <p>The resolution order is fixed: the most specific active assignment matching
 * (scope, data domain, purpose) wins; failing that, the parent scope's assignment; failing
 * that, the responsibility profile's declared default; failing that, <strong>the request is
 * refused and flagged for governance</strong>.
 *
 * <p>That last step is the whole point. An unresolvable controller is a data-protection
 * defect, not a value to guess. Nothing here falls back to the ministry, the hosting
 * operator, the organisation owner, the facility, or any operator field — §3A.2 forbids
 * inferring authority from any of them, and §26.2 #1 is still open.
 */
@Service
public class ControllerResolutionService {

    /** Scope types ordered most specific first — the §3A.4 walk. */
    private static final List<String> SCOPE_PRECEDENCE =
            List.of("FACILITY", "NODE", "SERVICE_AGREEMENT", "PROGRAMME", "ORGANISATION", "TRUST_DOMAIN");

    private final ProcessingRoleAssignmentRepository assignments;
    private final ServiceResponsibilityProfileRepository profiles;
    private final ServiceAgreementRepository agreements;

    public ControllerResolutionService(ProcessingRoleAssignmentRepository assignments,
                                       ServiceResponsibilityProfileRepository profiles,
                                       ServiceAgreementRepository agreements) {
        this.assignments = assignments;
        this.profiles = profiles;
        this.agreements = agreements;
    }

    /**
     * The outcome of a controller resolution. Either a controller was determined, or it was
     * not — and when it was not, the caller gets a reason code and the governance action
     * required, never a substitute party.
     */
    public record Resolution(
            boolean resolved,
            UUID controllerPartyId,
            String roleType,
            String resolvedAtScopeType,
            UUID resolvedAtScopeId,
            String instrumentReference,
            String reasonCode,
            String reasonDetail,
            String requiredGovernanceAction,
            List<UUID> jointControllerPartyIds) {

        public static Resolution of(ProcessingRoleAssignmentEntity a) {
            return new Resolution(true, a.getPartyId(), a.getRoleType(), a.getScopeType(),
                    a.getScopeId(), a.getInstrumentReference(), null, null, null, List.of());
        }

        public static Resolution joint(List<ProcessingRoleAssignmentEntity> rows) {
            ProcessingRoleAssignmentEntity first = rows.get(0);
            return new Resolution(true, null, "JOINT_CONTROLLER", first.getScopeType(),
                    first.getScopeId(), first.getInstrumentReference(), null, null, null,
                    rows.stream().map(ProcessingRoleAssignmentEntity::getPartyId).toList());
        }

        public static Resolution undetermined(String detail, String action) {
            return new Resolution(false, null, null, null, null, null,
                    "CONTROLLER_UNDETERMINED", detail, action, List.of());
        }

        public static Resolution profileIncomplete(String detail, String action) {
            return new Resolution(false, null, null, null, null, null,
                    "RESPONSIBILITY_PROFILE_INCOMPLETE", detail, action, List.of());
        }
    }

    /**
     * A governed scope to walk, innermost first. Callers pass what they actually know; a
     * facility-level operation supplies facility, organisation and trust domain, a
     * domain-level one supplies only the trust domain.
     */
    public record ScopeChain(UUID facilityId, UUID nodeId, UUID serviceAgreementId,
                             UUID programmeId, UUID organisationId, UUID trustDomainId) {

        UUID idFor(String scopeType) {
            return switch (scopeType) {
                case "FACILITY" -> facilityId;
                case "NODE" -> nodeId;
                case "SERVICE_AGREEMENT" -> serviceAgreementId;
                case "PROGRAMME" -> programmeId;
                case "ORGANISATION" -> organisationId;
                case "TRUST_DOMAIN" -> trustDomainId;
                default -> null;
            };
        }
    }

    /**
     * Resolve the legal controller for (scope chain, data domain, purpose) at an instant.
     *
     * @return a resolution that is either determined, or explicitly undetermined with the
     *         governance action required. Never a guess.
     */
    public Resolution resolveController(ScopeChain chain, String dataDomain, String purpose,
                                        OffsetDateTime at) {
        for (String scopeType : SCOPE_PRECEDENCE) {
            UUID scopeId = chain.idFor(scopeType);
            if (scopeId == null) {
                continue;
            }
            List<ProcessingRoleAssignmentEntity> sole = assignments.findActiveFor(
                    scopeType, scopeId, dataDomain, purpose, "LEGAL_CONTROLLER", at);
            if (!sole.isEmpty()) {
                // The exclusion constraint guarantees at most one; if the database somehow
                // holds more, that is a defect to surface rather than silently pick from.
                if (sole.size() > 1) {
                    return Resolution.undetermined(
                            "More than one active legal controller at scope " + scopeType
                                    + " for " + dataDomain + "/" + purpose,
                            "Withdraw the contradictory assignment; only one may be active");
                }
                return Resolution.of(sole.get(0));
            }
            List<ProcessingRoleAssignmentEntity> joint = assignments.findActiveFor(
                    scopeType, scopeId, dataDomain, purpose, "JOINT_CONTROLLER", at);
            if (!joint.isEmpty()) {
                return Resolution.joint(joint);
            }
        }

        // Nothing assigned anywhere in the chain. Fall back to the agreement's responsibility
        // profile default — which is itself allowed to be undetermined.
        Optional<ServiceResponsibilityProfileEntity> profile = activeProfileFor(chain);
        if (profile.isEmpty()) {
            return Resolution.profileIncomplete(
                    "No active responsibility profile governs this scope",
                    "Activate a service agreement whose responsibility profile covers this scope");
        }
        ServiceResponsibilityProfileEntity p = profile.get();
        if (p.hasResolvedController()) {
            return new Resolution(true, p.getDataControllerId(), "LEGAL_CONTROLLER",
                    "SERVICE_AGREEMENT", chain.serviceAgreementId(), null, null, null, null, List.of());
        }

        return Resolution.undetermined(
                "No active legal controller assignment for data domain '" + dataDomain
                        + "' and purpose '" + purpose + "' at any scope, and responsibility profile "
                        + p.getProfileCode() + " declares controller state "
                        + p.getControllerResolutionState(),
                "Record a processing_role_assignment naming the legal controller, citing the "
                        + "instrument relied on. This is an open governance determination "
                        + "(architecture §26.2 #1) and must not be defaulted.");
    }

    private Optional<ServiceResponsibilityProfileEntity> activeProfileFor(ScopeChain chain) {
        if (chain.serviceAgreementId() != null) {
            return agreements.findById(chain.serviceAgreementId())
                    .filter(a -> "ACTIVE".equals(a.getStatus()))
                    .flatMap(a -> profiles.findById(a.getResponsibilityProfileId()));
        }
        if (chain.trustDomainId() != null && chain.organisationId() != null) {
            return agreements.findFirstByTrustDomainIdAndOrganisationIdAndStatus(
                            chain.trustDomainId(), chain.organisationId(), "ACTIVE")
                    .flatMap(a -> profiles.findById(a.getResponsibilityProfileId()));
        }
        return Optional.empty();
    }
}
