package zw.gov.mohcc.impilo.orgregistry.api;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.orgregistry.core.ControllerResolutionService;
import zw.gov.mohcc.impilo.orgregistry.core.GovernanceRefusal;
import zw.gov.mohcc.impilo.orgregistry.core.ResponsibilityGovernanceService;
import zw.gov.mohcc.impilo.orgregistry.core.ResponsibilityGovernanceService.GovernanceActor;
import zw.gov.mohcc.impilo.orgregistry.persistence.entity.*;
import zw.gov.mohcc.impilo.orgregistry.persistence.repository.*;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Trust-domain and responsibility governance API (v1.3.8 §Scope E).
 *
 * <p>Every actor and every authority scope is taken from the server-derived trust context.
 * Nothing on the request body or query string is trusted as authority — §12.4 eliminated
 * that class of assertion, and a governance API is the last place to reintroduce it.
 *
 * <p>Refusals return the real condition: {@code 409} for a lifecycle conflict, {@code 422}
 * for an incomplete or undetermined responsibility, {@code 404} for a missing record. There
 * is no success envelope wrapping a failure.
 */
@RestController
@RequestMapping("/v1/governance")
public class TrustDomainGovernanceController {

    private final ResponsibilityGovernanceService governance;
    private final ControllerResolutionService resolution;
    private final TrustDomainRepository trustDomains;
    private final TrustDomainRelationshipRepository relationships;
    private final ServiceResponsibilityProfileRepository profiles;
    private final ServiceAgreementRepository agreements;
    private final ResponsibilityAuditEventRepository auditEvents;

    public TrustDomainGovernanceController(ResponsibilityGovernanceService governance,
                                           ControllerResolutionService resolution,
                                           TrustDomainRepository trustDomains,
                                           TrustDomainRelationshipRepository relationships,
                                           ServiceResponsibilityProfileRepository profiles,
                                           ServiceAgreementRepository agreements,
                                           ResponsibilityAuditEventRepository auditEvents) {
        this.governance = governance;
        this.resolution = resolution;
        this.trustDomains = trustDomains;
        this.relationships = relationships;
        this.profiles = profiles;
        this.agreements = agreements;
        this.auditEvents = auditEvents;
    }

    // ── Trust domains ───────────────────────────────────────────────────────

    @GetMapping("/trust-domains")
    public Page<TrustDomainEntity> listTrustDomains(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String controllerType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        return trustDomains.search(status, controllerType, PageRequest.of(page, Math.min(size, 100)));
    }

    @GetMapping("/trust-domains/{trustDomainId}")
    public ResponseEntity<TrustDomainEntity> getTrustDomain(@PathVariable UUID trustDomainId) {
        return trustDomains.findById(trustDomainId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /** The domain's family: its parent, if any, and its subdomains. */
    @GetMapping("/trust-domains/{trustDomainId}/hierarchy")
    public ResponseEntity<Map<String, Object>> hierarchy(@PathVariable UUID trustDomainId) {
        if (trustDomains.findById(trustDomainId).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        List<TrustDomainRelationshipEntity> children =
                relationships.findByParentTrustDomainIdAndStatus(trustDomainId, "ACTIVE");
        return ResponseEntity.ok(Map.of(
                "trustDomainId", trustDomainId,
                "parent", relationships.findByChildTrustDomainIdAndStatus(trustDomainId, "ACTIVE")
                        .map(TrustDomainRelationshipEntity::getParentTrustDomainId).orElse(null),
                "children", children.stream().map(TrustDomainRelationshipEntity::getChildTrustDomainId).toList(),
                // Stated on every hierarchy read so no consumer can infer otherwise.
                "familyMembershipGrantsClinicalAccess", false));
    }

    @PostMapping("/trust-domains")
    public ResponseEntity<TrustDomainEntity> createTrustDomain(@RequestBody TrustDomainEntity body) {
        TrustDomainEntity created = governance.createTrustDomain(body, actor());
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PostMapping("/trust-domains/{trustDomainId}/accredit")
    public TrustDomainEntity accredit(@PathVariable UUID trustDomainId) {
        return governance.accreditTrustDomain(trustDomainId, actor());
    }

    // ── Responsibility profiles ─────────────────────────────────────────────

    @PostMapping("/responsibility-profiles")
    public ResponseEntity<ServiceResponsibilityProfileEntity> createProfile(
            @RequestBody ServiceResponsibilityProfileEntity body) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(governance.createProfileDraft(body, actor()));
    }

    @GetMapping("/responsibility-profiles/{profileId}")
    public ResponseEntity<ServiceResponsibilityProfileEntity> getProfile(@PathVariable UUID profileId) {
        return profiles.findById(profileId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /** Validation is a read: it reports findings without changing anything. */
    @GetMapping("/responsibility-profiles/{profileId}/validation")
    public ResponseEntity<Map<String, Object>> validateProfile(@PathVariable UUID profileId) {
        return profiles.findById(profileId)
                .map(p -> {
                    List<String> findings = governance.validateProfile(p);
                    return ResponseEntity.ok(Map.of(
                            "responsibilityProfileId", profileId,
                            "activatable", findings.isEmpty(),
                            "findings", findings,
                            "controllerResolutionState", p.getControllerResolutionState()));
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/responsibility-profiles/{profileId}/activate")
    public ServiceResponsibilityProfileEntity activateProfile(@PathVariable UUID profileId) {
        return governance.activateProfile(profileId, actor());
    }

    @PostMapping("/responsibility-profiles/{profileId}/supersede")
    public ResponseEntity<ServiceResponsibilityProfileEntity> supersedeProfile(
            @PathVariable UUID profileId,
            @RequestBody SupersedeRequest request) {
        ServiceResponsibilityProfileEntity successor =
                governance.supersedeProfile(profileId, request.successor(), request.reason(), actor());
        return ResponseEntity.status(HttpStatus.CREATED).body(successor);
    }

    public record SupersedeRequest(ServiceResponsibilityProfileEntity successor, String reason) {}

    @GetMapping("/responsibility-profiles/{profileId}/history")
    public List<ServiceResponsibilityProfileEntity> profileHistory(@PathVariable UUID profileId) {
        return profiles.findById(profileId)
                .map(p -> profiles.findByProfileCodeOrderByVersionDesc(p.getProfileCode()))
                .orElse(List.of());
    }

    // ── Service agreements ──────────────────────────────────────────────────

    @PostMapping("/service-agreements")
    public ResponseEntity<ServiceAgreementEntity> createAgreement(@RequestBody ServiceAgreementEntity body) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(governance.createAgreementDraft(body, actor()));
    }

    @PostMapping("/service-agreements/{agreementId}/activate")
    public ServiceAgreementEntity activateAgreement(@PathVariable UUID agreementId) {
        return governance.activateAgreement(agreementId, actor());
    }

    @GetMapping("/service-agreements/{agreementId}")
    public ResponseEntity<ServiceAgreementEntity> getAgreement(@PathVariable UUID agreementId) {
        return agreements.findById(agreementId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    // ── Controller resolution ───────────────────────────────────────────────

    /**
     * Resolve the legal controller for a governed operation, or explain precisely why it
     * cannot be resolved. An undetermined controller is {@code 422} with a reason code and
     * the governance action required — never a 200 naming a stand-in.
     */
    @GetMapping("/controller-resolution")
    public ResponseEntity<ControllerResolutionService.Resolution> resolveController(
            @RequestParam String dataDomain,
            @RequestParam String purpose,
            @RequestParam(required = false) UUID facilityId,
            @RequestParam(required = false) UUID nodeId,
            @RequestParam(required = false) UUID serviceAgreementId,
            @RequestParam(required = false) UUID programmeId,
            @RequestParam(required = false) UUID organisationId,
            @RequestParam(required = false) UUID trustDomainId) {

        ControllerResolutionService.ScopeChain chain = new ControllerResolutionService.ScopeChain(
                facilityId, nodeId, serviceAgreementId, programmeId, organisationId, trustDomainId);
        ControllerResolutionService.Resolution result =
                resolution.resolveController(chain, dataDomain, purpose, OffsetDateTime.now());

        // 422: the request was well-formed and the answer is "nobody has decided yet".
        return result.resolved()
                ? ResponseEntity.ok(result)
                : ResponseEntity.unprocessableEntity().body(result);
    }

    // ── Audit history ───────────────────────────────────────────────────────

    @GetMapping("/audit/{targetType}/{targetId}")
    public Page<ResponsibilityAuditEventEntity> auditHistory(
            @PathVariable String targetType,
            @PathVariable UUID targetId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return auditEvents.findByTargetTypeAndTargetIdOrderByOccurredAtDesc(
                targetType, targetId, PageRequest.of(page, Math.min(size, 200)));
    }

    // ── Error contract ──────────────────────────────────────────────────────

    @ExceptionHandler(GovernanceRefusal.class)
    public ResponseEntity<Map<String, Object>> onRefusal(GovernanceRefusal refusal) {
        HttpStatus status = switch (refusal.getReasonCode()) {
            case "TRUST_DOMAIN_NOT_FOUND", "PROFILE_NOT_FOUND", "AGREEMENT_NOT_FOUND" -> HttpStatus.NOT_FOUND;
            case "TRUST_DOMAIN_CODE_IN_USE", "CONFLICTING_ACTIVE_AGREEMENT",
                 "PROFILE_IMMUTABLE", "AGREEMENT_IMMUTABLE",
                 "INVALID_LIFECYCLE_TRANSITION" -> HttpStatus.CONFLICT;
            default -> HttpStatus.UNPROCESSABLE_ENTITY;
        };
        return ResponseEntity.status(status).body(Map.of(
                "reasonCode", refusal.getReasonCode(),
                "message", refusal.getMessage(),
                "affectedScope", refusal.getAffectedScope(),
                "requiredGovernanceAction", refusal.getRequiredGovernanceAction()));
    }

    /**
     * The acting party, derived entirely server-side. A request that arrives without a trust
     * context has no actor, and a governance write with no actor cannot be audited — so it
     * is refused rather than attributed to nobody.
     */
    private GovernanceActor actor() {
        TrustContext ctx = TrustContextHolder.get();
        if (ctx == null || ctx.actorId() == null || ctx.actorId().isBlank()) {
            throw new GovernanceRefusal("ACTOR_NOT_ESTABLISHED",
                    "No server-derived actor is present on this request",
                    "request",
                    "Route the request through the trust plane so an actor can be established");
        }
        // Scope comes from the context's facility/workspace, never from the request body.
        String correlation = ctx.correlationId() == null ? null : ctx.correlationId().toString();
        return new GovernanceActor(ctx.actorId(), ctx.actorType(),
                ctx.facilityId() != null ? "FACILITY" : null, ctx.facilityId(),
                correlation, correlation);
    }
}
