package zw.gov.mohcc.impilo.experience.scheduling;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import zw.gov.mohcc.impilo.experience.client.TshepoIdentityServiceClient;
import zw.gov.mohcc.impilo.experience.config.SystemTrustContext;
import zw.gov.mohcc.impilo.experience.workcontext.ResolvedWorkContext;
import zw.gov.mohcc.impilo.experience.workcontext.WorkContextResolutionService;

import java.util.ArrayList;
import java.util.List;

/**
 * Re-proves live WORK_CONTEXT tokens against source, so a duty that ends does not leave a
 * usable token behind for the remainder of its TTL (Phase C, C4).
 *
 * <p>A work token binds a context that was proven <em>once</em>, at mint. If the underlying
 * assignment is then revoked, suspended or expires, the token keeps asserting that authority
 * until it expires naturally. This job closes that window to the poll interval.</p>
 *
 * <h3>Why this cannot simply revoke whatever fails to prove</h3>
 * <p>The resolver unions five sources. When those sources are unreachable it reports
 * {@code work_context_unavailable} and returns no contexts — which is indistinguishable, at
 * the level of a single context lookup, from "this person's duty genuinely ended". A job
 * that revoked on proof-failure alone would therefore convert a WGV or Vashandi outage into
 * a mass logout of every signed-in worker in the country, at the exact moment the estate is
 * already degraded.</p>
 *
 * <p>So revocation is gated twice. Per token, the resolution must be healthy — a degraded or
 * partially-available resolution defers that token to a later cycle rather than acting on
 * evidence it knows to be incomplete. Across the cycle, a circuit breaker refuses to act at
 * all if an implausible share of tokens would be revoked at once, which catches systemic
 * causes the per-token check cannot see. Deferring costs a few minutes of stale authority;
 * the alternative costs the estate its availability, and silently.</p>
 *
 * <p>Disabled by default. This job revokes credentials, so it is switched on deliberately,
 * per environment, after the shadow counters below have been observed.</p>
 */
@Component
public class WorkContextRevalidationJob {

    private static final Logger log = LoggerFactory.getLogger(WorkContextRevalidationJob.class);

    /** Stable grep targets for the shadow-mode measurement and for alerting. */
    public static final String REVOKED_MARKER = "WORK_CONTEXT_REVALIDATION_REVOKED";
    public static final String WOULD_REVOKE_MARKER = "WORK_CONTEXT_REVALIDATION_WOULD_REVOKE";
    public static final String DEFERRED_MARKER = "WORK_CONTEXT_REVALIDATION_DEFERRED";
    public static final String CIRCUIT_MARKER = "WORK_CONTEXT_REVALIDATION_CIRCUIT_OPEN";

    private final TshepoIdentityServiceClient identityClient;
    private final WorkContextResolutionService resolutionService;
    private final boolean enabled;
    private final boolean revokeEnabled;
    private final int batchLimit;
    private final double circuitBreakerRatio;

    public WorkContextRevalidationJob(
            TshepoIdentityServiceClient identityClient,
            WorkContextResolutionService resolutionService,
            @Value("${impilo.work-context.revalidation.enabled:false}") boolean enabled,
            @Value("${impilo.work-context.revalidation.revoke-enabled:false}") boolean revokeEnabled,
            @Value("${impilo.work-context.revalidation.batch-limit:200}") int batchLimit,
            @Value("${impilo.work-context.revalidation.circuit-breaker-ratio:0.25}") double circuitBreakerRatio) {
        this.identityClient = identityClient;
        this.resolutionService = resolutionService;
        this.enabled = enabled;
        this.revokeEnabled = revokeEnabled;
        this.batchLimit = batchLimit;
        this.circuitBreakerRatio = circuitBreakerRatio;
    }

    /** Outcome of one sweep, returned for tests and for the operator report. */
    public record CycleReport(int examined, int stillValid, int deferred, int revoked, int wouldRevoke,
                              boolean circuitOpen) {
        public static CycleReport empty() {
            return new CycleReport(0, 0, 0, 0, 0, false);
        }
    }

    @Scheduled(fixedDelayString = "${impilo.work-context.revalidation.interval-ms:120000}")
    public void revalidate() {
        if (!enabled) {
            return;
        }
        try {
            runCycle();
        } catch (Exception e) {
            // A sweep failure must never kill the scheduler thread.
            log.warn("Work context revalidation cycle failed: {}", e.getMessage());
        }
    }

    public CycleReport runCycle() {
        JsonNode tokens = identityClient.listActiveWorkContextTokens(batchLimit);
        if (tokens == null || !tokens.isArray() || tokens.isEmpty()) {
            return CycleReport.empty();
        }

        List<Candidate> candidates = new ArrayList<>();
        int stillValid = 0;
        int deferred = 0;

        for (JsonNode row : tokens) {
            String jti = text(row, "jti");
            String actorId = text(row, "actorId");
            String tenantId = text(row, "tenantId");
            String contextId = text(row, "contextId");
            if (jti == null || actorId == null || contextId == null) {
                continue;
            }

            Verdict verdict = SystemTrustContext.callWith(tenantId, "work-context-revalidation",
                    () -> prove(actorId, contextId));

            switch (verdict) {
                case STILL_VALID -> stillValid++;
                case UNPROVABLE -> candidates.add(new Candidate(jti, actorId, contextId, tenantId));
                case DEFER -> {
                    deferred++;
                    log.info("{} jti={} reason=sources_degraded", DEFERRED_MARKER, jti);
                }
            }
        }

        int examined = stillValid + deferred + candidates.size();

        // Systemic-cause circuit breaker: a healthy estate does not end a quarter of all
        // duties inside one two-minute window. If it looks like it did, the more likely
        // explanation is a resolver or feed defect, and acting on it would be the damage.
        if (!candidates.isEmpty() && examined > 0
                && (double) candidates.size() / examined > circuitBreakerRatio) {
            log.error("{} candidates={} examined={} ratio_limit={} — revoking nothing this cycle",
                    CIRCUIT_MARKER, candidates.size(), examined, circuitBreakerRatio);
            return new CycleReport(examined, stillValid, deferred, 0, candidates.size(), true);
        }

        int revoked = 0;
        for (Candidate c : candidates) {
            if (!revokeEnabled) {
                // Shadow mode: measure what enforcement would do before it does it.
                log.info("{} jti={} actor={} context={}", WOULD_REVOKE_MARKER, c.jti(), c.actorId(), c.contextId());
                continue;
            }
            try {
                identityClient.revokeScopedToken(c.jti());
                revoked++;
                log.info("{} jti={} actor={} context={}", REVOKED_MARKER, c.jti(), c.actorId(), c.contextId());
            } catch (Exception e) {
                log.warn("Work context revalidation could not revoke jti={}: {}", c.jti(), e.getMessage());
            }
        }

        return new CycleReport(examined, stillValid, deferred, revoked,
                revokeEnabled ? 0 : candidates.size(), false);
    }

    private enum Verdict { STILL_VALID, UNPROVABLE, DEFER }

    private record Candidate(String jti, String actorId, String contextId, String tenantId) {}

    private Verdict prove(String actorId, String contextId) {
        WorkContextResolutionService.ResolutionOutcome outcome;
        try {
            outcome = resolutionService.resolve(actorId, null);
        } catch (Exception e) {
            // A thrown resolution is not evidence a duty ended.
            return Verdict.DEFER;
        }
        if (outcome == null) {
            return Verdict.DEFER;
        }
        // resolve() reports degradation explicitly precisely so callers need not infer it
        // from an empty list. Absence of evidence is not evidence of absence.
        if (outcome.friendlyResolutionState() != null) {
            return Verdict.DEFER;
        }
        boolean present = outcome.contexts().stream()
                .map(ResolvedWorkContext::contextId)
                .anyMatch(contextId::equals);
        return present ? Verdict.STILL_VALID : Verdict.UNPROVABLE;
    }

    private static String text(JsonNode node, String field) {
        if (node == null) return null;
        JsonNode v = node.get(field);
        return v != null && !v.isNull() ? v.asText() : null;
    }
}
