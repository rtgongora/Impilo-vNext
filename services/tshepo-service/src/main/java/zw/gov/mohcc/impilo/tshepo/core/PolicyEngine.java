package zw.gov.mohcc.impilo.tshepo.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.tshepo.api.AuthorizationRequest;
import zw.gov.mohcc.impilo.tshepo.integration.GovernanceScopeClient;
import zw.gov.mohcc.impilo.tshepo.persistence.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

/**
 * Policy Decision Point (PDP) for the Impilo Health Operating System.
 *
 * <p>Aligned with Health OS Access Control Doctrine (§11) — evaluates all
 * 10 access-decision dimensions where applicable.</p>
 *
 * Evaluates every request against:
 *   1. Device risk scoring
 *   2. Purpose-of-use validation
 *   3. Break-glass rules
 *   4. RBAC/ABAC policy (role, facility scope, workspace scope)
 *   5. Consent enforcement (for clinical/portal-sensitive resources)
 *   6. Risk-based step-up
 *   7. Provider ID activation check (Health OS §6)
 *   8. Assurance level gate (Health OS §11)
 *   9. ALLOW with appropriate obligations
 *
 * After evaluation, persists the decision to the policy decision log
 * and appends a tamper-evident audit chain entry (serialized via
 * SELECT ... FOR UPDATE on audit_chain_head).
 */
@Service
public class PolicyEngine {

    private static final Logger log = LoggerFactory.getLogger(PolicyEngine.class);

    private final RiskScoring riskScoring;
    private final PolicyDecisionLogRepository decisionLogRepo;
    private final AuditEventRepository auditEventRepo;
    private final AuditChainHeadRepository chainHeadRepo;
    private final ConsentDirectiveRepository consentRepo;
    private final WorkspaceValidationService workspaceValidationService;
    private final ObjectProvider<GovernanceScopeClient> governanceScopeClient;

    public PolicyEngine(
            RiskScoring riskScoring,
            PolicyDecisionLogRepository decisionLogRepo,
            AuditEventRepository auditEventRepo,
            AuditChainHeadRepository chainHeadRepo,
            ConsentDirectiveRepository consentRepo,
            WorkspaceValidationService workspaceValidationService,
            ObjectProvider<GovernanceScopeClient> governanceScopeClient) {
        this.riskScoring = riskScoring;
        this.decisionLogRepo = decisionLogRepo;
        this.auditEventRepo = auditEventRepo;
        this.chainHeadRepo = chainHeadRepo;
        this.consentRepo = consentRepo;
        this.workspaceValidationService = workspaceValidationService;
        this.governanceScopeClient = governanceScopeClient;
    }

    /**
     * Evaluate an authorization request and return a decision.
     * This method is transactional to ensure the audit chain write is serialized.
     */
    @Transactional
    public Decision evaluate(AuthorizationRequest request) {
        long startTime = System.currentTimeMillis();

        UUID tenantId = request.tenantId();

        // --- Step 1: Risk scoring ---
        int riskScore = riskScoring.score(
            tenantId,
            request.deviceFingerprint(),
            request.actorId()
        );

        // Device is blocked
        if (riskScore >= 81) {
            Decision decision = Decision.deny("DEVICE_BLOCKED", "Device risk threshold exceeded", riskScore);
            persistDecision(request, decision, startTime);
            appendAuditEntry(request, "DENY");
            return decision;
        }

        // --- Step 2: Purpose-of-use validation ---
        PurposeOfUse purpose = PurposeOfUse.fromHeader(request.purposeOfUse());
        if (purpose == null) {
            Decision decision = Decision.deny("INVALID_PURPOSE", "Missing or invalid purpose-of-use", riskScore);
            persistDecision(request, decision, startTime);
            appendAuditEntry(request, "DENY");
            return decision;
        }

        // --- Step 3: Break-glass check ---
        if (purpose == PurposeOfUse.BREAK_GLASS) {
            // Break-glass always requires step-up verification
            if (riskScore > 30) {
                Decision decision = Decision.stepUpRequired(
                    List.of("SUPERVISOR_APPROVAL", "REASON_CODE"), riskScore
                );
                persistDecision(request, decision, startTime);
                appendAuditEntry(request, "STEP_UP_REQUIRED");
                return decision;
            }
            // Low-risk break-glass: allow with elevated logging
            Decision decision = Decision.allow(
                new Obligations(null, null, "ELEVATED", null), riskScore,
                List.of("BREAK_GLASS_APPROVED", "LOW_RISK")
            );
            persistDecision(request, decision, startTime);
            appendAuditEntry(request, "ALLOW_BREAK_GLASS");
            return decision;
        }

        // --- Step 4: RBAC/ABAC policy evaluation ---
        // Facility scope check: if the request specifies a facility, verify actor has access
        if ((request.facilityId() != null || request.tusoFacilityNumericId() != null)
                && !isActorAuthorizedForFacility(request)) {
            Decision decision = Decision.deny("FACILITY_SCOPE",
                "Actor not authorized for requested facility", riskScore);
            persistDecision(request, decision, startTime);
            appendAuditEntry(request, "DENY");
            return decision;
        }

        // --- Step 5: Consent enforcement (for clinical resources) ---
        if (requiresConsent(request.action(), request.resourceType())) {
            boolean hasConsent = checkConsent(
                tenantId, request.resourceId(), request.actorId(), purpose
            );
            if (!hasConsent) {
                Decision decision = Decision.deny("CONSENT_REQUIRED",
                    "No active consent for this access", riskScore);
                persistDecision(request, decision, startTime);
                appendAuditEntry(request, "DENY");
                return decision;
            }
        }

        // --- Step 6: Risk-based step-up ---
        if (riskScore >= 61 && isHighRiskAction(request.action())) {
            Decision decision = Decision.stepUpRequired(
                List.of("MFA", "BIOMETRIC"), riskScore
            );
            persistDecision(request, decision, startTime);
            appendAuditEntry(request, "STEP_UP_REQUIRED");
            return decision;
        }

        // --- Step 7: Provider ID activation check (Health OS §6) ---
        // "Sign in as a person; practice as a provider only under activated Provider ID."
        // Clinical write actions by PROVIDER actors require an active Provider ID header.
        if (requiresProviderActivation(request) && !hasActivatedProvider(request)) {
            Decision decision = Decision.deny("PROVIDER_NOT_ACTIVATED",
                "Regulated professional action requires an activated Provider ID (x-provider-id header)", riskScore);
            persistDecision(request, decision, startTime);
            appendAuditEntry(request, "DENY");
            return decision;
        }

        // --- Step 8: Assurance level gate (Health OS §11) ---
        // High-sensitivity resources require elevated identity assurance (LOA3+).
        if (requiresElevatedAssurance(request.action(), request.resourceType())
                && !meetsAssuranceThreshold(request.assuranceLevel(), "LOA3")) {
            Decision decision = Decision.stepUpRequired(
                List.of("IDENTITY_PROOFING", "BIOMETRIC"), riskScore
            );
            persistDecision(request, decision, startTime);
            appendAuditEntry(request, "STEP_UP_ASSURANCE");
            return decision;
        }

        // --- Step 9: ALLOW with appropriate obligations ---
        Obligations obligations = computeObligations(request, purpose, riskScore);
        Decision decision = Decision.allow(obligations, riskScore);
        persistDecision(request, decision, startTime);
        appendAuditEntry(request, "ALLOW");

        return decision;
    }

    // ------------------------------------------------------------------
    // Audit chain — serialized write with SELECT ... FOR UPDATE
    // ------------------------------------------------------------------

    private void appendAuditEntry(AuthorizationRequest request, String outcome) {
        UUID tenantId = request.tenantId();

        // Lock the chain head row (or create genesis if first entry)
        AuditChainHeadEntity head = chainHeadRepo.findByTenantIdForUpdate(tenantId);
        if (head == null) {
            head = new AuditChainHeadEntity();
            head.setTenantId(tenantId);
            head.setCurrentHash("GENESIS");
            head.setCurrentSeq(0L);
            head.setUpdatedAt(Instant.now());
            head = chainHeadRepo.saveAndFlush(head);
        }

        long newSeq = head.getCurrentSeq() + 1;
        String prevHash = head.getCurrentHash();

        // Build audit event
        AuditEventEntity event = new AuditEventEntity();
        event.setTenantId(tenantId);
        event.setSequenceNum(newSeq);
        event.setCorrelationId(request.correlationId());
        event.setActorId(request.actorId());
        event.setActorType(request.actorType());
        event.setAction(request.action());
        event.setResourceType(request.resourceType());
        event.setResourceId(request.resourceId());
        event.setPurposeOfUse(request.purposeOfUse());
        event.setFacilityId(request.facilityId());
        event.setWorkspaceId(request.workspaceId());
        event.setOutcome(outcome);
        event.setOccurredAt(Instant.now());
        event.setPrevHash(prevHash);

        // Compute tamper-evident hash
        String entryHash = computeEntryHash(tenantId, newSeq, prevHash, event);
        event.setEntryHash(entryHash);

        auditEventRepo.save(event);

        // Update head pointer
        head.setCurrentHash(entryHash);
        head.setCurrentSeq(newSeq);
        head.setUpdatedAt(Instant.now());
        chainHeadRepo.save(head);
    }

    private String computeEntryHash(UUID tenantId, long seq, String prevHash, AuditEventEntity event) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String payload = String.join("|",
                tenantId.toString(),
                String.valueOf(seq),
                prevHash,
                event.getActorId(),
                event.getAction(),
                event.getResourceType(),
                String.valueOf(event.getResourceId()),
                event.getOutcome(),
                event.getOccurredAt().toString()
            );
            byte[] hash = digest.digest(payload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    // ------------------------------------------------------------------
    // Policy decision log persistence
    // ------------------------------------------------------------------

    private void persistDecision(AuthorizationRequest request, Decision decision, long startTime) {
        long durationMs = System.currentTimeMillis() - startTime;

        PolicyDecisionLogEntity entry = new PolicyDecisionLogEntity();
        entry.setTenantId(request.tenantId());
        entry.setCorrelationId(request.correlationId());
        entry.setActorId(request.actorId());
        entry.setActorType(request.actorType());
        entry.setAction(request.action());
        entry.setResourceType(request.resourceType());
        entry.setResourceId(request.resourceId());
        entry.setPurposeOfUse(request.purposeOfUse());
        entry.setFacilityId(request.facilityId());
        entry.setWorkspaceId(request.workspaceId());
        // Health OS §12: audit must capture providerId and subjectId where available
        entry.setProviderId(request.providerId());
        entry.setSubjectId(request.subjectId());
        entry.setDecision(decision.verdict().name());
        entry.setObligations(decision.obligations() != null ? decision.obligations().toJson() : null);
        entry.setRiskScore((short) decision.riskScore());
        entry.setDecidedAt(Instant.now());
        entry.setDurationMs((int) durationMs);
        entry.setPolicyVersion(decision.policyVersion());
        if (decision.reasonCodes() != null && !decision.reasonCodes().isEmpty()) {
            entry.setReasonCodes(String.join(",", decision.reasonCodes()));
        }

        decisionLogRepo.save(entry);
    }

    // ------------------------------------------------------------------
    // Policy helper methods
    // ------------------------------------------------------------------

    private boolean isActorAuthorizedForFacility(AuthorizationRequest request) {
        Long tusoFacility = request.tusoFacilityNumericId();
        if (request.facilityId() == null && tusoFacility == null) {
            // No facility scope in request — allow (resource-level access)
            return true;
        }

        // System actors and super-admins bypass facility checks
        if ("SYSTEM".equalsIgnoreCase(request.actorType())
                || "SUPER_ADMIN".equalsIgnoreCase(request.actorType())) {
            return true;
        }

        // Check if the actor has an active workspace or shift at the requested facility
        if (request.workspaceId() != null) {
            return workspaceMatchesFacility(
                    request.tenantId(),
                    request.workspaceId(),
                    tusoFacility,
                    request.actorId(),
                    request.providerId(),
                    request.correlationId());
        }

        if (request.shiftId() != null && !request.shiftId().isBlank()) {
            // Actor has an active shift — shifts are bound to facilities
            return true;
        }

        // Assignment-aware governance: when TUSO facility id is present, consult Workforce Governance.
        if (tusoFacility != null) {
            GovernanceScopeClient client = governanceScopeClient.getIfAvailable();
            if (client != null && client.isEnabled()) {
                String corr = request.correlationId() != null ? request.correlationId().toString() : null;
                return client.evaluateFacilityScope(
                        request.tenantId(),
                        request.actorId(),
                        request.providerId(),
                        tusoFacility,
                        corr);
            }
        }

        log.debug("No workspace/shift for actor {} at facility context (tusoFacility={}, legacyUuid={}), allowing with audit",
                request.actorId(), tusoFacility, request.facilityId());
        return true;
    }

    private boolean workspaceMatchesFacility(UUID tenantId,
                                             UUID workspaceId,
                                             Long requestedTusoFacilityId,
                                             String actorId,
                                             String providerId,
                                             UUID correlationId) {
        if (workspaceId == null) {
            return false;
        }

        var result = workspaceValidationService.validate(tenantId, workspaceId);

        if (result.degradedMode()) {
            log.warn("Workspace validation degraded (TUSO unavailable), workspace={}", workspaceId);
            return true;
        }

        log.debug("Workspace {} validation: valid={}, active={}, reason={}",
                workspaceId, result.valid(), result.active(), result.reason());

        if (!result.valid()) {
            return false;
        }

        if (requestedTusoFacilityId != null && result.facilityNumericId() != null
                && !requestedTusoFacilityId.equals(result.facilityNumericId())) {
            log.warn("Workspace {} belongs to facility {}, not {}",
                    workspaceId, result.facilityNumericId(), requestedTusoFacilityId);
            return false;
        }

        GovernanceScopeClient client = governanceScopeClient.getIfAvailable();
        Long facilityForGovernance = requestedTusoFacilityId != null ? requestedTusoFacilityId : result.facilityNumericId();
        if (client != null && client.isEnabled() && facilityForGovernance != null) {
            return client.evaluateFacilityScope(
                    tenantId,
                    actorId,
                    providerId,
                    facilityForGovernance,
                    correlationId != null ? correlationId.toString() : null);
        }

        return true;
    }

    private boolean requiresConsent(String action, String resourceType) {
        // Clinical reads on patient data require consent
        if (resourceType == null) return false;
        return resourceType.startsWith("Patient") ||
               resourceType.startsWith("Encounter") ||
               resourceType.startsWith("Observation") ||
               resourceType.startsWith("DiagnosticReport");
    }

    private boolean checkConsent(UUID tenantId, String resourceId, String actorId, PurposeOfUse purpose) {
        if (purpose == PurposeOfUse.EMERGENCY || purpose == PurposeOfUse.BREAK_GLASS) {
            return true; // Emergency/break-glass overrides consent (with elevated audit)
        }
        if (resourceId == null) {
            return true; // Collection-level access doesn't require specific consent
        }
        // Check for active consent directive covering this access
        return consentRepo.existsActiveConsent(
            tenantId, resourceId, actorId, purpose.name()
        );
    }

    // ------------------------------------------------------------------
    // Health OS §6: Provider ID activation
    // ------------------------------------------------------------------

    /**
     * Determines if this request requires an activated Provider ID.
     * Clinical write actions by PROVIDER-type actors are regulated professional acts.
     */
    private boolean requiresProviderActivation(AuthorizationRequest request) {
        if (!"PROVIDER".equalsIgnoreCase(request.actorType())) {
            return false; // Only provider-type actors need Provider ID activation
        }
        String action = request.action();
        if (action == null) return false;
        // Clinical writes: POST/PUT/PATCH/DELETE on clinical resources
        boolean isMutating = action.startsWith("POST:") || action.startsWith("PUT:")
                || action.startsWith("PATCH:") || action.startsWith("DELETE:");
        if (!isMutating) return false;
        String resourceType = request.resourceType();
        return resourceType != null && (
            resourceType.startsWith("Patient") ||
            resourceType.startsWith("Encounter") ||
            resourceType.startsWith("Observation") ||
            resourceType.equals("prescriptions") ||
            resourceType.equals("dispense") ||
            resourceType.equals("referrals") ||
            resourceType.equals("lab-orders") ||
            resourceType.equals("clinical-notes") ||
            resourceType.equals("vitals") ||
            resourceType.equals("triage")
        );
    }

    private boolean hasActivatedProvider(AuthorizationRequest request) {
        return request.providerId() != null && !request.providerId().isBlank();
    }

    // ------------------------------------------------------------------
    // Health OS §11: Assurance level
    // ------------------------------------------------------------------

    /**
     * Determines if a resource/action pair requires elevated identity assurance.
     * Merge, export, and break-glass-adjacent actions require LOA3+.
     */
    private boolean requiresElevatedAssurance(String action, String resourceType) {
        if (action == null) return false;
        if (action.contains("MERGE") || action.contains("EXPORT") || action.contains("BULK")) {
            return true;
        }
        // PII-heavy resources always require elevated assurance
        return "clients".equalsIgnoreCase(resourceType)
            || "identity".equalsIgnoreCase(resourceType);
    }

    /**
     * Checks if the provided assurance level meets the required threshold.
     * Levels: LOA1 < LOA2 < LOA3 < LOA4. Null/missing treated as LOA1.
     */
    private boolean meetsAssuranceThreshold(String actual, String required) {
        int actualLevel = parseLoaLevel(actual);
        int requiredLevel = parseLoaLevel(required);
        return actualLevel >= requiredLevel;
    }

    private int parseLoaLevel(String loa) {
        if (loa == null || loa.isBlank()) return 1;
        return switch (loa.toUpperCase()) {
            case "LOA1" -> 1;
            case "LOA2" -> 2;
            case "LOA3" -> 3;
            case "LOA4" -> 4;
            default -> 1;
        };
    }

    private boolean isHighRiskAction(String action) {
        if (action == null) return false;
        return action.contains("DELETE") ||
               action.contains("EXPORT") ||
               action.contains("BULK") ||
               action.contains("MERGE") ||
               action.contains("RECOVERY");
    }

    // ------------------------------------------------------------------
    // Health OS §8: Graduated trust and friction
    // "Friction must be proportionate to risk."
    // ------------------------------------------------------------------

    /**
     * Determines the friction level for a given resource type and action.
     * Used by obligation computation to signal the UI how much verification
     * to apply. The graduated levels are:
     *
     * MINIMAL — wellness exploration, club browsing, lifestyle content
     * LOW — buying non-regulated goods, connecting ordinary devices
     * STANDARD — authenticated access to personal records, scheduling
     * ELEVATED — clinical record access, write operations on health data
     * MAXIMUM — prescribing, claims, controlled substance fulfilment
     */
    enum FrictionLevel { MINIMAL, LOW, STANDARD, ELEVATED, MAXIMUM }

    static FrictionLevel classifyFriction(String action, String resourceType, PurposeOfUse purpose) {
        if (resourceType == null) return FrictionLevel.STANDARD;

        // Wellness, lifestyle, community, search, conversational — minimal friction
        // Health OS §8: "low-risk wellness exploration should be easy"
        // Health OS §16a: "searching for governed knowledge should be easy within safe boundaries"
        if (resourceType.startsWith("wellness") || resourceType.startsWith("clubs")
                || resourceType.startsWith("community") || resourceType.startsWith("challenges")
                || resourceType.startsWith("routes") || resourceType.startsWith("diet")
                || resourceType.startsWith("sleep") || resourceType.startsWith("coaching")
                || resourceType.startsWith("search") || resourceType.startsWith("ask")
                || resourceType.startsWith("guidance") || resourceType.startsWith("education")
                || resourceType.startsWith("knowledge")) {
            return FrictionLevel.MINIMAL;
        }

        // Marketplace browse, low-risk products — low friction
        if (resourceType.startsWith("marketplace") || resourceType.startsWith("catalog")
                || resourceType.equals("products") || resourceType.equals("vendors")) {
            if (action != null && action.startsWith("GET:")) return FrictionLevel.LOW;
            return FrictionLevel.STANDARD; // writes to marketplace = standard
        }

        // Clinical reads — elevated
        if (resourceType.startsWith("Patient") || resourceType.startsWith("Encounter")
                || resourceType.startsWith("Observation")) {
            if (action != null && action.startsWith("GET:")) return FrictionLevel.ELEVATED;
            return FrictionLevel.MAXIMUM; // clinical writes = maximum
        }

        // Prescriptions, controlled substances — maximum
        if (resourceType.equals("prescriptions") || resourceType.equals("dispense")
                || resourceType.equals("claims")) {
            return FrictionLevel.MAXIMUM;
        }

        // Default
        return FrictionLevel.STANDARD;
    }

    private Obligations computeObligations(AuthorizationRequest request, PurposeOfUse purpose, int riskScore) {
        String loggingLevel = riskScore > 50 ? "ELEVATED" : "STANDARD";

        // Health OS §8: classify friction level for the UI
        FrictionLevel friction = classifyFriction(request.action(), request.resourceType(), purpose);
        // Wellness/lifestyle actions get lighter logging unless risk-scored
        if (friction == FrictionLevel.MINIMAL && riskScore < 30) {
            loggingLevel = "LIGHT";
        }

        // Purpose-based masking
        if (purpose == PurposeOfUse.RESEARCH || purpose == PurposeOfUse.PUBLIC_HEALTH) {
            return new Obligations(
                "ANONYMIZED",
                List.of("name", "phone", "address", "dateOfBirth"),
                loggingLevel,
                friction.name()
            );
        }

        if (purpose == PurposeOfUse.OPERATIONS) {
            return new Obligations(
                "FACILITY_SCOPE",
                List.of("name", "phone"),
                loggingLevel,
                friction.name()
            );
        }

        return new Obligations(null, null, loggingLevel, friction.name());
    }
}
