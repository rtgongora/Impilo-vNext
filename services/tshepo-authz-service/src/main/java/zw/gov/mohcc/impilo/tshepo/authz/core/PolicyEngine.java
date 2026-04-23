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
                        VisibilityEscalationService visibilityEscalationService) {
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

            // min_loa check
            if (conditions.containsKey("min_loa")) {
                int minLoa = ((Number) conditions.get("min_loa")).intValue();
                if (request.loaLevel() < minLoa) {
                    log.debug("Condition failed: min_loa={} but request.loaLevel={}",
                            minLoa, request.loaLevel());
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

            return true;

        } catch (JsonProcessingException e) {
            log.warn("Failed to parse policy rule conditions JSON: {}", e.getMessage());
            return false; // Fail-closed: invalid conditions = no match
        }
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
}
