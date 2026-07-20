package zw.gov.mohcc.impilo.tshepo.authz.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.tshepo.authz.config.AuthzProperties;
import zw.gov.mohcc.impilo.tshepo.authz.dto.AuthzInternalRequest;
import zw.gov.mohcc.impilo.tshepo.authz.dto.DutyContext;
import zw.gov.mohcc.impilo.tshepo.authz.dto.EscalationGrantView;
import zw.gov.mohcc.impilo.tshepo.authz.persistence.entity.PolicyDecisionLogEntity;
import zw.gov.mohcc.impilo.tshepo.authz.persistence.entity.PolicyRuleEntity;
import zw.gov.mohcc.impilo.tshepo.authz.persistence.repository.PolicyDecisionLogRepository;
import zw.gov.mohcc.impilo.tshepo.authz.service.*;
import zw.gov.mohcc.impilo.tshepo.contracts.dto.AuthzResponse;
import zw.gov.mohcc.impilo.tshepo.contracts.dto.ConsentDecision;
import zw.gov.mohcc.impilo.tshepo.contracts.dto.Obligations;
import zw.gov.mohcc.impilo.tshepo.contracts.dto.VisibilityProfile;
import zw.gov.mohcc.impilo.tshepo.contracts.enums.PurposeOfUse;
import zw.gov.mohcc.impilo.tshepo.contracts.enums.Verdict;
import zw.gov.mohcc.impilo.tshepo.contracts.headers.TrustHeaders;

import java.util.*;
import java.time.Instant;

/**
 * Policy Decision Point (PDP) for the Impilo platform.
 *
 * <p>This is THE CORE of the trust layer. Every request flowing through Envoy
 * is evaluated here in 7 steps:</p>
 *
 * <ol>
 *   <li><strong>Risk scoring</strong> — lookup device_profile, compute score (0-100).
 *       Blocked devices (score >= 81) are denied immediately.</li>
 *   <li><strong>Purpose validation</strong> — reject requests with unknown or missing
 *       purpose-of-use.</li>
 *   <li><strong>Break-glass check</strong> — if purpose is BREAK_GLASS, require an active
 *       break_glass_request AND a completed step-up challenge.</li>
 *   <li><strong>RBAC/ABAC</strong> — load matching policy_rules for the (actor_type,
 *       resource_type, action, purpose) tuple. Check facility_scope and workspace_scope
 *       constraints. Apply JSONB conditions (min_loa, allowed_facilities, etc.).
 *       First matching DENY rule wins. If no ALLOW rule matches, deny.</li>
 *   <li><strong>Consent evaluation</strong> — for clinical resources (Patient, Encounter,
 *       Observation, DiagnosticReport, MedicationRequest), call tshepo-consent-service.</li>
 *   <li><strong>Risk-based step-up</strong> — if risk >= 61 AND action is sensitive
 *       (DELETE, EXPORT, BULK, MERGE, RECOVERY), require step-up.</li>
 *   <li><strong>ALLOW with obligations</strong> — compute obligations based on purpose
 *       (RESEARCH -> mask PII, PUBLIC_HEALTH -> mask identifiers, OPERATIONS -> facility-scoped).</li>
 * </ol>
 *
 * <p>Every evaluation persists to policy_decision_log AND publishes an audit event
 * to Kafka via the transactional outbox.</p>
 */
@Service
public class PolicyEngine {

    private static final Logger log = LoggerFactory.getLogger(PolicyEngine.class);

    private static final Set<String> CLINICAL_RESOURCE_TYPES = Set.of(
            "Patient", "Encounter", "Observation", "DiagnosticReport", "MedicationRequest",
            "patients", "encounters", "observations", "diagnostic-reports", "medication-requests"
    );

    private static final Set<String> SENSITIVE_ACTIONS = Set.of(
            "DELETE", "EXPORT", "BULK", "MERGE", "RECOVERY"
    );

    private final DeviceRiskScoreEvaluator riskScoring;
    private final PolicyCacheService policyCacheService;
    private final ProviderPrivilegeRevocationStore privilegeRevocationStore;
    private final ConsentClient consentClient;
    private final StepUpService stepUpService;
    private final BreakGlassService breakGlassService;
    private final PolicyDecisionLogRepository decisionLogRepository;
    private final AuditPublisher auditPublisher;
    private final AuthzProperties properties;
    private final ObjectMapper objectMapper;
    private final VisibilityEscalationService visibilityEscalationService;
    private final DelegationClient delegationClient;
    private final OpaDecisionClient opaDecisionClient;

    public PolicyEngine(DeviceRiskScoreEvaluator riskScoring,
                        PolicyCacheService policyCacheService,
                        ProviderPrivilegeRevocationStore privilegeRevocationStore,
                        ConsentClient consentClient,
                        StepUpService stepUpService,
                        BreakGlassService breakGlassService,
                        PolicyDecisionLogRepository decisionLogRepository,
                        AuditPublisher auditPublisher,
                        AuthzProperties properties,
                        ObjectMapper objectMapper,
                        VisibilityEscalationService visibilityEscalationService,
                        DelegationClient delegationClient,
                        OpaDecisionClient opaDecisionClient) {
        this.riskScoring = riskScoring;
        this.policyCacheService = policyCacheService;
        this.privilegeRevocationStore = privilegeRevocationStore;
        this.consentClient = consentClient;
        this.stepUpService = stepUpService;
        this.breakGlassService = breakGlassService;
        this.decisionLogRepository = decisionLogRepository;
        this.auditPublisher = auditPublisher;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.visibilityEscalationService = visibilityEscalationService;
        this.delegationClient = delegationClient;
        this.opaDecisionClient = opaDecisionClient;
    }

    /**
     * Evaluate an authorization request and return a decision.
     *
     * <p>This method MUST be fast — it is called on every inbound request.
     * Policy rules are cached in Redis; device profiles are cached in Redis.
     * The decision log write and outbox write happen in the same transaction.</p>
     */
    @Transactional
    public AuthzResponse evaluate(AuthzInternalRequest request) {
        long startTime = System.nanoTime();

        UUID tenantId = request.tenantId();
        if (tenantId == null) {
            return denyAndLog(request, "MISSING_TENANT", "Tenant ID is required", 0, startTime);
        }

        if (request.providerId() != null && !request.providerId().isBlank()
                && privilegeRevocationStore.isRevoked(request.providerId())) {
            return denyAndLog(request, "PROVIDER_PRIVILEGE_REVOKED",
                    "Provider privilege suspended or revoked (VARAPI)", 0, startTime);
        }

        Optional<EscalationGrantView> activeEscalation = Optional.empty();
        if (request.escalationGrantId() != null && !request.escalationGrantId().isBlank()) {
            activeEscalation = visibilityEscalationService.resolveActiveGrant(request);
            if (activeEscalation.isEmpty()) {
                return denyAndLog(request, "ESCALATION_INVALID",
                        "Escalation grant is missing, expired, or not bound to this actor",
                        0, startTime);
            }
        }

        // ────────────────────────────────────────────────────────────────
        // WORK_CONTEXT duty-token binding (Vashandi-proven "on-duty, here, in this
        // role, right now"). Gated by tshepo.authz.work-context-mode:
        //   OFF     — never read the token.
        //   SHADOW  — introspect + compare token↔headers + audit divergence; NEVER denies
        //             (may fold the proven duty role additively when the token matches).
        //   ENFORCE — a mismatched/revoked token denies a mutating (clinical-write) request.
        // Fail-open by construction (an absent/unresolvable token never denies).
        // ────────────────────────────────────────────────────────────────
        DutyBinding duty = bindWorkContext(request, startTime);
        if (duty.deny() != null) {
            return duty.deny();
        }
        request = duty.request();

        // ────────────────────────────────────────────────────────────────
        // Step 1: Risk scoring — device reputation
        // ────────────────────────────────────────────────────────────────
        int riskScore = riskScoring.score(tenantId, request.deviceFingerprint(), request.actorId());

        if (riskScore >= properties.getRiskThresholds().getDenyThreshold()) {
            return denyAndLog(request, "DEVICE_BLOCKED",
                    "Device risk threshold exceeded (score=" + riskScore + ")",
                    riskScore, startTime);
        }

        // ────────────────────────────────────────────────────────────────
        // Step 2: Purpose-of-use validation
        // ────────────────────────────────────────────────────────────────
        PurposeOfUse purpose = parsePurpose(request.purposeOfUse());
        if (purpose == null) {
            return denyAndLog(request, "INVALID_PURPOSE",
                    "Missing or unrecognized purpose-of-use: " + request.purposeOfUse(),
                    riskScore, startTime);
        }

        // ────────────────────────────────────────────────────────────────
        // Step 3: Break-glass check
        // ────────────────────────────────────────────────────────────────
        if (purpose == PurposeOfUse.BREAK_GLASS) {
            return evaluateBreakGlass(request, riskScore, startTime);
        }

        // ────────────────────────────────────────────────────────────────
        // Step 4: RBAC/ABAC policy evaluation
        // ────────────────────────────────────────────────────────────────
        PolicyStep4 step4 = evaluatePolicies(request, purpose, riskScore, startTime);
        if (step4.deny() != null) {
            return step4.deny();
        }
        PolicyRuleEntity matchedAllowRule = step4.matchedAllowRule();

        // ────────────────────────────────────────────────────────────────
        // Step 4.5: Delegated / act-on-behalf authorization (L5, G-CZO-03)
        // When the actor declares acting FOR another subject (X-Subject-ID ≠ actor), require an
        // ACTIVE, in-scope, unexpired Mvumo delegation with the delegate meeting the assurance
        // floor. Delegation authorises WHO may act; the subject's clinical consent (Step 5) still
        // governs WHAT data. Conjunctive with base RBAC — never widens beyond it. Fail-closed.
        // ────────────────────────────────────────────────────────────────
        AuthzResponse delegationDeny = evaluateDelegation(request, riskScore, startTime);
        if (delegationDeny != null) {
            return delegationDeny;
        }

        // ────────────────────────────────────────────────────────────────
        // Step 4.6: Self-treatment block (work-pro-life isolation, G-PX-01)
        // A provider acting in a WORK context must not open their OWN clinical record — that
        // belongs to My-Life. Emergency / break-glass purposes pass through (requiresConsent is
        // false for them) for emergency self/family care, which is audited.
        // ────────────────────────────────────────────────────────────────
        AuthzResponse selfTreatmentDeny = evaluateSelfTreatment(request, purpose, riskScore, startTime);
        if (selfTreatmentDeny != null) {
            return selfTreatmentDeny;
        }

        // ────────────────────────────────────────────────────────────────
        // Step 5: Consent evaluation (clinical resources)
        // ────────────────────────────────────────────────────────────────
        if (requiresConsent(request.resourceType(), purpose)) {
            ConsentDecision consent = consentClient.evaluateConsent(
                    tenantId, request.resourceType(), request.resourceId(),
                    request.actorId(), purpose.name());

            if (!consent.permitted()) {
                return denyAndLog(request, "CONSENT_DENIED",
                        "Consent not granted: " + consent.reason(),
                        riskScore, startTime);
            }
        }

        // ────────────────────────────────────────────────────────────────
        // Step 6: Risk-based step-up
        // ────────────────────────────────────────────────────────────────
        if (riskScore >= properties.getRiskThresholds().getStepUpTrigger()
                && isHighRiskAction(request.action())) {

            // Check if actor already completed a recent step-up
            if (!stepUpService.hasRecentStepUp(tenantId, request.actorId())) {
                return stepUpAndLog(request, riskScore, startTime);
            }
        }

        // ────────────────────────────────────────────────────────────────
        // Step 7: ALLOW with obligations
        // ────────────────────────────────────────────────────────────────
        Obligations obligations = VisibilityObligationComposer.compose(
                request, purpose, riskScore, matchedAllowRule, activeEscalation, objectMapper);
        Map<String, String> headerMutations = buildHeaderMutations(obligations, request);

        // Phase 3 strangler: SHADOW-compare the OPA gate decision (Java stays authoritative).
        shadowCompareOpa(request, purpose, matchedAllowRule, true);

        return allowAndLog(request, obligations, headerMutations, riskScore, startTime);
    }

    // ════════════════════════════════════════════════════════════════════
    // Step 3: Break-glass evaluation
    // ════════════════════════════════════════════════════════════════════

    private AuthzResponse evaluateBreakGlass(AuthzInternalRequest request, int riskScore, long startTime) {
        UUID tenantId = request.tenantId();
        String actorId = request.actorId();

        // Require an active break-glass request
        if (!breakGlassService.hasActiveBreakGlass(tenantId, actorId)) {
            return denyAndLog(request, "NO_BREAK_GLASS_REQUEST",
                    "Break-glass purpose requires an active break-glass request. " +
                    "Submit one via POST /v1/break-glass first.",
                    riskScore, startTime);
        }

        // Require a completed step-up challenge
        if (!stepUpService.hasRecentStepUp(tenantId, actorId)) {
            return stepUpAndLog(request, riskScore, startTime);
        }

        // Break-glass ALLOWED — with elevated obligations + full visibility envelope
        Obligations obligations = VisibilityObligationComposer.compose(
                request, PurposeOfUse.BREAK_GLASS, riskScore, null, Optional.empty(), objectMapper);
        Map<String, String> headers = buildHeaderMutations(obligations, request);

        log.warn("BREAK-GLASS ALLOW: actor={}, resource={}/{}, correlation={}",
                actorId, request.resourceType(), request.resourceId(), request.correlationId());

        return allowAndLog(request, obligations, headers, riskScore, startTime);
    }

    // ════════════════════════════════════════════════════════════════════
    // Step 4: RBAC/ABAC policy evaluation
    // ════════════════════════════════════════════════════════════════════

    private PolicyStep4 evaluatePolicies(AuthzInternalRequest request, PurposeOfUse purpose,
                                         int riskScore, long startTime) {
        UUID tenantId = request.tenantId();

        List<PolicyRuleEntity> rules = policyCacheService.getActiveRulesForResource(
                tenantId, request.resourceType());

        if (rules.isEmpty()) {
            if (purpose == PurposeOfUse.SYSTEM) {
                return PolicyStep4.continueWith(null);
            }
            return PolicyStep4.deny(denyAndLog(request, "NO_MATCHING_RULES",
                    "No active policy rules found for resource type: " + request.resourceType(),
                    riskScore, startTime));
        }

        PolicyRuleEntity matchedAllowRule = null;

        for (PolicyRuleEntity rule : rules) {
            if (!matchesRule(rule, request, purpose)) {
                continue;
            }

            if ("DENY".equalsIgnoreCase(rule.getEffect())) {
                return PolicyStep4.deny(denyAndLog(request, "POLICY_DENY",
                        "Denied by policy rule: " + rule.getName(),
                        riskScore, startTime));
            }

            if ("ALLOW".equalsIgnoreCase(rule.getEffect())) {
                if (rule.isFacilityScope() && request.facilityId() == null) {
                    continue;
                }
                if (rule.isWorkspaceScope() && request.workspaceId() == null) {
                    continue;
                }
                if (!evaluateConditions(rule.getConditions(), request)) {
                    continue;
                }
                matchedAllowRule = rule;
                break;
            }
        }

        if (matchedAllowRule == null) {
            if (purpose == PurposeOfUse.SYSTEM) {
                return PolicyStep4.continueWith(null);
            }
            return PolicyStep4.deny(denyAndLog(request, "NO_ALLOW_RULE",
                    "No matching ALLOW rule for this actor/resource/action combination",
                    riskScore, startTime));
        }

        return PolicyStep4.continueWith(matchedAllowRule);
    }

    private record PolicyStep4(AuthzResponse deny, PolicyRuleEntity matchedAllowRule) {
        static PolicyStep4 continueWith(PolicyRuleEntity matchedAllowRule) {
            return new PolicyStep4(null, matchedAllowRule);
        }

        static PolicyStep4 deny(AuthzResponse deny) {
            return new PolicyStep4(deny, null);
        }
    }

    /**
     * Check if a policy rule matches the current request context.
     */
    private boolean matchesRule(PolicyRuleEntity rule, AuthzInternalRequest request, PurposeOfUse purpose) {
        // Actor type match (null in rule = wildcard)
        if (rule.getActorType() != null && !rule.getActorType().isEmpty()) {
            if (!rule.getActorType().equalsIgnoreCase(request.actorType())) {
                return false;
            }
        }

        // Role match (null = wildcard)
        if (rule.getRole() != null && !rule.getRole().isEmpty()) {
            if (request.roles() == null || !request.roles().contains(rule.getRole())) {
                return false;
            }
        }

        // Action match (null = wildcard)
        if (rule.getAction() != null && !rule.getAction().isEmpty()) {
            String requestAction = request.action();
            if (requestAction == null) return false;

            // Match by prefix: rule "DELETE" matches "DELETE:/v1/patients/..."
            String actionVerb = requestAction.contains(":")
                    ? requestAction.substring(0, requestAction.indexOf(":"))
                    : requestAction;
            if (!rule.getAction().equalsIgnoreCase(actionVerb)
                    && !rule.getAction().equals("*")) {
                return false;
            }
        }

        // Purpose match (null = wildcard)
        if (rule.getPurpose() != null && !rule.getPurpose().isEmpty()) {
            if (!rule.getPurpose().equalsIgnoreCase(purpose.name())) {
                return false;
            }
        }

        return true;
    }

    /**
     * Evaluate JSONB conditions attached to a policy rule.
     *
     * <p>Supported conditions:
     * <ul>
     *   <li>{@code min_loa}: minimum Level of Assurance required</li>
     *   <li>{@code allowed_facilities}: list of facility UUIDs the actor must be in</li>
     *   <li>{@code allowed_actor_types}: list of permitted actor types</li>
     *   <li>{@code max_risk_score}: maximum risk score allowed</li>
     *   <li>{@code path_contains}: the normalised request path must contain this value as a
     *       complete path-segment sequence (i.e. followed by {@code /} or end-of-path), or, if a
     *       list, at least one of them. Lets a rule pin a specific endpoint even when the coarse
     *       resource-type (the last path segment, e.g. {@code decision}) collides across services
     *       — closing the cross-service over-grant that bare segment matching would allow. The
     *       path is normalised first (query/matrix/fragment stripped, literal and percent-encoded)
     *       and matching is segment-bounded, so neither query smuggling nor a longer same-prefix
     *       route can satisfy the pin. Fail-closed: a present condition with a null/non-matching
     *       path denies.</li>
     * </ul>
     * </p>
     */
    @SuppressWarnings("unchecked")
    private boolean evaluateConditions(String conditionsJson, AuthzInternalRequest request) {
        if (conditionsJson == null || conditionsJson.isBlank()) {
            return true; // No conditions = unconditional match
        }

        try {
            Map<String, Object> conditions = objectMapper.readValue(conditionsJson,
                    new TypeReference<>() {});

            // min_loa check — keyed on the EFFECTIVE LoA (the stronger of the session's
            // ACR-derived login level and the actor's current identity-assurance level
            // propagated via X-Assurance-Level). This is what makes a self-service
            // verification upgrade actually change what policy sees (closes G-CZO-01).
            if (conditions.containsKey("min_loa")) {
                int minLoa = ((Number) conditions.get("min_loa")).intValue();
                int effLoa = effectiveLoa(request);
                if (effLoa < minLoa) {
                    log.debug("Condition failed: min_loa={} but effectiveLoa={} (acr={}, assuranceHeader={})",
                            minLoa, effLoa, request.loaLevel(), request.assuranceLevel());
                    return false;
                }
            }

            // allowed_facilities check
            if (conditions.containsKey("allowed_facilities") && request.facilityId() != null) {
                List<String> allowedFacilities = (List<String>) conditions.get("allowed_facilities");
                if (!allowedFacilities.contains(request.facilityId().toString())) {
                    log.debug("Condition failed: facility {} not in allowed list",
                            request.facilityId());
                    return false;
                }
            }

            // allowed_actor_types check
            if (conditions.containsKey("allowed_actor_types")) {
                List<String> allowedTypes = (List<String>) conditions.get("allowed_actor_types");
                if (request.actorType() == null || !allowedTypes.contains(request.actorType())) {
                    log.debug("Condition failed: actor type {} not in allowed list",
                            request.actorType());
                    return false;
                }
            }

            // max_risk_score check
            if (conditions.containsKey("max_risk_score")) {
                // This will be checked during risk scoring phase
                // but can also be a per-rule override
                int maxRisk = ((Number) conditions.get("max_risk_score")).intValue();
                // We don't have the risk score in the rule evaluation context directly,
                // but the overall engine will have caught blocked devices already
            }

            // responsibility scope check
            if (conditions.containsKey("allowed_scope_refs")) {
                List<String> allowedScopes = (List<String>) conditions.get("allowed_scope_refs");
                Set<String> requestScopes = new HashSet<>();
                if (request.facilityId() != null) requestScopes.add(request.facilityId().toString());
                if (request.workspaceId() != null) requestScopes.add(request.workspaceId().toString());
                if (request.departmentId() != null) requestScopes.add(request.departmentId());
                if (request.wardId() != null) requestScopes.add(request.wardId());
                if (request.programmeId() != null) requestScopes.add(request.programmeId());
                if (request.subjectId() != null) requestScopes.add(request.subjectId());
                boolean scopeMatch = requestScopes.stream().anyMatch(allowedScopes::contains);
                if (!scopeMatch) {
                    log.debug("Condition failed: request scopes {} not in allowed_scope_refs {}", requestScopes, allowedScopes);
                    return false;
                }
            }

            // path_contains check — pins a rule to a specific endpoint so a colliding coarse
            // resource-type (last path segment) cannot grant access to a same-segment endpoint
            // in another service. Fail-closed: a present condition with a null or non-matching
            // path denies. The path is normalised first — query string (?), matrix params (;)
            // and fragment (#) are stripped — so a caller cannot smuggle the required substring
            // into the query of an unrelated endpoint (e.g. POST /x/decision?=/cadre/decision).
            if (conditions.containsKey("path_contains")) {
                String reqPath = normalisePathForMatch(request.path());
                Object pc = conditions.get("path_contains");
                boolean matched;
                if (pc instanceof List<?> list) {
                    matched = list.stream()
                            .anyMatch(x -> x != null && pathContainsSegment(reqPath, x.toString()));
                } else {
                    matched = pc != null && pathContainsSegment(reqPath, pc.toString());
                }
                if (!matched) {
                    log.debug("Condition failed: path '{}' does not contain required path_contains '{}'",
                            reqPath, pc);
                    return false;
                }
            }

            // account assurance state check — a "verified" account means the actor's current
            // identity-assurance level is at least LOA3 (in-person verified, per AssurancePolicy).
            // Legacy verification-state strings ("VERIFIED"/"REGISTRY") still pass for back-compat.
            if (Boolean.TRUE.equals(conditions.get("account_assurance_required"))) {
                if (!accountVerified(request)) {
                    log.debug("Condition failed: account assurance required but got {}", request.assuranceLevel());
                    return false;
                }
            }

            // verification grace period check
            if (conditions.containsKey("verification_grace_expiry_epoch_ms")) {
                long expiryEpochMs = ((Number) conditions.get("verification_grace_expiry_epoch_ms")).longValue();
                boolean verified = accountVerified(request);
                if (!verified && Instant.now().toEpochMilli() > expiryEpochMs) {
                    log.debug("Condition failed: verification grace expired for actor {}", request.actorId());
                    return false;
                }
            }

            return true;

        } catch (JsonProcessingException e) {
            log.warn("Failed to parse policy rule conditions JSON: {}", e.getMessage());
            return false; // Fail-closed: invalid conditions = no match
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // Assurance-level helpers (G-CZO-01: identity-assurance → policy)
    // ════════════════════════════════════════════════════════════════════

    /**
     * Effective Level of Assurance for policy: the stronger of the session's ACR-derived
     * login LoA (frozen at token validation) and the actor's current identity-assurance
     * level propagated via {@code X-Assurance-Level} (populated authoritatively by the BFF
     * from identity-assurance-service, the canonical owner). Taking the max is monotonic —
     * it never reduces access below the prior ACR-only behaviour, and lifts it the moment a
     * verification upgrade is recorded.
     */
    private int effectiveLoa(AuthzInternalRequest request) {
        return Math.max(request.loaLevel(), parseAssuranceLoa(request.assuranceLevel()));
    }

    /**
     * Parse an {@code X-Assurance-Level} header value ("LOA3" or bare "3") to its numeric
     * rank 1..4. Returns 0 when absent or unparseable (fail-safe: contributes nothing to the
     * effective LoA, leaving the ACR level in force).
     */
    private static int parseAssuranceLoa(String raw) {
        if (raw == null || raw.isBlank()) return 0;
        String v = raw.trim().toUpperCase(Locale.ROOT);
        if (v.startsWith("LOA")) v = v.substring(3).trim();
        try {
            int n = Integer.parseInt(v);
            return (n >= 1 && n <= 4) ? n : 0;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * Whether the actor holds a "verified" account for {@code account_assurance_required} rules:
     * identity-assurance level LOA3+ (in-person verified), or a legacy verification-state string.
     * Note this is keyed on the propagated assurance level only (not the ACR login level), so a
     * strong login alone does not satisfy an account-verification requirement.
     */
    private static boolean accountVerified(AuthzInternalRequest request) {
        if (parseAssuranceLoa(request.assuranceLevel()) >= 3) return true;
        String v = request.assuranceLevel() == null ? "" : request.assuranceLevel().toUpperCase(Locale.ROOT);
        return v.contains("VERIFIED") || v.contains("REGISTRY");
    }

    // ════════════════════════════════════════════════════════════════════
    // Step 4.5: Delegated / act-on-behalf helpers (L5, G-CZO-03)
    // ════════════════════════════════════════════════════════════════════

    /**
     * Returns a DENY when the actor declares acting for another subject ({@code X-Subject-ID} ≠
     * actor) but lacks an active, in-scope, sufficiently-assured delegation; returns {@code null}
     * when the request is not delegated (subject absent or == actor) or the delegation authorises it.
     * Fail-closed: a resolution error denies. Delegation authorises WHO may act; the subject's
     * clinical consent (Step 5) still governs WHAT data.
     */
    private AuthzResponse evaluateDelegation(AuthzInternalRequest request, int riskScore, long startTime) {
        String subjectId = request.subjectId();
        String actorId = request.actorId();
        if (subjectId == null || subjectId.isBlank() || subjectId.equals(actorId)) {
            return null; // not acting on behalf of a different subject
        }
        DelegationResolution res;
        try {
            res = delegationClient.resolve(request.tenantId(), actorId, subjectId);
        } catch (Exception e) {
            log.warn("Delegation resolution failed for actor={} subject={}: {}", actorId, subjectId, e.getMessage());
            return denyAndLog(request, "DELEGATION_UNAVAILABLE",
                    "Delegation could not be verified", riskScore, startTime);
        }
        if (res == null || !res.active()) {
            return denyAndLog(request, "DELEGATION_NOT_ACTIVE",
                    "No active delegation authorising this actor to act for the subject", riskScore, startTime);
        }
        if (effectiveLoa(request) < res.assuranceFloor()) {
            return denyAndLog(request, "DELEGATION_ASSURANCE_TOO_LOW",
                    "Delegate assurance below the delegation floor", riskScore, startTime);
        }
        if (!scopeAllows(res.scope(), request.resourceType())) {
            return denyAndLog(request, "DELEGATION_OUT_OF_SCOPE",
                    "Requested resource is outside the delegation scope", riskScore, startTime);
        }
        return null; // delegation authorises the actor; continue to consent (against the subject)
    }

    /**
     * Self-treatment block (work-pro-life isolation, G-PX-01). A provider acting in a WORK context
     * (Provider ID activated + a facility/workspace/shift context) may not open a clinical record
     * whose subject is their own person anchor — that is a My-Life action, not a work action.
     * Returns a DENY in that case, else {@code null} to continue.
     *
     * <p>Scoped to {@link #requiresConsent} clinical resources, which already excludes EMERGENCY /
     * BREAK_GLASS / SYSTEM purposes — so emergency self/family care passes through (audited). A
     * citizen (or a provider with no active work context) reaching their own record in My-Life is
     * NOT blocked here (LIFE-SELF-ONLY).</p>
     */
    private AuthzResponse evaluateSelfTreatment(AuthzInternalRequest request, PurposeOfUse purpose,
                                                int riskScore, long startTime) {
        if (!requiresConsent(request.resourceType(), purpose)) {
            return null;
        }
        boolean providerWorkContext = request.providerId() != null && !request.providerId().isBlank()
                && (request.facilityId() != null || request.workspaceId() != null
                    || (request.shiftId() != null && !request.shiftId().isBlank()));
        if (!providerWorkContext) {
            return null;
        }
        String subject = request.subjectId();
        if (subject != null && !subject.isBlank() && subject.equals(request.actorId())) {
            return denyAndLog(request, "SELF_TREATMENT_BLOCKED",
                    "A provider may not open their own clinical record in work mode; use My Life, "
                            + "or an emergency/break-glass purpose for emergency self/family care.",
                    riskScore, startTime);
        }
        return null;
    }

    // ════════════════════════════════════════════════════════════════════
    // Phase 3: OPA shadow comparison (strangler — Java authoritative)
    // ════════════════════════════════════════════════════════════════════

    /**
     * In SHADOW mode, evaluate the OPA gate decision (purpose/min_loa/account-assurance) on a
     * prepared input and log when it diverges from the Java verdict. OPA is never authoritative
     * here; any error is swallowed. OFF by default → no-op, zero behaviour change. (The full
     * DB-rule RBAC/ABAC requires policy_rules delivered as OPA bundle data — a later increment.)
     */
    private void shadowCompareOpa(AuthzInternalRequest request, PurposeOfUse purpose,
                                  PolicyRuleEntity matchedRule, boolean javaAllow) {
        String mode = properties.getOpaMode();
        if (mode == null || "OFF".equalsIgnoreCase(mode)) {
            return;
        }
        try {
            Map<String, Object> input = new LinkedHashMap<>();
            input.put("actor_id", request.actorId() != null ? request.actorId() : "");
            input.put("purpose", purpose != null ? purpose.name() : "");
            input.put("loa", request.loaLevel());
            input.put("assurance_loa", parseAssuranceLoa(request.assuranceLevel()));
            Map<String, Object> conditions = parseConditions(matchedRule);
            if (conditions.get("min_loa") instanceof Number n) {
                input.put("min_loa", n.intValue());
            }
            if (Boolean.TRUE.equals(conditions.get("account_assurance_required"))) {
                input.put("account_assurance_required", true);
            }
            OpaDecision opa = opaDecisionClient.decide(input);
            if (opa == null) {
                return; // OPA undefined / unreachable — no signal
            }
            if (opa.allow() != javaAllow) {
                log.warn("OPA-SHADOW divergence: java.allow={} opa.allow={} reasons={} actor={} purpose={} correlation={}",
                        javaAllow, opa.allow(), opa.denyReasons(), request.actorId(),
                        purpose != null ? purpose.name() : null, request.correlationId());
            } else {
                log.debug("OPA-SHADOW agree: allow={} actor={}", javaAllow, request.actorId());
            }
        } catch (Exception e) {
            log.debug("OPA-SHADOW comparison skipped: {}", e.getMessage());
        }
    }

    /** Query/matrix/fragment delimiters — literal and percent-encoded — that end the route path. */
    private static final String[] PATH_TAIL_DELIMITERS = {"?", ";", "#", "%3f", "%3b", "%23"};

    /**
     * Normalise a request path for matching: strip the query string, matrix parameters, and
     * fragment — in both literal ({@code ?;#}) and percent-encoded ({@code %3F/%3B/%23}) form —
     * so caller-controlled query content cannot smuggle the required substring into an unrelated
     * route (e.g. {@code POST /x/decision?=/cadre/decision} or its {@code %3F} encoding). The
     * encoded forms are cut because Envoy delivers {@code :path} undecoded; cutting them prevents
     * a proxy/parser differential from satisfying the pin. Returns "" for a null path.
     */
    private static String normalisePathForMatch(String path) {
        if (path == null) {
            return "";
        }
        String lower = path.toLowerCase(Locale.ROOT);
        int cut = path.length();
        for (String delim : PATH_TAIL_DELIMITERS) {
            int i = lower.indexOf(delim);
            if (i >= 0 && i < cut) {
                cut = i;
            }
        }
        return path.substring(0, cut);
    }

    /**
     * Segment-bounded path containment: {@code pin} must appear in {@code path} as a complete
     * path-segment sequence — i.e. immediately followed by {@code /} or the end of the path —
     * not merely as an arbitrary substring. This prevents a pin like {@code /care-plans} from
     * being satisfied by an unrelated route such as {@code /care-plans-export}, so the pin
     * authorises only the intended endpoint (and its sub-paths).
     */
    private static boolean pathContainsSegment(String path, String pin) {
        if (path == null || pin == null || pin.isEmpty()) {
            return false;
        }
        int from = 0;
        int idx;
        while ((idx = path.indexOf(pin, from)) >= 0) {
            int end = idx + pin.length();
            if (end == path.length() || path.charAt(end) == '/') {
                return true;
            }
            from = idx + 1;
        }
        return false;
    }

    private Map<String, Object> parseConditions(PolicyRuleEntity rule) {
        if (rule == null || rule.getConditions() == null || rule.getConditions().isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(rule.getConditions(), new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            return Map.of();
        }
    }

    /** Coarse scope containment: "*" allows all; otherwise a token must equal the resource type or prefix it ("type:..."). */
    private static boolean scopeAllows(List<String> scope, String resourceType) {
        if (scope == null || scope.isEmpty()) return false;
        String rt = resourceType == null ? "" : resourceType.toLowerCase(Locale.ROOT);
        for (String s : scope) {
            if (s == null) continue;
            String tok = s.trim().toLowerCase(Locale.ROOT);
            if (tok.equals("*") || tok.equals(rt) || (!rt.isEmpty() && tok.startsWith(rt + ":"))) {
                return true;
            }
        }
        return false;
    }

    // ════════════════════════════════════════════════════════════════════
    // Step 5: Consent evaluation helpers
    // ════════════════════════════════════════════════════════════════════

    private boolean requiresConsent(String resourceType, PurposeOfUse purpose) {
        if (resourceType == null) return false;
        // Emergency and break-glass override consent (with elevated audit)
        if (purpose == PurposeOfUse.EMERGENCY || purpose == PurposeOfUse.BREAK_GLASS) {
            return false;
        }
        // System calls don't require consent
        if (purpose == PurposeOfUse.SYSTEM) {
            return false;
        }
        return CLINICAL_RESOURCE_TYPES.contains(resourceType);
    }

    // ════════════════════════════════════════════════════════════════════
    // Step 6: Risk-based step-up helpers
    // ════════════════════════════════════════════════════════════════════

    private boolean isHighRiskAction(String action) {
        if (action == null) return false;
        String upper = action.toUpperCase();
        return SENSITIVE_ACTIONS.stream().anyMatch(upper::contains);
    }

    private Map<String, String> buildHeaderMutations(Obligations obligations,
                                                      AuthzInternalRequest request) {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put(TrustHeaders.DECISION, "ALLOW");
        headers.put(TrustHeaders.ACTOR_ID, request.actorId() != null ? request.actorId() : "");
        headers.put(TrustHeaders.ACTOR_TYPE, request.actorType() != null ? request.actorType() : "");

        if (request.tenantId() != null) {
            headers.put(TrustHeaders.TENANT_ID, request.tenantId().toString());
        }
        if (request.correlationId() != null) {
            headers.put(TrustHeaders.CORRELATION_ID, request.correlationId().toString());
        }
        if (request.providerId() != null) {
            headers.put(TrustHeaders.PROVIDER_ID, request.providerId());
        }
        if (request.subjectId() != null) {
            headers.put(TrustHeaders.SUBJECT_ID, request.subjectId());
        }
        if (request.assuranceLevel() != null) {
            headers.put(TrustHeaders.ASSURANCE_LEVEL, request.assuranceLevel());
        }

        if (obligations != null) {
            if (obligations.maxScope() != null) {
                headers.put(TrustHeaders.MAX_SCOPE, obligations.maxScope());
            }
            if (obligations.maskFields() != null && !obligations.maskFields().isEmpty()) {
                headers.put(TrustHeaders.MASK_FIELDS, String.join(",", obligations.maskFields()));
            }
            if (obligations.loggingLevel() != null) {
                headers.put(TrustHeaders.LOGGING_LEVEL, obligations.loggingLevel());
            }

            try {
                String obligationsJson = objectMapper.writeValueAsString(obligations);
                headers.put(TrustHeaders.OBLIGATIONS, obligationsJson);
            } catch (JsonProcessingException e) {
                headers.put(TrustHeaders.OBLIGATIONS, "{}");
            }

            VisibilityProfile vp = obligations.visibilityProfile();
            if (vp != null) {
                if (vp.visibilityTier() != null) {
                    headers.put(TrustHeaders.VISIBILITY_TIER, vp.visibilityTier());
                }
                if (vp.piiAccess() != null) {
                    headers.put(TrustHeaders.PII_ACCESS, vp.piiAccess());
                }
                if (vp.clinicalAccess() != null) {
                    headers.put(TrustHeaders.CLINICAL_ACCESS, vp.clinicalAccess());
                }
                if (vp.aggregateOnly() != null) {
                    headers.put(TrustHeaders.AGGREGATE_ONLY, Boolean.toString(vp.aggregateOnly()));
                }
                if (vp.resourceSensitivityClass() != null) {
                    headers.put(TrustHeaders.RESOURCE_SENSITIVITY, vp.resourceSensitivityClass());
                }
                if (vp.escalationGrantId() != null) {
                    headers.put(TrustHeaders.ESCALATION_GRANT_ID, vp.escalationGrantId());
                }
                if (vp.exportPolicy() != null) {
                    headers.put(TrustHeaders.EXPORT_POLICY, vp.exportPolicy());
                }
                if (vp.suppressFields() != null && !vp.suppressFields().isEmpty()) {
                    headers.put(TrustHeaders.SUPPRESS_FIELDS, String.join(",", vp.suppressFields()));
                }
                if (vp.drillDownAllowed() != null) {
                    headers.put(TrustHeaders.DRILL_DOWN_ALLOWED, Boolean.toString(vp.drillDownAllowed()));
                }
            }
        }

        return headers;
    }

    // ════════════════════════════════════════════════════════════════════
    // Decision logging and response building
    // ════════════════════════════════════════════════════════════════════

    private AuthzResponse allowAndLog(AuthzInternalRequest request, Obligations obligations,
                                       Map<String, String> headers, int riskScore, long startTime) {
        persistDecision(request, "ALLOW", riskScore, null, null, obligations, startTime);
        auditPublisher.queueAuditEvent(request, "ALLOW", riskScore, null, obligations);

        return AuthzResponse.allow(obligations, riskScore, headers);
    }

    private AuthzResponse denyAndLog(AuthzInternalRequest request, String errorCode,
                                      String errorMessage, int riskScore, long startTime) {
        persistDecision(request, "DENY", riskScore, errorCode, null, null, startTime);
        auditPublisher.queueAuditEvent(request, "DENY", riskScore, errorCode);

        log.warn("DENY: actor={}, action={}, resource={}, reason={}, correlation={}, " +
                "provider={}, department={}, ward={}, programme={}, subject={}, assurance={}",
                request.actorId(), request.action(), request.resourceType(),
                errorCode, request.correlationId(),
                request.providerId(), request.departmentId(), request.wardId(),
                request.programmeId(), request.subjectId(), request.assuranceLevel());

        return AuthzResponse.deny(errorCode, errorMessage, riskScore);
    }

    private AuthzResponse stepUpAndLog(AuthzInternalRequest request, int riskScore, long startTime) {
        List<String> methods = properties.getStepUpMethods();
        String methodsStr = String.join(",", methods);

        persistDecision(request, "STEP_UP_REQUIRED", riskScore, null, methodsStr, null, startTime);
        auditPublisher.queueAuditEvent(request, "STEP_UP_REQUIRED", riskScore, null);

        log.info("STEP_UP: actor={}, methods={}, correlation={}",
                request.actorId(), methods, request.correlationId());

        return AuthzResponse.stepUp(methods, riskScore);
    }

    private void persistDecision(AuthzInternalRequest request, String verdict, int riskScore,
                                  String denyReason, String stepUpMethods,
                                  Obligations obligations, long startTime) {
        PolicyDecisionLogEntity entry = new PolicyDecisionLogEntity();
        entry.setTenantId(request.tenantId());
        entry.setCorrelationId(request.correlationId());
        entry.setActorId(request.actorId() != null ? request.actorId() : "anonymous");
        entry.setActorType(request.actorType());
        entry.setAction(request.action() != null ? request.action() : "UNKNOWN");
        entry.setResourceType(request.resourceType());
        entry.setResourceId(request.resourceId());
        entry.setPurposeOfUse(request.purposeOfUse());
        entry.setVerdict(verdict);
        entry.setRiskScore(riskScore);
        entry.setDenyReason(denyReason);
        entry.setStepUpMethods(stepUpMethods);
        entry.setDeviceFingerprint(request.deviceFingerprint());
        entry.setFacilityId(request.facilityId());
        entry.setWorkspaceId(request.workspaceId());
        entry.setProviderId(request.providerId());
        entry.setDepartmentId(request.departmentId());
        entry.setWardId(request.wardId());
        entry.setProgrammeId(request.programmeId());
        entry.setSubjectId(request.subjectId());
        entry.setAssuranceLevel(request.assuranceLevel());

        if (obligations != null) {
            try {
                entry.setObligations(objectMapper.writeValueAsString(obligations));
            } catch (JsonProcessingException e) {
                entry.setObligations("{}");
            }
        }

        decisionLogRepository.save(entry);
    }

    // ════════════════════════════════════════════════════════════════════
    // Utility
    // ════════════════════════════════════════════════════════════════════

    private PurposeOfUse parsePurpose(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return PurposeOfUse.valueOf(value.trim().toUpperCase().replace('-', '_'));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // WORK_CONTEXT duty-token binding
    // ════════════════════════════════════════════════════════════════════

    /** Result of the binding step: the (possibly role-augmented) request, and an optional deny. */
    private record DutyBinding(AuthzInternalRequest request, AuthzResponse deny) {}

    /**
     * Bind the introspected WORK_CONTEXT duty token to this request. See the call site for
     * the mode semantics. Returns the effective request (duty role folded in when the token
     * matches) plus an optional ENFORCE denial. Never denies in OFF/SHADOW.
     */
    private DutyBinding bindWorkContext(AuthzInternalRequest request, long startTime) {
        String mode = properties.getWorkContextMode();
        if (mode == null || "OFF".equalsIgnoreCase(mode)) {
            return new DutyBinding(request, null);
        }
        DutyContext dc = request.dutyContext();
        if (dc == null) {
            return new DutyBinding(request, null);
        }
        boolean enforce = "ENFORCE".equalsIgnoreCase(mode);
        boolean protectedWrite = isMutating(request.method());

        // Token carried but identity says inactive (revoked / expired / unknown jti).
        if (dc.present() && !dc.active()) {
            signalWorkContext(request, "WORK_CONTEXT_REVOKED", Map.of("reason", "introspection_inactive"));
            if (enforce && protectedWrite) {
                return new DutyBinding(request, denyAndLog(request, "WORK_TOKEN_REVOKED",
                        "Work-context token is revoked or expired", 0, startTime));
            }
            return new DutyBinding(request, null);
        }

        // No usable token. Absence NEVER denies (S2S / citizen / non-duty actors legitimately
        // carry none) — even under ENFORCE; it is only a shadow signal on a present-but-wrong-kind
        // token during a clinical write.
        if (!dc.usable()) {
            if (protectedWrite && dc.present()) {
                signalWorkContext(request, "WORK_CONTEXT_UNUSABLE",
                        Map.of("tokenKind", String.valueOf(dc.tokenKind())));
            }
            return new DutyBinding(request, null);
        }

        // Usable token — compare its proven context against the loose client trust headers.
        List<String> mismatches = new ArrayList<>();
        if (dc.actorId() != null && request.actorId() != null
                && !dc.actorId().equalsIgnoreCase(request.actorId())) {
            mismatches.add("actor");
        }
        if (dc.facilityId() != null && request.facilityId() != null
                && !dc.facilityId().equalsIgnoreCase(request.facilityId().toString())) {
            mismatches.add("facility");
        }
        if (dc.workspaceId() != null && request.workspaceId() != null
                && !dc.workspaceId().equalsIgnoreCase(request.workspaceId().toString())) {
            mismatches.add("workspace");
        }
        if (dc.providerId() != null && request.providerId() != null
                && !dc.providerId().equalsIgnoreCase(request.providerId())) {
            mismatches.add("provider");
        }

        if (!mismatches.isEmpty()) {
            signalWorkContext(request, "WORK_CONTEXT_MISMATCH", Map.of(
                    "fields", String.join(",", mismatches),
                    "tokenFacility", String.valueOf(dc.facilityId()),
                    "headerFacility", String.valueOf(request.facilityId())));
            if (enforce && protectedWrite) {
                return new DutyBinding(request, denyAndLog(request, "WORK_TOKEN_CONTEXT_MISMATCH",
                        "Trust headers do not match the duty token (" + String.join(",", mismatches) + ")",
                        0, startTime));
            }
            // Shadow: the context is suspect — do NOT fold the duty role.
            return new DutyBinding(request, null);
        }

        // Matched. Fold the duty role into the effective role set (additive — never removes a
        // Keycloak-claim role) so policy rules authored against the duty role also match.
        AuthzInternalRequest effective = request;
        if (dc.role() != null && !dc.role().isBlank()) {
            List<String> roles = new ArrayList<>(request.roles() != null ? request.roles() : List.of());
            if (roles.stream().noneMatch(r -> r != null && r.equalsIgnoreCase(dc.role()))) {
                roles.add(dc.role());
                effective = request.withRoles(roles);
            }
        }
        signalWorkContext(request, "WORK_CONTEXT_MATCHED", Map.of(
                "facility", String.valueOf(dc.facilityId()),
                "role", String.valueOf(dc.role()),
                "assignment", String.valueOf(dc.assignmentId())));
        return new DutyBinding(effective, null);
    }

    /** Emit a governance signal (event_outbox) + log for a work-context binding outcome. */
    private void signalWorkContext(AuthzInternalRequest request, String eventType, Map<String, Object> extra) {
        try {
            Map<String, Object> payload = new HashMap<>(extra);
            payload.put("actorId", request.actorId());
            payload.put("tenantId", request.tenantId() != null ? request.tenantId().toString() : null);
            payload.put("path", request.path());
            payload.put("method", request.method());
            payload.put("mode", properties.getWorkContextMode());
            auditPublisher.queueGovernanceEvent(eventType, request.actorId(), payload);
        } catch (Exception e) {
            log.warn("Failed to emit work-context signal {}: {}", eventType, e.getMessage());
        }
        log.info("WORK_CONTEXT binding: {} actor={} path={} mode={}",
                eventType, request.actorId(), request.path(), properties.getWorkContextMode());
    }

    private static boolean isMutating(String method) {
        if (method == null) return false;
        String m = method.toUpperCase();
        return m.equals("POST") || m.equals("PUT") || m.equals("PATCH") || m.equals("DELETE");
    }
}
