package zw.gov.mohcc.impilo.tshepo.consent.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.tshepo.contracts.dto.ConsentDecision;
import zw.gov.mohcc.impilo.tshepo.consent.persistence.ConsentAuditEntity;
import zw.gov.mohcc.impilo.tshepo.consent.persistence.ConsentAuditRepository;
import zw.gov.mohcc.impilo.tshepo.consent.persistence.ConsentDirectiveEntity;
import zw.gov.mohcc.impilo.tshepo.consent.persistence.ConsentDirectiveRepository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * THE CORE consent evaluation engine.
 *
 * <p>Given (tenantId, actorId/granteeRef, patientRef, purpose, scope, timeframe),
 * evaluates all active consent directives and returns a PERMIT or DENY decision
 * using the {@link ConsentDecision} DTO from tshepo-contracts.</p>
 *
 * <p>This is the hot path — called by tshepo-authz-service on every clinical access
 * decision. Results are cached in Redis with a configurable TTL.</p>
 *
 * <h3>Evaluation Rules:</h3>
 * <ol>
 *   <li>If NO consent exists for this patient: DENY with "NO_ACTIVE_CONSENT"</li>
 *   <li>If a consent exists with provision=deny for this purpose: DENY</li>
 *   <li>If a consent exists with provision=permit AND scope matches AND time is within period: PERMIT</li>
 *   <li>If consent is expired (period_end &lt; now): skip it</li>
 *   <li>Return the most restrictive result if multiple directives apply (deny wins)</li>
 * </ol>
 */
@Service
public class ConsentEvaluationService {

    private static final Logger log = LoggerFactory.getLogger(ConsentEvaluationService.class);

    private final ConsentDirectiveRepository consentRepo;
    private final ConsentCacheService cacheService;
    private final ConsentAuditRepository consentAuditRepository;
    private final ObjectMapper objectMapper;

    public ConsentEvaluationService(ConsentDirectiveRepository consentRepo,
                                     ConsentCacheService cacheService,
                                     ConsentAuditRepository consentAuditRepository,
                                     ObjectMapper objectMapper) {
        this.consentRepo = consentRepo;
        this.cacheService = cacheService;
        this.consentAuditRepository = consentAuditRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Evaluates consent for a given access request.
     *
     * <p>Returns the {@link ConsentDecision} from tshepo-contracts — this is the
     * enforcement contract consumed by tshepo-authz-service. Consent without
     * enforcement is not acceptable.</p>
     *
     * @param tenantId   the tenant context
     * @param actorId    the actor/grantee requesting access
     * @param patientRef the patient CPID/HealthID reference
     * @param purpose    the purpose of use (e.g., TREATMENT, PAYMENT, RESEARCH)
     * @param scope      the requested scope (e.g., read, write, clinical-data)
     * @return the consent decision (permitted=true/false with allowed/denied scopes)
     */
    // NOT readOnly. This method writes an evaluation audit row, so declaring the transaction
    // read-only made every write fail with "cannot execute INSERT in a read-only transaction".
    //
    // The failure was invisible in the worst possible way: persistEvaluationAudit only writes when
    // the decision carries a consentId, and ONLY A PERMIT HAS ONE. Every denial therefore skipped
    // the write and returned cleanly, while every PERMIT threw and surfaced as a 500. Consent
    // evaluation could refuse access perfectly and had never once succeeded -- and because a 500
    // from the consent service makes the PDP fail closed, the estate would have looked like
    // strict consent enforcement rather than a broken audit write.
    @Transactional
    public ConsentDecision evaluate(UUID tenantId, String actorId, String patientRef,
                                     String purpose, String scope) {
        log.debug("Evaluating consent: tenant={} actor={} patient={} purpose={} scope={}",
                tenantId, actorId, patientRef, purpose, scope);

        // Check cache first
        Optional<ConsentDecision> cached = cacheService.getCachedDecision(
                tenantId, patientRef, actorId, purpose, scope);
        if (cached.isPresent()) {
            log.debug("Returning cached consent decision for patient={} actor={}", patientRef, actorId);
            return cached.get();
        }

        // Evaluate from database
        ConsentDecision decision = evaluateFromDatabase(tenantId, actorId, patientRef, purpose, scope);

        // Cache the result
        cacheService.cacheDecision(tenantId, patientRef, actorId, purpose, scope, decision);
        persistEvaluationAudit(tenantId, actorId, patientRef, purpose, scope, decision);

        return decision;
    }

    /**
     * Core evaluation logic against the database.
     * Returns the tshepo-contracts ConsentDecision DTO for direct consumption by authz-service.
     */
    private ConsentDecision evaluateFromDatabase(UUID tenantId, String actorId,
                                                   String patientRef, String purpose,
                                                   String scope) {
        Instant now = Instant.now();

        // Find all active consent directives matching the evaluation criteria.
        // This includes directives where grantee matches OR grantee is null (applies to all).
        List<ConsentDirectiveEntity> directives = consentRepo.findActiveForEvaluation(
                tenantId, patientRef, actorId, purpose);

        // Rule 1: No consent exists for this patient at all
        if (directives.isEmpty()) {
            // Double-check if the patient has ANY active consents
            long totalActive = consentRepo.countByTenantIdAndPatientRefAndStatus(
                    tenantId, patientRef, "ACTIVE");
            if (totalActive == 0) {
                log.info("DENY: No active consents for patient={} in tenant={}", patientRef, tenantId);
                return ConsentDecision.noConsentFound();
            }
            // Patient has consents but none match the requested purpose
            log.info("DENY: No matching consents for patient={} purpose={}", patientRef, purpose);
            return ConsentDecision.deny("NO_MATCHING_CONSENT");
        }

        // Separate permits and denies, filtering out expired ones
        List<ConsentDirectiveEntity> permits = new ArrayList<>();
        List<ConsentDirectiveEntity> denies = new ArrayList<>();

        for (ConsentDirectiveEntity directive : directives) {
            // Rule 4: Skip expired consents
            if (directive.getPeriodEnd() != null && directive.getPeriodEnd().isBefore(now)) {
                log.debug("Skipping expired consent id={} (expired at {})",
                        directive.getId(), directive.getPeriodEnd());
                continue;
            }

            // Check if the consent period has started
            if (directive.getPeriodStart() != null && directive.getPeriodStart().isAfter(now)) {
                log.debug("Skipping not-yet-active consent id={} (starts at {})",
                        directive.getId(), directive.getPeriodStart());
                continue;
            }

            // Categorize by provision type
            if ("deny".equalsIgnoreCase(directive.getProvision())) {
                denies.add(directive);
            } else if ("permit".equalsIgnoreCase(directive.getProvision())) {
                // Rule 3: Check scope match
                if (scopeMatches(directive.getScope(), scope)) {
                    permits.add(directive);
                }
            }
        }

        // Rule 2 & 5: Deny wins if there are any explicit deny provisions (most restrictive)
        if (!denies.isEmpty()) {
            ConsentDirectiveEntity denyDirective = denies.get(0);
            log.info("DENY: Explicit deny consent id={} for patient={} purpose={}",
                    denyDirective.getId(), patientRef, purpose);
            return new ConsentDecision(
                    false,
                    denyDirective.getId().toString(),
                    "deny",
                    null,
                    List.of(scope),
                    "CONSENT_DENIED");
        }

        // Rule 3: Check for matching permits
        if (!permits.isEmpty()) {
            // Use the first matching consent ID as the primary consent reference
            String primaryConsentId = permits.get(0).getId().toString();
            List<String> allowedScopes = permits.stream()
                    .map(ConsentDirectiveEntity::getScope)
                    .distinct()
                    .collect(Collectors.toList());

            log.info("PERMIT: Found {} matching consent directives for patient={} actor={} purpose={}",
                    permits.size(), patientRef, actorId, purpose);
            return ConsentDecision.permit(primaryConsentId, allowedScopes);
        }

        // No valid (non-expired, scope-matching) permits found
        log.info("DENY: No valid consent permits for patient={} actor={} purpose={} scope={}",
                patientRef, actorId, purpose, scope);
        return ConsentDecision.deny("NO_VALID_CONSENT");
    }

    /**
     * Checks if the directive scope matches the requested scope.
     * Supports exact match and wildcard (*) scopes.
     */
    private boolean scopeMatches(String directiveScope, String requestedScope) {
        if (directiveScope == null || requestedScope == null) {
            return false;
        }
        // Wildcard scope matches everything
        if ("*".equals(directiveScope)) {
            return true;
        }
        // Exact match
        if (directiveScope.equalsIgnoreCase(requestedScope)) {
            return true;
        }
        // Prefix match: directive scope "clinical" matches "clinical-data", "clinical-notes", etc.
        if (requestedScope.toLowerCase().startsWith(directiveScope.toLowerCase() + "-")
                || requestedScope.toLowerCase().startsWith(directiveScope.toLowerCase() + "/")) {
            return true;
        }
        return false;
    }

    private void persistEvaluationAudit(
            UUID tenantId, String actorId, String patientRef, String purpose, String scope, ConsentDecision decision) {
        if (decision == null || decision.consentId() == null || decision.consentId().isBlank()) {
            return;
        }
        UUID consentId;
        try {
            consentId = UUID.fromString(decision.consentId());
        } catch (IllegalArgumentException ex) {
            return;
        }
        ConsentAuditEntity audit = new ConsentAuditEntity();
        audit.setTenantId(tenantId);
        audit.setConsentId(consentId);
        audit.setAction("EVALUATE_" + (decision.permitted() ? "PERMIT" : "DENY"));
        audit.setActorId(actorId != null && !actorId.isBlank() ? actorId : "system");
        try {
            var detail = new HashMap<String, Object>();
            detail.put("patientRef", patientRef);
            detail.put("purpose", purpose);
            detail.put("scope", scope);
            detail.put("reason", decision.reason());
            detail.put("provision", decision.provision());
            audit.setDetail(objectMapper.writeValueAsString(detail));
        } catch (JsonProcessingException ex) {
            audit.setDetail("{}");
        }
        consentAuditRepository.save(audit);
    }
}
