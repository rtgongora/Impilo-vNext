package zw.gov.mohcc.impilo.tshepo.authz.shadow;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.tshepo.authz.core.PolicyEngine;
import zw.gov.mohcc.impilo.tshepo.authz.persistence.entity.ShadowDecisionLogEntity;
import zw.gov.mohcc.impilo.tshepo.authz.session.SessionInfo;
import zw.gov.mohcc.impilo.tshepo.authz.session.SessionAssuranceRouter;
import zw.gov.mohcc.impilo.tshepo.authz.dto.AuthzInternalRequest;
import zw.gov.mohcc.impilo.tshepo.contracts.dto.AuthzResponse;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.Map;
import java.util.UUID;

/**
 * Measures what authorization would decide if the PDP could see a browser session's roles.
 *
 * <h2>Why this endpoint exists at all</h2>
 * <p>Measured 2026-08-07: the browser holds an opaque BFF session cookie and sends no
 * Authorization header, so ext_authz validates no session and resolves no roles — while 489 of 525
 * active ALLOW rules require one. 93% of the platform's authorization rules cannot match a
 * signed-in human. Enabling that blind is not available to us: rules that have never fired would
 * begin firing, DENY rules included, against traffic that works today.</p>
 *
 * <h2>Why it cannot affect a decision</h2>
 * <p><b>Isolation here is physical, not conditional.</b> The production decision is made by
 * ext_authz at Envoy <em>before</em> the BFF executes any code. This endpoint is called
 * afterwards, by the BFF, on a separate request. There is no ordering in which its answer could
 * replace a verdict that has already been returned — which is a stronger guarantee than any flag
 * check, and the reason this shape was chosen over attaching a bearer to the live path.</p>
 *
 * <p>It therefore returns <b>no verdict</b>. The response is an acknowledgement, so that a caller
 * cannot come to depend on it, and so this can never quietly become an alternate authentication
 * route for an ordinary endpoint.</p>
 *
 * <h2>The bearer</h2>
 * <p>Arrives in the request body, is used to validate a session and resolve roles, and is
 * discarded. It is never persisted (the table has no column for it), never logged, never traced,
 * and never forwarded. {@link ShadowEvaluationRequest#toString()} redacts it.</p>
 */
@RestController
@RequestMapping("/v1/authorize/shadow")
public class ShadowAuthorizationController {

    private static final Logger log = LoggerFactory.getLogger(ShadowAuthorizationController.class);

    private final PolicyEngine policyEngine;
    private final SessionAssuranceRouter sessionRouter;
    private final ShadowDecisionRecorder recorder;
    private final ShadowProbeProperties properties;
    /** Per-instance concurrency ceiling; see gate 3. */
    private final Semaphore capacity;

    public ShadowAuthorizationController(PolicyEngine policyEngine,
                                         SessionAssuranceRouter sessionRouter,
                                         ShadowDecisionRecorder recorder,
                                         ShadowProbeProperties properties) {
        this.policyEngine = policyEngine;
        this.sessionRouter = sessionRouter;
        this.recorder = recorder;
        this.properties = properties;
        this.capacity = new Semaphore(Math.max(1, properties.getMaxConcurrent()));
    }

    /**
     * Dedicated probe-authentication header. TEMPORARY P1 mechanism — see
     * {@link ShadowProbeProperties}. Redacted wherever it could be rendered.
     */
    public static final String PROBE_KEY_HEADER = "X-Impilo-Shadow-Probe-Key";

    @PostMapping
    public ResponseEntity<Map<String, String>> evaluate(
            @RequestBody ShadowEvaluationRequest req,
            @RequestHeader(value = PROBE_KEY_HEADER, required = false) String probeKey) {

        // ── Gate 1: the feature itself. FIRST, and before ANY bearer processing. ─────────────
        // Disabled means the endpoint does not exist, not "the BFF politely declines to call it".
        // An estate with shadowing off must not expose a token-processing surface at all, so this
        // returns 404 rather than 403 — a disabled experiment should not even advertise itself.
        if (!properties.isEnabled()) {
            return ResponseEntity.notFound().build();
        }

        // ── Gate 2: is the CALLER an authorised workload? ────────────────────────────────────
        // This is not the user's bearer and must never be confused with it. The probe key answers
        // "may this workload invoke the measurement API"; the bearer inside the DTO answers "which
        // human is being hypothetically evaluated". Using either as the other collapses the trust
        // boundary — the user's token is EVIDENCE BEING EVALUATED, not proof of entitlement to
        // call an internal API.
        //
        // Fails closed on a blank configured key: no secret configured refuses everyone, rather
        // than accepting anyone.
        if (!probeKeyMatches(probeKey)) {
            log.warn("shadow probe rejected: bad or missing {} (key value never logged)", PROBE_KEY_HEADER);
            return ResponseEntity.status(403).build();
        }

        // ── Gate 3: capacity. Production authorization wins every contention, always. ────────
        // Non-blocking: a saturated shadow path must never add latency to the PDP, and a bug in
        // the BFF must not be able to make the measurement a denial-of-service amplifier against
        // the thing being measured. A dropped probe is RECORDED, not silently lost — a dataset
        // that quietly omits its hardest requests is worse than one that admits the gap.
        if (!capacity.tryAcquire()) {
            ShadowDecisionLogEntity dropped = new ShadowDecisionLogEntity();
            dropped.setMethod(req.method());
            dropped.setPath(req.path());
            dropped.setRouteClass(routeClass(req.path()));
            dropped.setProbeMode(req.probeMode());
            dropped.setProbeOutcome(ShadowDecisionLogEntity.OUTCOME_DROPPED_CAPACITY);
            recorder.record(dropped);
            return ack();
        }
        try {
            return evaluateInternal(req);
        } finally {
            capacity.release();
        }
    }

    /** Constant-time comparison. A timing-distinguishable compare on a shared secret is a leak. */
    private boolean probeKeyMatches(String presented) {
        String expected = properties.getProbeKey();
        if (expected == null || expected.isBlank() || presented == null) return false;
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                presented.getBytes(StandardCharsets.UTF_8));
    }

    private ResponseEntity<Map<String, String>> evaluateInternal(ShadowEvaluationRequest req) {
        ShadowDecisionLogEntity row = new ShadowDecisionLogEntity();
        row.setProbeMode(req.probeMode());
        row.setMethod(req.method());
        row.setPath(req.path());
        row.setProdVerdict(req.prodVerdict());
        row.setProdActorType(req.prodActorType());
        row.setTotalAddedMillis(req.totalAddedMillis());
        row.setRouteClass(routeClass(req.path()));

        Map<String, String> ctx = req.trustContext() == null ? Map.of() : req.trustContext();
        row.setActorId(ctx.get("actorId"));
        row.setTenantId(uuid(ctx.get("tenantId")));
        row.setCorrelationId(uuid(ctx.get("correlationId")));

        long started = System.nanoTime();
        try {
            SessionInfo session;
            try {
                session = sessionRouter.validateSession(req.bearer());
            } catch (Exception e) {
                // A session we cannot validate is a measurable outcome, not an error to hide: it
                // tells us the bearer path would not have produced roles for this request either.
                row.setProbeOutcome(ShadowDecisionLogEntity.OUTCOME_BEARER_UNRESOLVED);
                recorder.record(row);
                return ack();
            }

            List<String> roles = session.roles() == null ? List.of() : session.roles();
            row.setShadowRoles(String.join(",", roles));

            // The proposed model. Deliberately NOT deriveActorType() — see PrincipalProjection.
            String workMode = ctx.get("workMode");           // only ever from a proven duty context
            String providerId = ctx.get("providerId");
            PrincipalProjection.Projection projection =
                    PrincipalProjection.project(roles, workMode, providerId, false);
            row.setPrincipalClass(projection.principalClass().name());
            row.setActivePersona(projection.activePersona());
            row.setLegacyActorType(projection.legacyActorType());

            // Same inputs as production, with exactly ONE deliberate difference: roles are present.
            // Anything else differing would manufacture a delta that says nothing about enforcement.
            AuthzInternalRequest shadowRequest = new AuthzInternalRequest(
                    row.getTenantId(), session.actorId() != null ? session.actorId() : ctx.get("actorId"),
                    projection.legacyActorType(), roles, ctx.get("purposeOfUse"),
                    ctx.get("deviceFingerprint"), row.getCorrelationId(),
                    uuid(ctx.get("facilityId")), uuid(ctx.get("workspaceId")), ctx.get("shiftId"),
                    req.method(), req.path(), ctx.get("action"), ctx.get("resourceType"),
                    ctx.get("resourceId"), session.loaLevel(), session.sessionId(),
                    "", session.authenticationAssurance(),
                    providerId, ctx.get("departmentId"), ctx.get("wardId"), ctx.get("programmeId"),
                    ctx.get("subjectId"), ctx.get("assuranceLevel"),
                    ctx.get("escalationGrantId"), null, null);

            row.setAction(ctx.get("action"));
            row.setResourceType(ctx.get("resourceType"));

            AuthzResponse shadow = policyEngine.evaluate(shadowRequest);
            row.setPdpMillis((int) ((System.nanoTime() - started) / 1_000_000L));
            row.setShadowVerdict(shadow.verdict() != null ? shadow.verdict().name() : null);
            row.setShadowDenyReason(shadow.errorCode());
            row.setDeltaKind(classifyDelta(req.prodVerdict(), row.getShadowVerdict(),
                    req.prodActorType(), projection.legacyActorType()));
            row.setProbeOutcome(ShadowDecisionLogEntity.OUTCOME_OK);
        } catch (Exception e) {
            // Never rethrow with the request attached — the DTO redacts the bearer, but an
            // exception carrying the object at all is a path this must not take.
            log.warn("shadow evaluation failed for {} {} — recorded as PDP_ERROR",
                    req.method(), req.path());
            row.setProbeOutcome(ShadowDecisionLogEntity.OUTCOME_PDP_ERROR);
        }

        recorder.record(row);
        return ack();
    }

    /** Deliberately verdict-free: nothing here may become something a caller can act on. */
    private static ResponseEntity<Map<String, String>> ack() {
        return ResponseEntity.accepted().body(Map.of("recorded", "true"));
    }

    /**
     * PERMIT_TO_DENY is the finding that would break live traffic at enforcement and is reported
     * first. A verdict change outranks a classification change, so the checks are ordered.
     */
    static String classifyDelta(String prodVerdict, String shadowVerdict,
                                String prodActorType, String shadowActorType) {
        boolean prodAllow = "ALLOW".equalsIgnoreCase(prodVerdict) || "PERMIT".equalsIgnoreCase(prodVerdict);
        boolean shadowAllow = "ALLOW".equalsIgnoreCase(shadowVerdict) || "PERMIT".equalsIgnoreCase(shadowVerdict);
        if (prodVerdict != null && shadowVerdict != null) {
            if (prodAllow && !shadowAllow) return ShadowDecisionLogEntity.DELTA_PERMIT_TO_DENY;
            if (!prodAllow && shadowAllow) return ShadowDecisionLogEntity.DELTA_DENY_TO_PERMIT;
        }
        if (prodActorType != null && shadowActorType != null
                && !prodActorType.equalsIgnoreCase(shadowActorType)) {
            return ShadowDecisionLogEntity.DELTA_ACTOR_TYPE_CHANGE;
        }
        return ShadowDecisionLogEntity.DELTA_NONE;
    }

    /** Coarse grouping so latency and deltas can be read per surface rather than per URL. */
    static String routeClass(String path) {
        if (path == null) return "unknown";
        String p = path.toLowerCase();
        if (p.contains("/providers/")) return "provider-registry";
        if (p.contains("/facilit")) return "facility-registry";
        if (p.contains("/clinical") || p.contains("/pct/") || p.contains("/encounter")) return "clinical";
        if (p.contains("/identity") || p.contains("/auth/")) return "identity";
        if (p.contains("/shell/") || p.contains("/session/") || p.contains("/nompilo/")) return "shell-chrome";
        if (p.contains("/mobile/")) return "mobile";
        return "other";
    }

    private static UUID uuid(String v) {
        try { return (v == null || v.isBlank()) ? null : UUID.fromString(v.trim()); }
        catch (IllegalArgumentException e) { return null; }
    }
}
