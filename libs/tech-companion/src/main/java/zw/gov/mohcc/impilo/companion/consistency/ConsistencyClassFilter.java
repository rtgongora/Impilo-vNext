package zw.gov.mohcc.impilo.companion.consistency;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.companion.error.ErrorCodes;
import zw.gov.mohcc.impilo.companion.error.ErrorEnvelopeWriter;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Servlet filter enforcing consistency class rules (A/B/C) per the v1.1 companion spec.
 *
 * <p>Applies to {@code /internal/v1/**} and {@code /external/v1/**} paths.
 * Uses the {@link ActionRegistry} to determine which consistency class applies
 * to each request.</p>
 *
 * <h3>Class A — Synchronous PDP decision required</h3>
 * <ul>
 *   <li>Checks for gateway-injected policy headers (X-Policy-Decision, X-Policy-Version, X-Decision-Reason)</li>
 *   <li>If headers present and decision=ALLOW → proceed</li>
 *   <li>If headers missing and PDP client configured → calls TSHEPO PDP internally</li>
 *   <li>If PDP unavailable → 503 PDP_UNAVAILABLE (break-glass only if action allows)</li>
 * </ul>
 *
 * <h3>Class B — Bounded staleness</h3>
 * <ul>
 *   <li>Queries {@link StalenessProvider} for current staleness</li>
 *   <li>If within threshold → proceed with staleness evidence header</li>
 *   <li>If exceeds threshold → 409 STALE_CONTEXT</li>
 * </ul>
 *
 * <h3>Class C — Offline with entitlement</h3>
 * <ul>
 *   <li>Requires Offline-Entitlement header with a valid token</li>
 *   <li>If missing → 403 OFFLINE_ENTITLEMENT_REQUIRED</li>
 * </ul>
 *
 * <p>Registered at filter order 13 (after V11HeaderFilter=10, Idempotency=11, Timeout=12).</p>
 */
public class ConsistencyClassFilter implements Filter {

    private static final Logger log = LoggerFactory.getLogger(ConsistencyClassFilter.class);

    public static final String OFFLINE_ENTITLEMENT_HEADER = "Offline-Entitlement";
    public static final String STALENESS_EVIDENCE_HEADER = "X-Staleness-Ms";

    private final ActionRegistry actionRegistry;
    private final PdpClient pdpClient;            // nullable — if null, Class A requires gateway headers
    private final StalenessProvider stalenessProvider; // nullable — if null, Class B uses default threshold

    public ConsistencyClassFilter(ActionRegistry actionRegistry,
                                   PdpClient pdpClient,
                                   StalenessProvider stalenessProvider) {
        this.actionRegistry = actionRegistry;
        this.pdpClient = pdpClient;
        this.stalenessProvider = stalenessProvider;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpReq = (HttpServletRequest) request;
        HttpServletResponse httpRes = (HttpServletResponse) response;

        String path = httpReq.getRequestURI();

        // Only enforce on v1.1 paths
        if (!isV11Path(path)) {
            chain.doFilter(request, response);
            return;
        }

        String method = httpReq.getMethod();
        Optional<ActionRegistryEntry> entryOpt = actionRegistry.lookup(path, method);

        // No registry entry → no consistency enforcement for this route
        if (entryOpt.isEmpty()) {
            chain.doFilter(request, response);
            return;
        }

        ActionRegistryEntry entry = entryOpt.get();
        String requestId = headerOrGenerate(httpReq, CompanionHeaders.REQUEST_ID);
        String correlationId = headerOrGenerate(httpReq, CompanionHeaders.CORRELATION_ID);

        switch (entry.consistencyClass()) {
            case CLASS_A -> {
                if (!enforceClassA(httpReq, httpRes, entry, requestId, correlationId)) return;
            }
            case CLASS_B -> {
                if (!enforceClassB(httpReq, httpRes, entry, requestId, correlationId)) return;
            }
            case CLASS_C -> {
                if (!enforceClassC(httpReq, httpRes, entry, requestId, correlationId)) return;
            }
        }

        chain.doFilter(request, response);
    }

    /**
     * Class A enforcement: require sync PDP decision.
     * Returns true if request may proceed, false if response was already written.
     */
    private boolean enforceClassA(HttpServletRequest req, HttpServletResponse res,
                                   ActionRegistryEntry entry,
                                   String requestId, String correlationId) throws IOException {

        // Check for gateway-injected policy decision headers
        String policyDecision = req.getHeader(CompanionHeaders.POLICY_DECISION);
        String policyVersion = req.getHeader(CompanionHeaders.POLICY_VERSION);

        if ("ALLOW".equalsIgnoreCase(policyDecision) && policyVersion != null && !policyVersion.isBlank()) {
            log.debug("Class A: gateway-injected ALLOW for action={}, policy_version={}",
                    entry.actionType(), policyVersion);
            return true;
        }

        // No gateway headers — try internal PDP call if configured
        if (pdpClient != null) {
            try {
                String tenantId = req.getHeader(CompanionHeaders.TENANT_ID);
                String authHeader = req.getHeader(CompanionHeaders.AUTHORIZATION);
                String actorId = extractActorFromAuth(authHeader);

                PdpClient.PdpDecision decision = pdpClient.evaluate(
                        tenantId, actorId, entry.actionType(), "ClassA", correlationId);

                if (decision.isAllow()) {
                    log.debug("Class A: internal PDP ALLOW for action={}, version={}",
                            entry.actionType(), decision.policyVersion());
                    // Inject policy headers for downstream transparency
                    res.setHeader(CompanionHeaders.POLICY_DECISION, decision.decision());
                    res.setHeader(CompanionHeaders.POLICY_VERSION,
                            decision.policyVersion() != null ? decision.policyVersion() : "internal");
                    if (decision.reasonCode() != null) {
                        res.setHeader(CompanionHeaders.DECISION_REASON, decision.reasonCode());
                    }
                    return true;
                } else {
                    log.warn("Class A: PDP DENY for action={}, reason={}",
                            entry.actionType(), decision.reasonCode());
                    ErrorEnvelopeWriter.write(res, 403,
                            ErrorCodes.FORBIDDEN,
                            "Policy decision: " + decision.decision() + " for action " + entry.actionType(),
                            Map.of("action", entry.actionType(),
                                    "decision", decision.decision(),
                                    "reason", decision.reasonCode() != null ? decision.reasonCode() : "DENIED"),
                            requestId, correlationId);
                    return false;
                }
            } catch (PdpClient.PdpUnavailableException e) {
                log.error("Class A: PDP unavailable for action={}", entry.actionType(), e);

                if (entry.breakGlassAllowed()) {
                    // Check for break-glass header
                    String breakGlass = req.getHeader("X-Break-Glass");
                    if ("true".equalsIgnoreCase(breakGlass)) {
                        log.warn("Class A: BREAK-GLASS override for action={}, correlation={}",
                                entry.actionType(), correlationId);
                        res.setHeader(CompanionHeaders.POLICY_DECISION, "ALLOW_BREAK_GLASS");
                        res.setHeader(CompanionHeaders.POLICY_VERSION, "break-glass");
                        res.setHeader(CompanionHeaders.DECISION_REASON, "PDP_UNAVAILABLE_BREAK_GLASS");
                        return true;
                    }
                }

                ErrorEnvelopeWriter.write(res, 503,
                        ErrorCodes.PDP_UNAVAILABLE,
                        "Policy Decision Point unavailable; cannot evaluate action: " + entry.actionType(),
                        Map.of("action", entry.actionType(),
                                "break_glass_allowed", entry.breakGlassAllowed()),
                        requestId, correlationId);
                return false;
            }
        }

        // No PDP client and no gateway headers — reject
        log.warn("Class A: no policy decision available for action={}", entry.actionType());
        ErrorEnvelopeWriter.write(res, 503,
                ErrorCodes.PDP_UNAVAILABLE,
                "No policy decision headers and no PDP client configured for action: " + entry.actionType(),
                Map.of("action", entry.actionType()),
                requestId, correlationId);
        return false;
    }

    /**
     * Class B enforcement: bounded staleness.
     * Returns true if request may proceed, false if response was already written.
     */
    private boolean enforceClassB(HttpServletRequest req, HttpServletResponse res,
                                   ActionRegistryEntry entry,
                                   String requestId, String correlationId) throws IOException {

        long stalenessMs = 0;
        if (stalenessProvider != null) {
            stalenessMs = stalenessProvider.currentStalenessMs(entry.actionType());
        }

        long threshold = entry.stalenessThresholdMs() > 0
                ? entry.stalenessThresholdMs()
                : 30_000; // default 30 seconds

        if (stalenessMs > threshold) {
            log.warn("Class B: staleness {}ms exceeds threshold {}ms for action={}",
                    stalenessMs, threshold, entry.actionType());
            ErrorEnvelopeWriter.write(res, 409,
                    ErrorCodes.STALE_CONTEXT,
                    "Projection staleness " + stalenessMs + "ms exceeds threshold " + threshold + "ms",
                    Map.of("action", entry.actionType(),
                            "staleness_ms", stalenessMs,
                            "threshold_ms", threshold),
                    requestId, correlationId);
            return false;
        }

        // Add staleness evidence header for downstream visibility
        res.setHeader(STALENESS_EVIDENCE_HEADER, String.valueOf(stalenessMs));

        log.debug("Class B: staleness {}ms within threshold {}ms for action={}",
                stalenessMs, threshold, entry.actionType());
        return true;
    }

    /**
     * Class C enforcement: offline with entitlement.
     * Returns true if request may proceed, false if response was already written.
     */
    private boolean enforceClassC(HttpServletRequest req, HttpServletResponse res,
                                   ActionRegistryEntry entry,
                                   String requestId, String correlationId) throws IOException {

        String offlineEntitlement = req.getHeader(OFFLINE_ENTITLEMENT_HEADER);

        if (offlineEntitlement == null || offlineEntitlement.isBlank()) {
            log.warn("Class C: missing Offline-Entitlement header for action={}", entry.actionType());
            ErrorEnvelopeWriter.write(res, 403,
                    ErrorCodes.OFFLINE_ENTITLEMENT_REQUIRED,
                    "Offline-Entitlement header required for offline action: " + entry.actionType(),
                    Map.of("action", entry.actionType(),
                            "consistency_class", "C"),
                    requestId, correlationId);
            return false;
        }

        log.debug("Class C: offline entitlement present for action={}", entry.actionType());
        return true;
    }

    private String headerOrGenerate(HttpServletRequest req, String headerName) {
        String value = req.getHeader(headerName);
        return (value != null && !value.isBlank()) ? value : UUID.randomUUID().toString();
    }

    private String extractActorFromAuth(String authHeader) {
        // In a full implementation, this would decode the JWT sub claim.
        // For Class A internal PDP calls, the PDP re-validates the full token.
        return authHeader != null ? "token-actor" : "anonymous";
    }

    private static boolean isV11Path(String path) {
        return path != null
                && (path.startsWith("/internal/v1/") || path.startsWith("/external/v1/"));
    }
}
