package zw.gov.mohcc.impilo.tshepo.authz.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
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
import zw.gov.mohcc.impilo.tshepo.contracts.dto.AuthenticationAssurance;
import zw.gov.mohcc.impilo.tshepo.contracts.dto.AuthzResponse;
import zw.gov.mohcc.impilo.tshepo.contracts.dto.ConsentDecision;
import zw.gov.mohcc.impilo.tshepo.contracts.dto.Obligations;
import zw.gov.mohcc.impilo.tshepo.contracts.dto.StepUpRequirement;
import zw.gov.mohcc.impilo.tshepo.contracts.dto.VisibilityProfile;
import zw.gov.mohcc.impilo.tshepo.contracts.enums.PurposeOfUse;
import zw.gov.mohcc.impilo.tshepo.contracts.enums.Verdict;
import zw.gov.mohcc.impilo.tshepo.contracts.headers.TrustHeaders;
import zw.gov.mohcc.impilo.tshepo.contracts.v1.TrustChallengeDecision;
import zw.gov.mohcc.impilo.tshepo.contracts.v1.TrustChallengeOutcome;
import zw.gov.mohcc.impilo.tshepo.contracts.v1.adapter.AuthzResponseChallengeAdapter;
import zw.gov.mohcc.impilo.tshepo.contracts.v1.adapter.LegacyAuthenticationAssuranceAdapter;

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
 *   <li><strong>Break-glass check</strong> — if purpose is BREAK_GLASS, apply the break-glass
 *       doctrine guard (verified provider capacity + facility context + named patient), then require
 *       an active break_glass_request (mandatory reason + limited duration) AND a completed step-up
 *       challenge. Break-glass never turns an unknown user into a health worker.</li>
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

    /** National duty satisfies any jurisdiction-scoped rule; see the allowed_jurisdictions check. */
    private static final String JURISDICTION_NATIONAL = "NATIONAL";

    private static final Logger log = LoggerFactory.getLogger(PolicyEngine.class);

    private static final Set<String> CLINICAL_RESOURCE_TYPES = Set.of(
            "Patient", "Encounter", "Observation", "DiagnosticReport", "MedicationRequest",
            "patients", "encounters", "observations", "diagnostic-reports", "medication-requests"
    );

    private static final Set<String> SENSITIVE_ACTIONS = Set.of(
            "DELETE", "EXPORT", "BULK", "MERGE", "RECOVERY"
    );

    /**
     * Every condition key {@code evaluateConditions} acts on, plus {@code visibility}. A key outside
     * this set is a NON-MATCH, not a silent pass — see the unknown-key guard at the top of
     * {@code evaluateConditions}.
     *
     * <p><strong>Why this exists.</strong> {@code evaluateConditions} used to test the keys it knew
     * and fall through to {@code return true}, so a rule carrying an unimplemented key did not sit
     * inert — it evaluated on whatever remained and fired MORE broadly than it read. On a DENY that
     * is worse than useless, because DENY wins: a rule pinned {@code {"path_contains": "/regulatory/",
     * "deny_operational_lanes": true}} read as "deny the operational lanes" but evaluated as "deny
     * everything under /regulatory/", swallowing the ALLOWs beside it. Treating an unknown key as a
     * non-match makes that impossible: an ALLOW with an unknown key grants nothing (fail-closed), a
     * DENY with an unknown key denies nothing (fail-narrow).</p>
     *
     * <p>{@code visibility} is listed although it is not a MATCH condition: it drives an obligation
     * (read by {@code parseConditions().get("visibility")} here and by
     * {@code VisibilityObligationComposer}) and a rule may legitimately carry it beside match
     * conditions, so it must not read as unknown.</p>
     *
     * <p>This is the single source of truth for the runtime check.
     * {@code PolicyEngineConditionKeyVocabularyTest} fails the build if a key handled in the source
     * is missing here (the engine would wrongly reject it) or listed here without a handler (the set
     * would drift into fiction). Keep it in lockstep with the checks below.</p>
     */
    static final Set<String> RECOGNISED_CONDITION_KEYS = Set.of(
            "min_loa",
            "min_aal",
            "accepted_amr",
            "max_auth_age_seconds",
            "phishing_resistant_required",
            "allowed_facilities",
            "allowed_actor_types",
            "max_risk_score",
            "allowed_scope_refs",
            "path_contains",
            "account_assurance_required",
            "verification_grace_expiry_epoch_ms",
            "allowed_workflow_states",
            "blocked_workflow_states",
            "allowed_departments",
            "allowed_wards",
            "allowed_organisations",
            "allowed_jurisdictions",
            "requires_provider_id",
            "visibility",
            "allowed_modes",
            "blocked_modes",
            "requires_identified_clinical_access"
    );

    /**
     * Canonical roles folded into the effective role set ONLY when the duty mode's
     * clinical-data envelope is IDENTIFIED (Phase B). A management/support/regulatory
     * session's raw roleTemplateId (e.g. WARD_CHARGE_NURSE in DEPARTMENT_MANAGEMENT
     * mode) is still folded — duty facts are never hidden — but the canonical
     * clinical role every clinical policy_rule matches on is not, which is what makes
     * "management mode grants no patient access" structurally true rather than a
     * UI-only promise.
     */
    private static final Set<String> CLINICAL_CANONICAL_ROLES = Set.of(
            "CLINICIAN", "DOCTOR", "NURSE", "PHARMACIST", "SURGEON", "ANAESTHETIST"
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
    private final RoleTemplateCatalog roleTemplateCatalog;
    private final ConfidentialityPolicyPack confidentialityPack;
    private final DecisionEnvelopeSigner decisionEnvelopeSigner;
    private final MeterRegistry meterRegistry;

    /**
     * Identifies the rego corpus the shadow comparison is scored against. Divergence counts from
     * two different policy versions are not the same measurement, so the version is a metric tag.
     */
    static final String OPA_SHADOW_POLICY_VERSION = "impilo.authz/v1";

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
                        OpaDecisionClient opaDecisionClient,
                        RoleTemplateCatalog roleTemplateCatalog,
                        ConfidentialityPolicyPack confidentialityPack,
                        DecisionEnvelopeSigner decisionEnvelopeSigner,
                        MeterRegistry meterRegistry) {
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
        this.roleTemplateCatalog = roleTemplateCatalog;
        this.confidentialityPack = confidentialityPack;
        this.decisionEnvelopeSigner = decisionEnvelopeSigner;
        this.meterRegistry = meterRegistry;
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
        ShadowCapture capture = new ShadowCapture();
        AuthzResponse response = evaluateInternal(request, capture);
        // Compare against the FINAL verdict, on every terminal path. The previous call site sat on
        // the ALLOW path with javaAllow hard-coded true, so every Java DENY was invisible to the
        // comparison — the divergence rate measured only false-deny risk and said nothing about
        // false-allow risk, which is the direction that matters for a policy cut-over.
        shadowCompareOpa(request, capture, response);
        return response;
    }

    /**
     * Carries what the OPA shadow needs out of the evaluation, as the evaluation discovers it.
     * Purpose and the matched rule are resolved mid-flight and are not reachable from the response.
     */
    private static final class ShadowCapture {
        private PurposeOfUse purpose;
        private PolicyRuleEntity matchedRule;
    }

    private AuthzResponse evaluateInternal(AuthzInternalRequest request, ShadowCapture shadowCapture) {
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

        // ────────────────────────────────────────────────────────────────
        // Step 0: Constrained recovery gate (Tshepo trust doctrine, CP3).
        // A session authenticated with recovery codes (AMR recovery marker) never carries
        // ordinary workforce AAL2 authority. Only account-recovery actions are permitted;
        // everything else returns the canonical RECOVERY_REQUIRED outcome, translated
        // fail-closed onto the legacy AuthzResponse wire (DENY, never an accidental ALLOW).
        // This gate runs before escalation resolution so a recovery session can never
        // activate a visibility-escalation grant or any context/authority elevation.
        // ────────────────────────────────────────────────────────────────
        AuthzResponse recoveryRestriction = evaluateConstrainedRecovery(request, startTime);
        if (recoveryRestriction != null) {
            return recoveryRestriction;
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
        shadowCapture.purpose = purpose;
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
        shadowCapture.matchedRule = matchedAllowRule;

        // ────────────────────────────────────────────────────────────────
        // Step 4.5: Delegated / act-on-behalf authorization (L5, G-CZO-03)
        // When the actor declares acting FOR another subject (X-Subject-ID ≠ actor), require an
        // ACTIVE, in-scope, unexpired Mvumo delegation with the delegate meeting the assurance
        // floor. Delegation authorises WHO may act; the subject's clinical consent (Step 5) still
        // governs WHAT data. Conjunctive with base RBAC — never widens beyond it. Fail-closed.
        // ────────────────────────────────────────────────────────────────
        DelegationStep delegation = evaluateDelegation(request, riskScore, startTime);
        if (delegation.deny() != null) {
            return delegation.deny();
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
        // Step 4.7: Specially-protected confidentiality control
        // When the request targets the confidential clinical lane, decide whether THIS requester
        // may receive content classified SPECIALLY_PROTECTED. Default is withheld: a delegate
        // (guardian / caregiver) is refused outright, and everyone else needs either to be the
        // subject themselves or an explicit governed entitlement. Both outcomes are audited.
        // ────────────────────────────────────────────────────────────────
        ProtectedAccess protectedAccess = evaluateConfidentiality(
                request, purpose, matchedAllowRule, delegation.resolution(), riskScore, startTime);
        if (protectedAccess.deny() != null) {
            return protectedAccess.deny();
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

            // Assurance belongs to this validated token/session. Actor-wide caches can leak a
            // step-up between devices and are not accepted as authorization proof.
            if (!meetsAuthenticationRequirement(request, 2,
                    properties.getStepUpWindowSeconds(), false, List.of())) {
                return stepUpAndLog(request, riskScore, startTime);
            }
        }

        // ────────────────────────────────────────────────────────────────
        // Step 7: ALLOW with obligations
        // ────────────────────────────────────────────────────────────────
        Obligations obligations = VisibilityObligationComposer.compose(
                request, purpose, riskScore, matchedAllowRule, activeEscalation, objectMapper,
                protectedAccess.grantedCategories());
        Map<String, String> headerMutations = buildHeaderMutations(obligations, request, purpose);

        auditConfidentialGrant(request, obligations, protectedAccess.grantBasis());

        return allowAndLog(request, obligations, headerMutations, riskScore, startTime);
    }

    // ════════════════════════════════════════════════════════════════════
    // Step 3: Break-glass evaluation
    // ════════════════════════════════════════════════════════════════════

    private AuthzResponse evaluateBreakGlass(AuthzInternalRequest request, int riskScore, long startTime) {
        UUID tenantId = request.tenantId();
        String actorId = request.actorId();

        // Step 4.5 (break-glass branch) — BREAK-GLASS doctrine guard. Enforce that the requester is an
        // already-authenticated, sufficiently-verified provider acting in a facility context on a named
        // patient BEFORE the reason/step-up checks widen access. Never turns an unknown user into a
        // health worker; a disputed (revoked) provider was already denied at the top of evaluate().
        AuthzResponse guardDeny = evaluateBreakGlassAccess(request, riskScore, startTime);
        if (guardDeny != null) {
            return guardDeny;
        }

        // Require an active break-glass request (captures the mandatory reason + limited duration).
        if (!breakGlassService.hasActiveBreakGlass(tenantId, actorId)) {
            return denyAndLog(request, "NO_BREAK_GLASS_REQUEST",
                    "Break-glass purpose requires an active break-glass request. " +
                    "Submit one via POST /v1/break-glass first.",
                    riskScore, startTime);
        }

        // Require fresh AAL2 in this token/session, never an actor-wide cached challenge.
        if (!meetsAuthenticationRequirement(request, 2,
                properties.getStepUpWindowSeconds(), false, List.of())) {
            return stepUpAndLog(request, riskScore, startTime);
        }

        // Break-glass ALLOWED — with elevated obligations + full visibility envelope.
        // Break-glass DOES reach specially-protected content: it is the governed emergency route,
        // and it is the only purpose that earns that reach, having already required an active
        // break-glass request plus a completed step-up. A bare EMERGENCY purpose header does not —
        // it is an unverified claim, and granting protected access on it would be the hole that
        // makes the whole control theatre. Every such reach is audited below at ELEVATED level, and
        // the active request sits in the PENDING_REVIEW queue for mandatory retrospective supervisor
        // review (see BreakGlassService).
        Obligations obligations = VisibilityObligationComposer.compose(
                request, PurposeOfUse.BREAK_GLASS, riskScore, null, Optional.empty(), objectMapper,
                List.of("*"));
        // BREAK_GLASS is the validated purpose on this path, so pass it explicitly rather than
        // letting the header default to the client's own x-purpose-of-use.
        Map<String, String> headers = buildHeaderMutations(obligations, request, PurposeOfUse.BREAK_GLASS);

        log.warn("BREAK-GLASS ALLOW: actor={}, provider={}, facility={}, patient={}, resource={}/{}, "
                        + "correlation={} — queued for retrospective supervisor review",
                actorId, request.providerId(), request.facilityId(), breakGlassPatientRef(request),
                request.resourceType(), request.resourceId(), request.correlationId());

        auditConfidentialGrant(request, obligations, "BREAK_GLASS");

        return allowAndLog(request, obligations, headers, riskScore, startTime);
    }

    // ════════════════════════════════════════════════════════════════════
    // Step 4.5: BREAK-GLASS doctrine guard (CZO-LEAD; identity-journey break-glass doctrine)
    // ════════════════════════════════════════════════════════════════════

    /**
     * Minimum effective LoA a provider must hold to raise break-glass — "sufficiently-verified".
     * Break-glass widens the too-narrow permission of an ALREADY-verified provider; it never elevates
     * a bare or anonymous session. LOA2 = at least a verified (remotely-proofed) identity.
     */
    private static final int BREAK_GLASS_MIN_LOA = 2;

    /**
     * BREAK-GLASS access guard (doctrine: identity-journey break-glass). Break-glass is ONLY for an
     * already-authenticated, sufficiently-verified PROVIDER whose normal permission is momentarily too
     * narrow for an immediate emergency. It may NEVER transform an unknown user into a health worker,
     * and NEVER override a disputed identity. This guard enforces the preconditions the emergency
     * widening depends on — a verified provider capacity, a facility context, and a named patient —
     * before the active-request (reason) + step-up checks grant the widened access.
     *
     * <p>Composes with {@link #evaluateDelegation} / {@link #evaluateSelfTreatment}: same shape
     * (returns a DENY {@link AuthzResponse}, or {@code null} to continue), same fail-closed discipline.
     * A revoked/disputed provider privilege is already denied at the very top of {@link #evaluate}
     * (before any scoring), so a disputed identity can never reach break-glass here.</p>
     */
    private AuthzResponse evaluateBreakGlassAccess(AuthzInternalRequest request, int riskScore, long startTime) {
        // (1) Verified provider capacity — never turn an unknown user into a health worker. Break-glass
        //     requires an activated professional identity (Provider ID + PROVIDER actor type), not a
        //     citizen/anonymous session that merely declares the BREAK_GLASS purpose.
        boolean providerCapacity = "PROVIDER".equalsIgnoreCase(request.actorType())
                && request.providerId() != null && !request.providerId().isBlank();
        if (!providerCapacity) {
            return denyAndLog(request, "BREAK_GLASS_REQUIRES_VERIFIED_PROVIDER",
                    "Break-glass is only available to an authenticated provider acting in a professional "
                            + "capacity; it never grants clinical access to a non-provider.",
                    riskScore, startTime);
        }
        // (1b) Sufficiently verified — break-glass widens a verified identity, never a bare session.
        if (identityLoa(request) < BREAK_GLASS_MIN_LOA) {
            return denyAndLog(request, "BREAK_GLASS_REQUIRES_VERIFIED_PROVIDER",
                    "Break-glass requires a sufficiently-verified provider identity (LOA"
                            + BREAK_GLASS_MIN_LOA + "+).",
                    riskScore, startTime);
        }
        // (2) Facility context — the emergency happens somewhere; the widening is facility-scoped and
        //     audited against that context.
        if (request.facilityId() == null) {
            return denyAndLog(request, "BREAK_GLASS_REQUIRES_FACILITY_CONTEXT",
                    "Break-glass requires an operating facility context (X-Facility-ID).",
                    riskScore, startTime);
        }
        // (3) Patient context — break-glass is per-patient, never a blanket override. The accessed
        //     patient must be named so the enhanced audit + retrospective review bind to a subject.
        if (breakGlassPatientRef(request) == null) {
            return denyAndLog(request, "BREAK_GLASS_REQUIRES_PATIENT_CONTEXT",
                    "Break-glass requires a specific patient context (X-Subject-ID or a resource id); "
                            + "it is never a blanket access override.",
                    riskScore, startTime);
        }
        return null;
    }

    /**
     * The patient a break-glass access targets: the declared subject (X-Subject-ID) when present, else
     * the path-derived resource id. Returns {@code null} when neither names a patient.
     */
    private static String breakGlassPatientRef(AuthzInternalRequest request) {
        if (request.subjectId() != null && !request.subjectId().isBlank()) {
            return request.subjectId();
        }
        if (request.resourceId() != null && !request.resourceId().isBlank()) {
            return request.resourceId();
        }
        return null;
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
                // A DENY rule fires only when its own scope + conditions apply — exactly like
                // the ALLOW branch. Without this gating a conditional DENY (e.g. a path_contains
                // rule that should block only /internal/v1/assets) fired on EVERY request that
                // matched the coarse role/action, over-denying the whole role/tenant. Honouring
                // the conditions here can only REDUCE denial, never introduce a new one.
                if (rule.isFacilityScope() && request.facilityId() == null) {
                    continue;
                }
                if (rule.isWorkspaceScope() && request.workspaceId() == null) {
                    continue;
                }
                if (!evaluateConditions(rule.getConditions(), request)) {
                    continue;
                }
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
     *
     * <p>A key outside {@link #RECOGNISED_CONDITION_KEYS} is a NON-MATCH with a WARN, not a silent
     * pass — see the unknown-key guard below and the constant's javadoc for why.</p>
     */
    @SuppressWarnings("unchecked")
    private boolean evaluateConditions(String conditionsJson, AuthzInternalRequest request) {
        if (conditionsJson == null || conditionsJson.isBlank()) {
            return true; // No conditions = unconditional match
        }

        try {
            Map<String, Object> conditions = objectMapper.readValue(conditionsJson,
                    new TypeReference<>() {});

            // Unknown-key guard — the root fix for "differently active". A key the engine does not
            // implement is a NON-MATCH, never the old silent fall-through to `return true`. Failing
            // the whole condition (rather than ignoring the stray key) is what keeps a rule from
            // firing more broadly than it reads: an ALLOW with an unknown key grants nothing
            // (fail-closed), a DENY with an unknown key denies nothing (fail-narrow, so it cannot
            // swallow the ALLOWs beside it). Loud, because PolicyConditionKeyContractTest means an
            // ACTIVE rule should never reach here with an unknown key; if one does, a migration
            // shipped past the guard and an operator needs to see it.
            List<String> unknownKeys = conditions.keySet().stream()
                    .filter(k -> !RECOGNISED_CONDITION_KEYS.contains(k))
                    .sorted()
                    .toList();
            if (!unknownKeys.isEmpty()) {
                log.warn("Policy rule carries unrecognised condition key(s) {} — the rule is treated "
                        + "as a NON-MATCH (fail-closed for ALLOW, fail-narrow for DENY), not silently "
                        + "ignored. Implement the key in evaluateConditions or repair the seed. "
                        + "conditions={}", unknownKeys, conditionsJson);
                return false;
            }

            // min_loa check — keyed on the EFFECTIVE LoA (the stronger of the session's
            // ACR-derived login level and the actor's current identity-assurance level
            // propagated via X-Assurance-Level). This is what makes a self-service
            // verification upgrade actually change what policy sees (closes G-CZO-01).
            if (conditions.containsKey("min_loa")) {
                int minLoa = ((Number) conditions.get("min_loa")).intValue();
                int identityLoa = identityLoa(request);
                if (identityLoa < minLoa) {
                    log.debug("Condition failed: min_loa={} but identityLoa={}", minLoa, identityLoa);
                    return false;
                }
            }

            int minAal = conditions.get("min_aal") instanceof Number n ? n.intValue() : 0;
            int maxAuthAge = conditions.get("max_auth_age_seconds") instanceof Number n
                    ? n.intValue() : 0;
            boolean phishingResistant = Boolean.TRUE.equals(conditions.get("phishing_resistant_required"));
            List<String> acceptedAmr = conditions.get("accepted_amr") instanceof List<?> values
                    ? values.stream().filter(String.class::isInstance).map(String.class::cast).toList()
                    : List.of();
            if ((minAal > 0 || maxAuthAge > 0 || phishingResistant || !acceptedAmr.isEmpty())
                    && !meetsAuthenticationRequirement(request, minAal, maxAuthAge,
                    phishingResistant, acceptedAmr)) {
                log.debug("Authentication assurance condition failed: requiredAal={}, maxAge={}, "
                                + "phishingResistant={}, acceptedAmr={}, actual={}",
                        minAal, maxAuthAge, phishingResistant, acceptedAmr,
                        request.authenticationAssurance());
                return false;
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

            // ── First-class operational dimensions (Health OS §7 + §10 + workflow-state) ──
            // department / ward / organisation are sourced from the WORK_CONTEXT duty token when
            // it is usable (authoritative), falling back to the client header otherwise.

            // workflow-state gates (X-Workflow-State): allow/blocked lists on the transaction state.
            if (conditions.containsKey("allowed_workflow_states")) {
                List<String> allowed = (List<String>) conditions.get("allowed_workflow_states");
                String state = request.workflowContext();
                if (state == null || allowed.stream().noneMatch(state::equalsIgnoreCase)) {
                    log.debug("Condition failed: workflow-state '{}' not in allowed {}", state, allowed);
                    return false;
                }
            }
            if (conditions.containsKey("blocked_workflow_states")) {
                List<String> blocked = (List<String>) conditions.get("blocked_workflow_states");
                String state = request.workflowContext();
                if (state != null && blocked.stream().anyMatch(state::equalsIgnoreCase)) {
                    log.debug("Condition failed: workflow-state '{}' is blocked {}", state, blocked);
                    return false;
                }
            }

            // department / ward / organisation membership (duty-token-authoritative).
            if (conditions.containsKey("allowed_departments")) {
                List<String> allowed = (List<String>) conditions.get("allowed_departments");
                String dept = effectiveDepartment(request);
                if (dept == null || allowed.stream().noneMatch(dept::equalsIgnoreCase)) {
                    log.debug("Condition failed: department '{}' not in allowed {}", dept, allowed);
                    return false;
                }
            }
            if (conditions.containsKey("allowed_wards")) {
                List<String> allowed = (List<String>) conditions.get("allowed_wards");
                String ward = effectiveWard(request);
                if (ward == null || allowed.stream().noneMatch(ward::equalsIgnoreCase)) {
                    log.debug("Condition failed: ward '{}' not in allowed {}", ward, allowed);
                    return false;
                }
            }
            if (conditions.containsKey("allowed_organisations")) {
                List<String> allowed = (List<String>) conditions.get("allowed_organisations");
                String org = effectiveOrganisation(request);
                if (org == null || allowed.stream().noneMatch(org::equalsIgnoreCase)) {
                    log.debug("Condition failed: organisation '{}' not in allowed {}", org, allowed);
                    return false;
                }
            }

            // Jurisdiction: a regulator's authority is bounded by WHERE as well as by which
            // organisation. An inspector appointed for one province must not act nationally
            // merely because their council is national, so this is its own dimension rather
            // than something inferred from the organisation.
            //
            // NATIONAL is a wildcard on the DUTY side only: a nationally-appointed officer
            // satisfies a rule scoped to any province. It is deliberately not a wildcard on the
            // rule side — a rule listing NATIONAL means national duty, not "anyone anywhere".
            if (conditions.containsKey("allowed_jurisdictions")) {
                List<String> allowed = (List<String>) conditions.get("allowed_jurisdictions");
                String jurisdiction = effectiveJurisdiction(request);
                boolean satisfied = jurisdiction != null
                        && (JURISDICTION_NATIONAL.equalsIgnoreCase(jurisdiction)
                            || allowed.stream().anyMatch(jurisdiction::equalsIgnoreCase));
                if (!satisfied) {
                    log.debug("Condition failed: jurisdiction '{}' not in allowed {}", jurisdiction, allowed);
                    return false;
                }
            }

            // attached role-id: a provider public id must be present (duty token or header) for
            // provider-only actions, beyond the negative revocation/self-treatment checks.
            if (Boolean.TRUE.equals(conditions.get("requires_provider_id"))) {
                String provider = effectiveProviderId(request);
                if (provider == null || provider.isBlank()) {
                    log.debug("Condition failed: requires_provider_id but none present for actor {}", request.actorId());
                    return false;
                }
            }

            // Mode (Phase B): fail-closed on a null mode — a rule that gates on
            // allowed_modes must not match a request with no usable duty context.
            if (conditions.containsKey("allowed_modes")) {
                List<String> allowed = (List<String>) conditions.get("allowed_modes");
                String workMode = effectiveWorkMode(request);
                if (workMode == null || allowed.stream().noneMatch(workMode::equalsIgnoreCase)) {
                    log.debug("Condition failed: work mode '{}' not in allowed {}", workMode, allowed);
                    return false;
                }
            }
            // blocked_modes is the DENY-side complement: a null mode is NOT blocked
            // (a non-duty S2S/citizen call carries no mode and must be unaffected).
            if (conditions.containsKey("blocked_modes")) {
                List<String> blocked = (List<String>) conditions.get("blocked_modes");
                String workMode = effectiveWorkMode(request);
                if (workMode != null && blocked.stream().anyMatch(workMode::equalsIgnoreCase)) {
                    log.debug("Condition failed: work mode '{}' is in blocked {}", workMode, blocked);
                    return false;
                }
            }
            if (Boolean.TRUE.equals(conditions.get("requires_identified_clinical_access"))) {
                DutyContext dc = request.dutyContext();
                if (dc == null || !dc.allowsIdentifiedClinicalRead()) {
                    log.debug("Condition failed: requires_identified_clinical_access but duty mode is {}",
                            dc != null ? dc.clinicalDataAccess() : "absent");
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
    private int identityLoa(AuthzInternalRequest request) {
        return parseAssuranceLoa(request.assuranceLevel());
    }

    private boolean meetsAuthenticationRequirement(AuthzInternalRequest request, int minAal,
                                                     int maxAgeSeconds, boolean phishingResistant,
                                                     List<String> acceptedMethods) {
        AuthenticationAssurance assurance = request.authenticationAssurance() == null
                ? AuthenticationAssurance.none() : request.authenticationAssurance();
        if (assurance.aal() < minAal) return false;
        if (phishingResistant && !assurance.phishingResistant()) return false;
        if (maxAgeSeconds > 0 && !assurance.isFresh(maxAgeSeconds, Instant.now())) return false;
        if (acceptedMethods != null && !acceptedMethods.isEmpty()
                && assurance.methods().stream().noneMatch(acceptedMethods::contains)) return false;
        return true;
    }

    /**
     * Step 0: constrained recovery gate. Returns a decision when the current session is a
     * recovery-code session attempting anything outside the recovery allowlist, else null.
     *
     * <p>The canonical decision is {@link TrustChallengeDecision#RECOVERY_REQUIRED}; the legacy
     * wire receives it through {@link AuthzResponseChallengeAdapter#toLegacySafe} which maps it
     * to a fail-closed DENY ({@code UNREPRESENTABLE_RECOVERY_REQUIRED}) rather than throwing.</p>
     */
    private AuthzResponse evaluateConstrainedRecovery(AuthzInternalRequest request, long startTime) {
        zw.gov.mohcc.impilo.tshepo.contracts.v1.AuthenticationAssurance canonical =
                LegacyAuthenticationAssuranceAdapter.toCanonical(request.authenticationAssurance());
        if (!canonical.isConstrainedRecovery()) {
            return null;
        }
        if (isRecoveryPermittedAction(request.action(), request.resourceType())) {
            // Permitted recovery actions still flow through every later step (RBAC, consent,
            // step-up) — the allowlist only exempts them from the blanket recovery restriction.
            return null;
        }
        TrustChallengeOutcome outcome = TrustChallengeOutcome.of(
                TrustChallengeDecision.RECOVERY_REQUIRED,
                "CONSTRAINED_RECOVERY_SESSION",
                "trust.recovery.enroll_required",
                "ENROLL_REPLACEMENT_FACTOR",
                2,
                List.of("totp", "webauthn"),
                List.of(),
                null, null, null,
                UUID.randomUUID().toString(),
                null, null, null);

        persistDecision(request, "RECOVERY_REQUIRED", 0,
                "CONSTRAINED_RECOVERY_SESSION", null, null, startTime);
        auditPublisher.queueAuditEvent(request, "RECOVERY_REQUIRED", 0,
                "CONSTRAINED_RECOVERY_SESSION");
        log.info("RECOVERY_REQUIRED: actor={}, action={}, resource={}, correlation={}",
                request.actorId(), request.action(), request.resourceType(),
                request.correlationId());

        return AuthzResponseChallengeAdapter.toLegacySafe(outcome);
    }

    /** Matches action/resource against the configured "ACTION:RESOURCE_TYPE" recovery allowlist. */
    private boolean isRecoveryPermittedAction(String action, String resourceType) {
        String normalisedAction = action == null ? "" : action.trim().toUpperCase(Locale.ROOT);
        String normalisedResource = resourceType == null ? "" : resourceType.trim().toUpperCase(Locale.ROOT);
        for (String entry : properties.getRecoveryPermittedActions()) {
            if (entry == null) continue;
            String[] parts = entry.trim().toUpperCase(Locale.ROOT).split(":", 2);
            String allowedAction = parts[0].trim();
            String allowedResource = parts.length > 1 ? parts[1].trim() : "*";
            boolean actionMatches = allowedAction.equals("*") || allowedAction.equals(normalisedAction);
            boolean resourceMatches = allowedResource.equals("*") || allowedResource.equals(normalisedResource);
            if (actionMatches && resourceMatches) {
                return true;
            }
        }
        return false;
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
     * The outcome of Step 4.5: an optional DENY, plus the resolved delegation when the request IS a
     * delegated act. A non-null {@code resolution} is the trust plane's answer to "is someone acting
     * on another person's behalf here, and in what relationship?" — which Step 4.7 needs, and which
     * used to be resolved and then thrown away.
     */
    private record DelegationStep(AuthzResponse deny, DelegationResolution resolution) {
        static DelegationStep notDelegated() {
            return new DelegationStep(null, null);
        }

        static DelegationStep deny(AuthzResponse deny) {
            return new DelegationStep(deny, null);
        }

        static DelegationStep authorised(DelegationResolution resolution) {
            return new DelegationStep(null, resolution);
        }
    }

    /**
     * Returns a DENY when the actor declares acting for another subject ({@code X-Subject-ID} ≠
     * actor) but lacks an active, in-scope, sufficiently-assured delegation; otherwise returns the
     * resolved delegation (or {@link DelegationStep#notDelegated()} when the request is not a
     * delegated act at all). Fail-closed: a resolution error denies. Delegation authorises WHO may
     * act; the subject's clinical consent (Step 5) still governs WHAT data.
     */
    private DelegationStep evaluateDelegation(AuthzInternalRequest request, int riskScore, long startTime) {
        String subjectId = request.subjectId();
        String actorId = request.actorId();
        if (subjectId == null || subjectId.isBlank() || subjectId.equals(actorId)) {
            return DelegationStep.notDelegated(); // not acting on behalf of a different subject
        }
        DelegationResolution res;
        try {
            res = delegationClient.resolve(request.tenantId(), actorId, subjectId);
        } catch (Exception e) {
            log.warn("Delegation resolution failed for actor={} subject={}: {}", actorId, subjectId, e.getMessage());
            return DelegationStep.deny(denyAndLog(request, "DELEGATION_UNAVAILABLE",
                    "Delegation could not be verified", riskScore, startTime));
        }
        if (res == null || !res.active()) {
            return DelegationStep.deny(denyAndLog(request, "DELEGATION_NOT_ACTIVE",
                    "No active delegation authorising this actor to act for the subject", riskScore, startTime));
        }
        if (identityLoa(request) < res.assuranceFloor()) {
            return DelegationStep.deny(denyAndLog(request, "DELEGATION_ASSURANCE_TOO_LOW",
                    "Delegate assurance below the delegation floor", riskScore, startTime));
        }
        if (!scopeAllows(res.scope(), request.resourceType())) {
            return DelegationStep.deny(denyAndLog(request, "DELEGATION_OUT_OF_SCOPE",
                    "Requested resource is outside the delegation scope", riskScore, startTime));
        }
        // Delegation authorises the actor; continue to confidentiality (4.7) and consent (5).
        return DelegationStep.authorised(res);
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
    // Step 4.7: Specially-protected confidentiality control
    // ════════════════════════════════════════════════════════════════════

    /**
     * The confidentiality verdict: an optional DENY, the confidential categories granted to this
     * requester, and the basis (for audit).
     */
    private record ProtectedAccess(AuthzResponse deny, List<String> grantedCategories, String grantBasis) {
        static ProtectedAccess notApplicable() {
            return new ProtectedAccess(null, List.of(), null);
        }

        static ProtectedAccess deny(AuthzResponse deny) {
            return new ProtectedAccess(deny, List.of(), null);
        }

        static ProtectedAccess granted(List<String> categories, String basis) {
            return new ProtectedAccess(null, categories, basis);
        }

        /** Refused, but in SHADOW mode: no denial, and no grant either. */
        static ProtectedAccess shadowRefused() {
            return new ProtectedAccess(null, List.of(), null);
        }
    }

    /** The whole-set grant. Used where enumerating categories would only add a chance to miss one. */
    private static final List<String> ALL_CATEGORIES = List.of("*");

    /**
     * Decide which categories of {@code SPECIALLY_PROTECTED} content this requester may receive.
     *
     * <p>Only the confidential lane is in scope — ordinary care is untouched, because a
     * confidentiality control that narrows everything gets routed around. Within that lane, in
     * order:</p>
     * <ol>
     *   <li><strong>Emergency and break-glass waive, always.</strong> Over-restricting kills people
     *       too: a teenager arriving unconscious whose HIV status or medication explains the
     *       presentation must not be invisible. Mirrors {@code ClinicalAccessGuard} in pct-service so
     *       the two layers behave identically, and is loudly audited — this is detection, not
     *       prevention, and it depends on the audit actually being reviewed.</li>
     *   <li><strong>A delegated act is refused.</strong> A caregiver acting for a child is a
     *       different requester from the child, and separating guardian access from the confidential
     *       adolescent record is the point. No policy rule widens this.</li>
     *   <li><strong>The subject themselves is granted every category</strong> — it is their record.</li>
     *   <li><strong>Otherwise the governed rule grant applies</strong>, narrowed to what the ratified
     *       policy pack currently permits. A clinical role alone grants nothing.</li>
     * </ol>
     *
     * <p><strong>Mode-gated.</strong> In {@code SHADOW} (the default) this evaluates and audits but
     * never denies, because the content that decides behaviour is an engineering seed pending MoHCC
     * ratification. {@code ENFORCE} additionally requires a ratified, effective pack; without one the
     * control stays inert and says so at ERROR rather than silently — being quietly ineffective is
     * the exact failure this seam exists to prevent.</p>
     */
    private ProtectedAccess evaluateConfidentiality(AuthzInternalRequest request,
                                                    PurposeOfUse purpose,
                                                    PolicyRuleEntity matchedAllowRule,
                                                    DelegationResolution delegation,
                                                    int riskScore, long startTime) {
        String mode = properties.getConfidentialityMode();
        if (mode == null || "OFF".equalsIgnoreCase(mode)) {
            return ProtectedAccess.notApplicable();
        }
        if (!ResourceSensitivityClassifier.isSpeciallyProtected(request.resourceType())) {
            return ProtectedAccess.notApplicable();
        }

        boolean enforcing = "ENFORCE".equalsIgnoreCase(mode);
        if (enforcing && !confidentialityPack.isEffective()) {
            // Asked to enforce with no ratified content. Refusing to enforce is the safe choice —
            // enforcing on an engineering seed could hide records from the clinicians treating the
            // person — but it must never be quiet, or an operator believes a control is live when it
            // is not. That belief is precisely the false assurance we are eliminating.
            log.error("CONFIDENTIALITY mode=ENFORCE but the policy pack is {} — the control is INERT. "
                            + "Ratify and activate {} before relying on it.",
                    confidentialityPack.stateLabel(), confidentialityPack.packId());
            emitConfidentialityEvent("CONFIDENTIALITY_ENFORCE_UNAVAILABLE", request, packStatePayload(request));
            enforcing = false;
        }

        // ── 1. Emergency / break-glass waiver ────────────────────────────────
        if (purpose == PurposeOfUse.EMERGENCY || purpose == PurposeOfUse.BREAK_GLASS) {
            log.warn("CONFIDENTIALITY WAIVED (purpose={}): actor={} reaching specially-protected "
                            + "content for subject={} resource={}/{} — emergency override. correlation={}",
                    purpose, request.actorId(), request.subjectId(),
                    request.resourceType(), request.resourceId(), request.correlationId());
            return ProtectedAccess.granted(ALL_CATEGORIES, "EMERGENCY_WAIVER:" + purpose.name());
        }

        // ── 2. Delegated act — refused ───────────────────────────────────────
        if (delegation != null) {
            String relationship = delegation.relationshipType() == null
                    ? "UNSPECIFIED" : delegation.relationshipType().toUpperCase(Locale.ROOT);
            auditConfidentialRefusal(request, "PROTECTED_RECORD_DELEGATE_DENIED", relationship, enforcing);
            if (!enforcing) {
                return ProtectedAccess.shadowRefused();
            }
            return ProtectedAccess.deny(denyAndLog(request, "PROTECTED_RECORD_DELEGATE_DENIED",
                    "This record is specially protected and cannot be opened on another person's "
                            + "behalf. The person it belongs to can share it themselves.",
                    riskScore, startTime));
        }

        // ── 3. The subject themselves ────────────────────────────────────────
        String subjectId = request.subjectId();
        if (subjectId != null && !subjectId.isBlank() && subjectId.equals(request.actorId())) {
            return ProtectedAccess.granted(ALL_CATEGORIES, "SUBJECT_SELF");
        }

        // ── 4. Governed rule grant, narrowed by the ratified pack ────────────
        List<String> requested = ruleRequestedCategories(matchedAllowRule);
        Set<String> permitted = confidentialityPack.retainGrantable(requested);
        if (!permitted.isEmpty()) {
            return ProtectedAccess.granted(List.copyOf(permitted), "POLICY_RULE:"
                    + (matchedAllowRule.getName() != null ? matchedAllowRule.getName() : "unnamed"));
        }

        String reason = requested.isEmpty()
                ? "PROTECTED_RECORD_NOT_ENTITLED"
                : "PROTECTED_RECORD_PACK_INERT";
        auditConfidentialRefusal(request, reason, null, enforcing);
        if (!enforcing) {
            return ProtectedAccess.shadowRefused();
        }
        return ProtectedAccess.deny(denyAndLog(request, reason,
                requested.isEmpty()
                        ? "Access to specially-protected data requires an explicit entitlement for "
                                + "this actor, purpose and context."
                        : "The confidentiality policy pack is not ratified, so no protected-content "
                                + "entitlement can be granted.",
                riskScore, startTime));
    }

    /**
     * The confidential categories the matched ALLOW rule asks for. This is the governance channel:
     * entitlement is seeded and reviewed as policy, never compiled in. A legacy rule that only says
     * {@code visibilityTier} grants nothing — confidentiality is a category obligation, not a tier.
     */
    private List<String> ruleRequestedCategories(PolicyRuleEntity matchedAllowRule) {
        if (matchedAllowRule == null) {
            return List.of();
        }
        Object visibility = parseConditions(matchedAllowRule).get("visibility");
        if (!(visibility instanceof Map<?, ?> overlay)) {
            return List.of();
        }
        if (!(overlay.get("confidentialCategories") instanceof List<?> raw)) {
            return List.of();
        }
        List<String> categories = new ArrayList<>();
        for (Object o : raw) {
            if (o != null && !o.toString().isBlank()) {
                categories.add(o.toString());
            }
        }
        return categories;
    }

    private Map<String, Object> packStatePayload(AuthzInternalRequest request) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("packId", confidentialityPack.packId());
        payload.put("packVersion", confidentialityPack.version());
        payload.put("packState", confidentialityPack.stateLabel());
        payload.put("actorId", request.actorId());
        payload.put("resourceType", request.resourceType());
        payload.put("correlationId", request.correlationId() != null ? request.correlationId().toString() : null);
        return payload;
    }

    /**
     * Audit a refusal on the confidential lane. Separate from the standard decision-log DENY so a
     * reviewer can read the confidentiality control's own record without filtering the whole authz
     * stream — a control nobody can review is not a control. {@code enforced=false} marks a SHADOW
     * observation: what the control WOULD have refused had it been live.
     */
    private void auditConfidentialRefusal(AuthzInternalRequest request, String reason,
                                          String relationship, boolean enforced) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("reason", reason);
        payload.put("enforced", enforced);
        payload.put("mode", properties.getConfidentialityMode());
        payload.put("packState", confidentialityPack.stateLabel());
        payload.put("actorId", request.actorId());
        payload.put("actorType", request.actorType());
        payload.put("subjectId", request.subjectId());
        payload.put("onBehalfRelationship", relationship);
        payload.put("resourceType", request.resourceType());
        payload.put("resourceId", request.resourceId());
        payload.put("purposeOfUse", request.purposeOfUse());
        payload.put("facilityId", request.facilityId() != null ? request.facilityId().toString() : null);
        payload.put("tenantId", request.tenantId() != null ? request.tenantId().toString() : null);
        payload.put("correlationId", request.correlationId() != null ? request.correlationId().toString() : null);
        emitConfidentialityEvent("CONFIDENTIAL_ACCESS_REFUSED", request, payload);
    }

    /**
     * Audit an access that actually reached specially-protected content. Driven off the COMPOSED
     * obligations rather than the decision, so every route that can reach protected data —
     * self-access, a governed rule, the emergency waiver, a workflow escalation grant — lands in the
     * same reviewable stream, including any future one nobody remembered to instrument.
     */
    private void auditConfidentialGrant(AuthzInternalRequest request, Obligations obligations, String basis) {
        if (obligations == null || obligations.visibilityProfile() == null) {
            return;
        }
        VisibilityProfile vp = obligations.visibilityProfile();
        if (!vp.allowsAnyConfidentialCategory()) {
            return;
        }
        Map<String, Object> payload = new HashMap<>();
        payload.put("grantBasis", basis);
        payload.put("mode", properties.getConfidentialityMode());
        payload.put("packState", confidentialityPack.stateLabel());
        payload.put("actorId", request.actorId());
        payload.put("actorType", request.actorType());
        payload.put("subjectId", request.subjectId());
        payload.put("resourceType", request.resourceType());
        payload.put("resourceId", request.resourceId());
        payload.put("purposeOfUse", request.purposeOfUse());
        payload.put("confidentialCategories", vp.confidentialCategories());
        payload.put("facilityId", request.facilityId() != null ? request.facilityId().toString() : null);
        payload.put("tenantId", request.tenantId() != null ? request.tenantId().toString() : null);
        payload.put("correlationId", request.correlationId() != null ? request.correlationId().toString() : null);
        emitConfidentialityEvent("CONFIDENTIAL_ACCESS_GRANTED", request, payload);
    }

    private void emitConfidentialityEvent(String eventType, AuthzInternalRequest request,
                                          Map<String, Object> payload) {
        try {
            auditPublisher.queueGovernanceEvent(eventType, request.actorId(), payload);
        } catch (Exception e) {
            log.warn("Failed to emit confidentiality event {}: {}", eventType, e.getMessage());
        }
        log.info("CONFIDENTIALITY {}: actor={} subject={} resource={}/{} correlation={}",
                eventType, request.actorId(), request.subjectId(),
                request.resourceType(), request.resourceId(), request.correlationId());
    }

    // ════════════════════════════════════════════════════════════════════
    // Phase 3: OPA shadow comparison (strangler — Java authoritative)
    // ════════════════════════════════════════════════════════════════════

    /**
     * In SHADOW mode, evaluate the OPA gate decision on a prepared input and record how it compares
     * to the Java verdict. OPA is never authoritative here and every error is swallowed, so a policy
     * or sidecar fault can never change an authorization outcome. OFF ⇒ no-op.
     *
     * <p>Runs on every terminal path, from {@link #evaluate}, against the final response. Comparing
     * only ALLOW (as this did previously, with {@code javaAllow} hard-coded {@code true}) measures
     * false-deny risk alone and is silent about false-allow risk — the direction that decides
     * whether a cut-over is safe.</p>
     *
     * <p>{@code STEP_UP_REQUIRED} is not folded into "deny": the policy has no risk/step-up
     * dimension, so it is counted in its own bucket rather than being scored as agreement or
     * divergence against a question the policy was never asked.</p>
     */
    private void shadowCompareOpa(AuthzInternalRequest request, ShadowCapture capture,
                                  AuthzResponse response) {
        String mode = properties.getOpaMode();
        if (mode == null || "OFF".equalsIgnoreCase(mode)) {
            return;
        }
        long started = System.nanoTime();
        try {
            Map<String, Object> conditions = parseConditions(capture.matchedRule);
            Integer minLoa = conditions.get("min_loa") instanceof Number n ? n.intValue() : null;
            Integer minAal = conditions.get("min_aal") instanceof Number n ? n.intValue() : null;
            Integer maxAuthAge = conditions.get("max_auth_age_seconds") instanceof Number n ? n.intValue() : null;
            boolean phishingResistant = Boolean.TRUE.equals(conditions.get("phishing_resistant_required"));
            boolean accountAssurance = Boolean.TRUE.equals(conditions.get("account_assurance_required"));

            Map<String, Object> input = OpaShadowInputMapper.build(
                    request, capture.purpose, minLoa, minAal, maxAuthAge, phishingResistant,
                    accountAssurance, identityLoa(request));

            OpaDecision opa = opaDecisionClient.decide(input);
            long micros = (System.nanoTime() - started) / 1_000L;
            if (opa == null) {
                recordShadowOutcome("no_signal", "none", micros);
                return;
            }

            Verdict verdict = response.verdict();
            if (verdict == Verdict.STEP_UP_REQUIRED) {
                recordShadowOutcome("step_up_not_comparable",
                        OpaShadowInputMapper.metricReason(opa.denyReasons()), micros);
                return;
            }

            boolean javaAllow = verdict == Verdict.ALLOW;
            if (opa.allow() == javaAllow) {
                recordShadowOutcome("agree", OpaShadowInputMapper.metricReason(opa.denyReasons()), micros);
                return;
            }

            // Not every disagreement is a policy disagreement. Attribute it, because a cut-over
            // decision rests on the REAL count alone: NO_RULE_COVERAGE is OPA not implementing the
            // rule class at all (its own header says the DB-rule RBAC/ABAC is out of scope), and
            // UNMAPPABLE is a field this service cannot supply. Folding either into "divergence"
            // produces a permanent near-100% rate that buries the disagreements that matter.
            OpaShadowInputMapper.DivergenceKind kind =
                    OpaShadowInputMapper.classify(opa.allow(), response.errorCode(), opa.denyReasons());
            String outcome = switch (kind) {
                case REAL -> "divergence";
                case NO_RULE_COVERAGE -> "divergence_no_rule_coverage";
                case UNMAPPABLE -> "divergence_unmappable";
            };
            recordShadowOutcome(outcome, OpaShadowInputMapper.metricReason(opa.denyReasons()), micros);

            // Only a REAL divergence is a warning; the other two are expected states of a
            // deliberately partial strangler and must not train anyone to ignore this log line.
            String message = "OPA-SHADOW {}: java.verdict={} java.reason={} opa.allow={} opa.reasons={} "
                    + "actor={} purpose={} resource={} correlation={}";
            Object[] args = {outcome, verdict, response.errorCode(), opa.allow(), opa.denyReasons(),
                    request.actorId(), capture.purpose != null ? capture.purpose.name() : null,
                    request.resourceType(), request.correlationId()};
            if (kind == OpaShadowInputMapper.DivergenceKind.REAL) {
                log.warn(message, args);
            } else {
                log.info(message, args);
            }
        } catch (Exception e) {
            recordShadowOutcome("error", "none", (System.nanoTime() - started) / 1_000L);
            log.debug("OPA-SHADOW comparison skipped: {}", e.getMessage());
        }
    }

    /**
     * Emit (AUTHORITATIVE) or measure (SHADOW) the validated operating context.
     *
     * <p>In SHADOW nothing is emitted and nothing changes downstream; the divergence between what
     * the client claimed and what the duty token validates is counted instead. That count is the
     * evidence for whether AUTHORITATIVE is safe — flipping it without knowing the rate would take
     * facility scope away from every request whose duty token happens not to carry one.</p>
     */
    private void emitValidatedContext(Map<String, String> headers, AuthzInternalRequest request,
                                      PurposeOfUse purpose) {
        ContextHeaderAuthority.Mode mode;
        try {
            mode = ContextHeaderAuthority.Mode.parse(properties.getContextHeaderMode());
        } catch (IllegalArgumentException e) {
            // Startup validation rejects a bad value, so reaching here means it changed at runtime.
            log.error("Invalid context-header-mode, treating as PASSTHROUGH: {}", e.getMessage());
            return;
        }
        if (mode == ContextHeaderAuthority.Mode.PASSTHROUGH) {
            return;
        }

        if (mode == ContextHeaderAuthority.Mode.AUTHORITATIVE) {
            headers.putAll(ContextHeaderAuthority.validatedContext(request, purpose));
        }

        if (meterRegistry == null) {
            return;
        }
        for (ContextHeaderAuthority.ContextDivergence d : ContextHeaderAuthority.compare(request, purpose)) {
            String outcome;
            if (d.differs()) {
                outcome = "differs";
            } else if (d.clientSupplied() && !d.validatedPresent()) {
                // The client claimed a context the trust plane cannot validate. Under
                // AUTHORITATIVE this claim is dropped -- the case that matters most.
                outcome = "client_only";
            } else if (!d.clientSupplied() && d.validatedPresent()) {
                outcome = "validated_only";
            } else {
                outcome = "match";
            }
            // Header NAME only. The values are facility and programme identifiers -- real
            // operational data that must not become a metric tag.
            meterRegistry.counter("tshepo.authz.context.header",
                    "header", d.header(), "outcome", outcome, "mode", mode.name()).increment();
        }
    }

    /**
     * Bounded-cardinality shadow metrics. Both tag values come from closed vocabularies — the
     * outcome set below and the policy's own deny-reason names — so no request-derived string can
     * grow the series.
     */
    private void recordShadowOutcome(String outcome, String reason, long micros) {
        if (meterRegistry == null) {
            return;
        }
        meterRegistry.counter("tshepo.authz.opa.shadow",
                "outcome", outcome,
                "reason", reason,
                "policy_version", OPA_SHADOW_POLICY_VERSION).increment();
        meterRegistry.timer("tshepo.authz.opa.shadow.latency", "outcome", outcome)
                .record(micros, java.util.concurrent.TimeUnit.MICROSECONDS);
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
        // The confidential lane is clinical by construction, whatever its route segments are
        // named — it must not slip past consent (or the Step 4.6 self-treatment block) merely
        // because its resource type is absent from the literal list above.
        return CLINICAL_RESOURCE_TYPES.contains(resourceType)
                || ResourceSensitivityClassifier.isSpeciallyProtected(resourceType);
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
        return buildHeaderMutations(obligations, request, null);
    }

    private Map<String, String> buildHeaderMutations(Obligations obligations,
                                                      AuthzInternalRequest request,
                                                      PurposeOfUse purpose) {
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
        AuthenticationAssurance authentication = request.authenticationAssurance();
        if (authentication != null && authentication.aal() > 0) {
            headers.put(TrustHeaders.AUTHENTICATION_AAL, Integer.toString(authentication.aal()));
            headers.put(TrustHeaders.AUTHENTICATION_AMR, String.join(",", authentication.methods()));
            if (authentication.authenticationTime() != null) {
                headers.put(TrustHeaders.AUTHENTICATION_TIME, authentication.authenticationTime().toString());
            }
            if (authentication.stepUpTime() != null) {
                headers.put(TrustHeaders.AUTHENTICATION_STEP_UP_TIME, authentication.stepUpTime().toString());
            }
            headers.put(TrustHeaders.AUTHENTICATION_PHISHING_RESISTANT,
                    Boolean.toString(authentication.phishingResistant()));
            if (authentication.sessionId() != null) {
                headers.put(TrustHeaders.AUTHENTICATION_SESSION_ID, authentication.sessionId());
            }
            if (authentication.flowId() != null) {
                headers.put(TrustHeaders.AUTHENTICATION_FLOW_ID, authentication.flowId());
            }
        }

        // Operating context, regenerated from the introspected work-context token. Until this
        // existed the PDP regenerated identity but not context, so x-facility-id and its siblings
        // reached every service exactly as the browser set them — and they could not simply be
        // stripped at Envoy, because stripping without a regenerator deletes the context the
        // estate runs on. Staged: emits only in AUTHORITATIVE, measures in SHADOW.
        emitValidatedContext(headers, request, purpose);

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

            String obligationsJson;
            try {
                obligationsJson = objectMapper.writeValueAsString(obligations);
            } catch (JsonProcessingException e) {
                obligationsJson = "{}";
            }
            headers.put(TrustHeaders.OBLIGATIONS, obligationsJson);

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

        // The envelope is minted here rather than in AuthorizeController because Envoy reaches
        // the PDP over HTTP in the deployed config and over gRPC in local dev. Both transports
        // forward whatever this map contains, so signing here covers both; signing in the HTTP
        // controller would have left the gRPC path issuing unsigned allows.
        //
        // Digest what is actually in the header, not what it was serialized from: if
        // serialization failed the header says "{}" and the envelope must commit to "{}".
        decisionEnvelopeSigner.sign(
                        request.tenantId(),
                        request.actorId(),
                        request.actorType(),
                        request.correlationId(),
                        request.method(),
                        request.path(),
                        headers.get(TrustHeaders.OBLIGATIONS))
                .ifPresent(envelope -> headers.put(TrustHeaders.DECISION_ENVELOPE, envelope));

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
        StepUpRequirement requirement = new StepUpRequirement(
                2, methods, properties.getStepUpWindowSeconds(), false, UUID.randomUUID().toString());

        persistDecision(request, "STEP_UP_REQUIRED", riskScore, null, methodsStr, null, startTime);
        auditPublisher.queueAuditEvent(request, "STEP_UP_REQUIRED", riskScore, null);

        log.info("STEP_UP: actor={}, methods={}, correlation={}",
                request.actorId(), methods, request.correlationId());

        return AuthzResponse.stepUp(requirement, riskScore);
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

        // Matched. Fold the duty role — the raw roleTemplateId AND any canonical role(s) it maps
        // to via the role-template catalog — into the effective role set (additive; never removes
        // a Keycloak-claim role) so policy rules authored against either also match.
        //
        // Phase B: canonical CLINICAL roles (CLINIAN/DOCTOR/NURSE/PHARMACIST/SURGEON/
        // ANAESTHETIST) are folded ONLY when the duty mode grants IDENTIFIED clinical
        // access. This is the change that makes "management/support/regulatory mode
        // grants no patient access" structurally true: it removes the role every
        // clinical policy_rule matches on, rather than leaving the boundary to a UI
        // promise. The raw roleTemplateId is still folded regardless — it is a duty
        // fact, not a grant.
        AuthzInternalRequest effective = request;
        List<String> removedForMode = new ArrayList<>();
        if (dc.role() != null && !dc.role().isBlank()) {
            List<String> roles = new ArrayList<>(request.roles() != null ? request.roles() : List.of());
            List<String> toAdd = new ArrayList<>();
            toAdd.add(dc.role());
            List<String> canonical = roleTemplateCatalog.resolve(request.tenantId(), dc.role());
            boolean identifiedClinical = dc.allowsIdentifiedClinicalRead();
            for (String c : canonical) {
                if (!identifiedClinical && c != null && CLINICAL_CANONICAL_ROLES.contains(c.toUpperCase())) {
                    removedForMode.add(c);
                    continue;
                }
                toAdd.add(c);
            }
            boolean changed = false;
            for (String candidate : toAdd) {
                if (candidate != null && !candidate.isBlank()
                        && roles.stream().noneMatch(r -> r != null && r.equalsIgnoreCase(candidate))) {
                    roles.add(candidate);
                    changed = true;
                }
            }
            if (changed) {
                effective = request.withRoles(roles);
            }
        }
        if (!removedForMode.isEmpty()) {
            signalWorkContext(request, "WORK_CONTEXT_MODE_NARROWED", Map.of(
                    "workMode", String.valueOf(dc.workMode()),
                    "removedRoles", String.join(",", removedForMode)));
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
            // "bindingMode" (SHADOW/ENFORCE) and the duty's "workMode" (Phase B, e.g.
            // CLINICAL_CARE) are two different things — they must not share a payload
            // key, or the governance event stream can't tell them apart.
            payload.put("bindingMode", properties.getWorkContextMode());
            auditPublisher.queueGovernanceEvent(eventType, request.actorId(), payload);
        } catch (Exception e) {
            log.warn("Failed to emit work-context signal {}: {}", eventType, e.getMessage());
        }
        log.info("WORK_CONTEXT binding: {} actor={} path={} bindingMode={}",
                eventType, request.actorId(), request.path(), properties.getWorkContextMode());
    }

    private static boolean isMutating(String method) {
        if (method == null) return false;
        String m = method.toUpperCase();
        return m.equals("POST") || m.equals("PUT") || m.equals("PATCH") || m.equals("DELETE");
    }

    /** Department for ABAC: the duty token's proven value when usable, else the client header. */
    private static String effectiveDepartment(AuthzInternalRequest request) {
        DutyContext dc = request.dutyContext();
        if (dc != null && dc.usable() && dc.departmentId() != null) return dc.departmentId();
        return request.departmentId();
    }

    /** Ward for ABAC: duty-token-authoritative, header fallback. */
    private static String effectiveWard(AuthzInternalRequest request) {
        DutyContext dc = request.dutyContext();
        if (dc != null && dc.usable() && dc.wardId() != null) return dc.wardId();
        return request.wardId();
    }

    /** Organisation for ABAC: duty-token-authoritative (no direct header equivalent). */
    private static String effectiveOrganisation(AuthzInternalRequest request) {
        DutyContext dc = request.dutyContext();
        if (dc != null && dc.usable() && dc.orgId() != null) return dc.orgId();
        return null;
    }

    /**
     * Jurisdiction of the current duty, read only from a usable WORK_CONTEXT token.
     *
     * <p>Deliberately NOT sourced from a request header: a header-supplied jurisdiction would let
     * a caller widen their own geographic authority by asserting it, which is precisely what the
     * duty token exists to prevent.</p>
     */
    private static String effectiveJurisdiction(AuthzInternalRequest request) {
        DutyContext dc = request.dutyContext();
        if (dc != null && dc.usable() && dc.jurisdictionCode() != null) return dc.jurisdictionCode();
        return null;
    }

    /**
     * The duty's WorkMode (Phase B), read ONLY from a usable WORK_CONTEXT token — never
     * a header, for the same reason {@link #effectiveJurisdiction} never trusts one: a
     * header-supplied mode would let a caller widen their own authority by asserting it.
     * Null when there is no usable duty token (S2S / citizen / non-duty traffic).
     */
    private static String effectiveWorkMode(AuthzInternalRequest request) {
        DutyContext dc = request.dutyContext();
        if (dc != null && dc.usable() && dc.workMode() != null) return dc.workMode();
        return null;
    }

    /** Provider id for ABAC: duty-token-authoritative, X-Provider-ID header fallback. */
    private static String effectiveProviderId(AuthzInternalRequest request) {
        DutyContext dc = request.dutyContext();
        if (dc != null && dc.usable() && dc.providerId() != null) return dc.providerId();
        return request.providerId();
    }
}
