package zw.gov.mohcc.impilo.tshepo.authz.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.tshepo.authz.core.PolicyEngine;
import zw.gov.mohcc.impilo.tshepo.authz.dto.AuthzInternalRequest;
import zw.gov.mohcc.impilo.tshepo.authz.session.SessionAssuranceRouter;
import zw.gov.mohcc.impilo.tshepo.authz.session.SessionInfo;
import zw.gov.mohcc.impilo.tshepo.authz.session.SessionValidationException;
import zw.gov.mohcc.impilo.tshepo.contracts.dto.AuthzResponse;
import zw.gov.mohcc.impilo.tshepo.contracts.dto.AuthenticationAssurance;
import zw.gov.mohcc.impilo.tshepo.contracts.enums.Verdict;
import zw.gov.mohcc.impilo.tshepo.contracts.headers.TrustHeaders;

import java.util.*;

/**
 * HTTP ext_authz endpoint.
 *
 * <p>Envoy can call this endpoint for ext_authz HTTP mode as an alternative
 * to the gRPC endpoint. The logic is identical: extract trust headers from
 * the forwarded request, validate the session, delegate to PolicyEngine,
 * and return the decision with obligation headers.</p>
 *
 * <p>Returns:
 * <ul>
 *   <li>200 OK with obligation headers → ALLOW (Envoy forwards to upstream)</li>
 *   <li>403 Forbidden → DENY (Envoy rejects the request)</li>
 *   <li>401 Unauthorized → STEP_UP_REQUIRED (client must complete challenge)</li>
 * </ul>
 * </p>
 */
@RestController
@RequestMapping("/v1/authorize")
public class AuthorizeController {

    private static final Logger log = LoggerFactory.getLogger(AuthorizeController.class);

    /** The purpose that short-circuits Step 4 and skips consent; see the guard in authorize(). */
    static final String PURPOSE_SYSTEM = "SYSTEM";

    /**
     * Actor types that may legitimately assert {@link #PURPOSE_SYSTEM}, as documented on
     * {@code SessionInfo.actorType} (PROVIDER, ADMIN, CITIZEN, SYSTEM, SERVICE). Only ever
     * consulted with a value taken from a VALIDATED token — never the client's header copy.
     */
    private static final java.util.Set<String> WORKLOAD_ACTOR_TYPES =
            java.util.Set.of("SYSTEM", "SERVICE");

    private final PolicyEngine policyEngine;
    private final SessionAssuranceRouter sessionRouter;
    private final ObjectMapper objectMapper;
    private final zw.gov.mohcc.impilo.tshepo.authz.service.IdentityIntrospectionClient introspectionClient;

    public AuthorizeController(PolicyEngine policyEngine,
                                SessionAssuranceRouter sessionRouter,
                                ObjectMapper objectMapper,
                                zw.gov.mohcc.impilo.tshepo.authz.service.IdentityIntrospectionClient introspectionClient) {
        this.policyEngine = policyEngine;
        this.sessionRouter = sessionRouter;
        this.objectMapper = objectMapper;
        this.introspectionClient = introspectionClient;
    }

    /**
     * Primary HTTP ext_authz check endpoint.
     * Accepts all HTTP methods — Envoy forwards the original request's method.
     */
    /**
     * Mapped at both {@code /v1/authorize} and {@code /v1/authorize/**}.
     *
     * <p>The wildcard is not cosmetic. Envoy's HTTP ext_authz filter prepends {@code path_prefix}
     * to the original request path, so it calls
     * {@code /v1/authorize/api/v1/patients/search} — never the bare endpoint. Without a handler
     * there, two things failed at once: Spring MVC had nothing to dispatch to, and Spring
     * Security's {@code MvcRequestMatcher} — which matches against the handler mapping, not the
     * raw string — could not match {@code /v1/authorize/**} either, so the permitAll entry was
     * skipped and the request fell through to {@code anyRequest().authenticated()} and answered
     * <strong>401</strong>. With {@code failure_mode_allow: false} that denies every request in
     * the estate, so the whole ext_authz integration could never have worked.</p>
     */
    @RequestMapping(value = {"", "/**"},
                    method = {RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT,
                              RequestMethod.DELETE, RequestMethod.PATCH, RequestMethod.HEAD,
                              RequestMethod.OPTIONS})
    public ResponseEntity<AuthzResponse> authorize(HttpServletRequest request) {

        // Extract the original method/path being asked about.
        //
        // Precedence: Envoy's pseudo-headers, then the HTTP/1.1-safe aliases, then the servlet's
        // own values. The aliases exist because a service calling this endpoint directly cannot
        // send `:method`/`:path` -- Java rejects a colon-prefixed header name outright -- and the
        // servlet fallback would evaluate the literal `POST /v1/authorize`, which matches no rule
        // and denies. Every in-process caller in experience-bff hit exactly that.
        String originalMethod = firstPresent(
                request.getHeader(":method"),
                request.getHeader(TrustHeaders.ORIGINAL_METHOD),
                request.getMethod());
        String originalPath = firstPresent(
                request.getHeader(":path"),
                request.getHeader(TrustHeaders.ORIGINAL_PATH),
                stripAuthorizePrefix(request.getRequestURI()));

        // Extract trust headers
        String tenantIdStr = request.getHeader(TrustHeaders.TENANT_ID);
        String actorId = request.getHeader(TrustHeaders.ACTOR_ID);
        String actorType = request.getHeader(TrustHeaders.ACTOR_TYPE);
        String purposeOfUse = request.getHeader(TrustHeaders.PURPOSE_OF_USE);
        String deviceFingerprint = request.getHeader(TrustHeaders.DEVICE_FINGERPRINT);
        String correlationIdStr = request.getHeader(TrustHeaders.CORRELATION_ID);
        String facilityIdStr = request.getHeader(TrustHeaders.FACILITY_ID);
        String workspaceIdStr = request.getHeader(TrustHeaders.WORKSPACE_ID);
        String shiftId = request.getHeader(TrustHeaders.SHIFT_ID);
        String providerId = request.getHeader(TrustHeaders.PROVIDER_ID);
        String departmentId = request.getHeader(TrustHeaders.DEPARTMENT_ID);
        String wardId = request.getHeader(TrustHeaders.WARD_ID);
        String programmeId = request.getHeader(TrustHeaders.PROGRAMME_ID);
        String subjectId = request.getHeader(TrustHeaders.SUBJECT_ID);
        String assuranceLevel = request.getHeader(TrustHeaders.ASSURANCE_LEVEL);
        String escalationGrantId = request.getHeader(TrustHeaders.ESCALATION_GRANT_ID);
        String workflowContext = request.getHeader(TrustHeaders.WORKFLOW_STATE);
        String workContextToken = request.getHeader(TrustHeaders.WORK_CONTEXT_TOKEN);
        String authorization = request.getHeader("authorization");

        // Parse UUIDs
        UUID tenantId = parseUuid(tenantIdStr);
        UUID correlationId = parseUuid(correlationIdStr);
        if (correlationId == null) {
            correlationId = UUID.randomUUID();
        }
        UUID facilityId = parseUuid(facilityIdStr);
        UUID workspaceId = parseUuid(workspaceIdStr);

        // Session assurance — validate bearer token if present
        List<String> roles = List.of();
        int loaLevel = 0;
        String sessionId = null;
        // The actor type as the VALIDATED TOKEN states it — null when no token validated. Kept
        // separate from `actorType`, which may still hold the client's own claim.
        String sessionActorType = null;
        AuthenticationAssurance authenticationAssurance = AuthenticationAssurance.none();

        if (authorization != null && !authorization.isBlank()) {
            try {
                SessionInfo session = sessionRouter.validateSession(authorization);
                roles = session.roles();
                loaLevel = session.loaLevel();
                sessionId = session.sessionId();
                authenticationAssurance = session.authenticationAssurance();

                // JWT identity is AUTHORITATIVE over client-supplied headers (TPL-1): a valid
                // session's sub/type/tenant OVERRIDE any X-Actor-ID/X-Actor-Type/X-Tenant-ID the
                // client set, preventing header-injection impersonation. The authoritative identity
                // is re-injected upstream by PolicyEngine.buildHeaderMutations, so downstream
                // services receive it too. Client headers are used only when the session does not
                // carry the claim (e.g. service-to-service tokens with no sub).
                if (session.actorId() != null && !session.actorId().isBlank()) {
                    if (actorId != null && !actorId.isBlank() && !actorId.equals(session.actorId())) {
                        log.warn("Trust-header override: client X-Actor-ID differs from session sub — using session (correlation={})",
                                correlationId);
                    }
                    actorId = session.actorId();
                }
                if (session.actorType() != null && !session.actorType().isBlank()) {
                    actorType = session.actorType();
                }
                if (session.tenantId() != null) {
                    tenantId = session.tenantId();
                }
                sessionActorType = session.actorType();
            } catch (SessionValidationException e) {
                log.warn("Session validation failed: {} — {}", e.getErrorCode(), e.getMessage());
            }
        }

        // ────────────────────────────────────────────────────────────────
        // TPL-1, extended to the privileged purpose.
        //
        // SYSTEM is not an ordinary purpose: PolicyEngine short-circuits Step 4 for it in BOTH
        // branches (no rules, and no matching rule), and requiresConsent returns false for it. It
        // is therefore the one purpose that reaches a clinical resource with NO policy rule and NO
        // consent evaluation. Until now it was read straight from x-purpose-of-use with nothing
        // checking who may assert it, and Envoy leaves that header client-supplied by design
        // (a retained gap in checkpoint-4/HEADER_CONTAINMENT.md).
        //
        // Measured 2026-08-07 on the live preview estate: sending this header alone turned a
        // 403 DENY/NO_ALLOW_RULE into a 200 ALLOW carrying piiAccess=FULL and clinicalAccess=FULL,
        // with an invented actor id and no bearer token.
        //
        // So it is treated exactly like actorId/actorType/tenantId above: a claim about the caller
        // is worth only what the validated token says. The same reasoning as the break-glass fix —
        // an actor-supplied field that the decision point consults ABOUT that actor is an oracle,
        // not evidence.
        //
        // Safe to fail closed: across 1,952 live decisions, ZERO carried purpose SYSTEM, so no
        // existing caller depends on it. A genuine workload presents a token whose actor type is
        // SYSTEM or SERVICE; anything else asking for SYSTEM is refused rather than downgraded,
        // because silently rewriting a caller's declared purpose would make the audit trail lie.
        // ────────────────────────────────────────────────────────────────
        boolean systemPurposeRefused = PURPOSE_SYSTEM.equalsIgnoreCase(trim(purposeOfUse))
                && !isWorkloadSession(sessionActorType, roles);

        // Validate mandatory headers
        if (tenantId == null || actorId == null || actorId.isBlank()
                || actorType == null || actorType.isBlank()
                || purposeOfUse == null || purposeOfUse.isBlank()) {
            log.warn("ext_authz DENY: missing mandatory trust headers, correlation={}", correlationId);
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(AuthzResponse.deny("MISSING_HEADERS",
                            "Required trust headers missing: x-tenant-id, x-actor-id, x-actor-type, x-purpose-of-use",
                            0));
        }

        // Derive action and resource type from path
        String action = AuthzInternalRequest.deriveAction(originalMethod, originalPath);
        String resourceType = AuthzInternalRequest.deriveResourceType(originalPath);
        String resourceId = AuthzInternalRequest.deriveResourceId(originalPath);

        // ────────────────────────────────────────────────────────────────
        // Refuse the SYSTEM purpose here — before the engine, and audited.
        //
        // Decided above (while the session was in scope) but acted on here, for two reasons.
        // First, tenant_id and actor_id are NOT NULL in policy_decision_log, so a decision can only
        // be recorded once the mandatory-header check has passed. Second, the refusal must still
        // land BEFORE PolicyEngine.evaluate: SYSTEM short-circuits Step 4 in both branches and
        // skips consent, so refusing after evaluation would be refusing after the bypass.
        //
        // recordPrePolicyDenial writes policy_decision_log + the audit outbox WITHOUT evaluating
        // anything. Without it this refusal left no decision row, so a detection query on
        // purpose_of_use='SYSTEM' would fall silent once the guard shipped — and silence would mean
        // "blocked", not "not attempted".
        //
        // The duty-token introspection below is deliberately not reached: a refused request should
        // not cause an outbound call.
        // ────────────────────────────────────────────────────────────────
        if (systemPurposeRefused) {
            log.warn("ext_authz DENY: purpose SYSTEM asserted without a validated workload session "
                            + "(sessionActorType={}, sessionRoles={}, actor={}, path={}, correlation={})",
                    sessionActorType, roles, actorId, originalPath, correlationId);
            AuthzResponse refusal = policyEngine.recordPrePolicyDenial(
                    new AuthzInternalRequest(
                            tenantId, actorId, actorType, roles, purposeOfUse,
                            deviceFingerprint, correlationId, facilityId, workspaceId,
                            shiftId, originalMethod, originalPath, action, resourceType,
                            resourceId, loaLevel, sessionId, "",
                            providerId, departmentId, wardId, programmeId,
                            subjectId, assuranceLevel, escalationGrantId, workflowContext,
                            zw.gov.mohcc.impilo.tshepo.authz.dto.DutyContext.absent()),
                    "PURPOSE_NOT_PERMITTED",
                    "The SYSTEM purpose of use may only be asserted by a workload identity "
                            + "established from a validated token.");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(refusal);
        }

        // Resolve the WORK_CONTEXT duty token (Vashandi-proven context) via introspection.
        // Fail-open on error; SHADOW/ENFORCE handling is in the PolicyEngine.
        zw.gov.mohcc.impilo.tshepo.authz.dto.DutyContext dutyContext =
                introspectionClient.introspect(workContextToken);

        // Build internal request
        AuthzInternalRequest authzRequest = new AuthzInternalRequest(
                tenantId, actorId, actorType, roles, purposeOfUse,
                deviceFingerprint, correlationId, facilityId, workspaceId,
                shiftId, originalMethod, originalPath, action, resourceType,
                resourceId, loaLevel, sessionId,
                authorization != null ? authorization : "",
                authenticationAssurance,
                providerId, departmentId, wardId, programmeId,
                subjectId, assuranceLevel,
                escalationGrantId, workflowContext,
                dutyContext
        );

        log.debug("ext_authz check: actor={}, action={}, resource={}, purpose={}, correlation={}, " +
                "provider={}, department={}, ward={}, programme={}, subject={}, assurance={}",
                actorId, action, resourceType, purposeOfUse, correlationId,
                providerId, departmentId, wardId, programmeId, subjectId, assuranceLevel);

        // Evaluate policy
        AuthzResponse authzResponse = policyEngine.evaluate(authzRequest);

        // Build HTTP response
        return switch (authzResponse.verdict()) {
            case ALLOW -> {
                log.debug("ext_authz ALLOW: actor={}, correlation={}", actorId, correlationId);

                ResponseEntity.BodyBuilder builder = ResponseEntity.ok();

                // Inject obligation headers
                if (authzResponse.headerMutations() != null) {
                    for (Map.Entry<String, String> entry : authzResponse.headerMutations().entrySet()) {
                        builder.header(entry.getKey(), entry.getValue());
                    }
                }

                yield builder.body(authzResponse);
            }
            case DENY -> {
                log.warn("ext_authz DENY: actor={}, reason={}, correlation={}",
                        actorId, authzResponse.errorCode(), correlationId);
                yield ResponseEntity.status(HttpStatus.FORBIDDEN).body(authzResponse);
            }
            case STEP_UP_REQUIRED -> {
                log.info("ext_authz STEP_UP: actor={}, methods={}, correlation={}",
                        actorId, authzResponse.stepUpMethods(), correlationId);
                yield ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(authzResponse);
            }
        };
    }

    /**
     * First non-blank value.
     *
     * <p>Blank is treated as absent deliberately: a header present but empty must fall through to
     * the next source rather than authorize an empty path, which would match no rule and produce a
     * denial whose cause is invisible.</p>
     */
    /** The prefix Envoy prepends to the original path ({@code http_service.path_prefix}). */
    static final String AUTHORIZE_PREFIX = "/v1/authorize";

    /**
     * Recover the caller's own path from the URI Envoy actually requested.
     *
     * <p>Envoy asks about {@code GET /api/v1/patients/search} by calling
     * {@code /v1/authorize/api/v1/patients/search}. Left as-is, the servlet URI would be fed to
     * {@code deriveResourceType}, which takes the last meaningful path segment — so the resource
     * would be derived from the caller's trailing id rather than its resource, and the decision
     * would be made about the wrong thing.
     *
     * <p>Returns {@code null} for the bare endpoint rather than {@code ""}, so
     * {@link #firstPresent} treats it as absent and the servlet's own URI is never mistaken for
     * a real subject path. A direct caller that supplies neither the pseudo-header nor
     * {@code x-original-path} then authorizes {@code /v1/authorize} itself, which matches no
     * rule and denies — visibly, rather than by silently evaluating an empty path.
     */
    static String stripAuthorizePrefix(String uri) {
        if (uri == null || !uri.startsWith(AUTHORIZE_PREFIX)) {
            return uri;
        }
        String remainder = uri.substring(AUTHORIZE_PREFIX.length());
        if (remainder.isEmpty() || remainder.equals("/")) {
            return null;
        }
        // Only a path-segment boundary counts: "/v1/authorizerext" is a different endpoint,
        // not this one with a suffix.
        return remainder.startsWith("/") ? remainder : uri;
    }

    private static String firstPresent(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    /** Null-safe trim; a blank header and an absent one must behave identically. */
    private static String trim(String value) {
        return value == null ? null : value.trim();
    }

    /**
     * Whether the VALIDATED token describes a workload.
     *
     * <p>Both inputs are token-derived: {@code sessionActorType} is null unless a session
     * validated, and {@code sessionRoles} is only ever assigned from {@code session.roles()} —
     * it is never populated from a client header. Passing the client-supplied {@code actorType}
     * here would reintroduce the very hole this closes, because the caller would be answering a
     * question about themselves.
     *
     * <p><strong>Roles are checked, not just the actor type.</strong> A service-to-service token in
     * this estate carries no {@code sub} and no actor-type claim — the workload identity arrives as
     * a ROLE. Gating on actor type alone refused every legitimate s2s caller, which
     * {@code AuthorizeControllerTest.clientHeadersUsedWhenSessionLacksClaims_serviceToken} and
     * {@code …envoyPrefixedPathIsAuthorizedAsTheCallersOwnPath} both caught.
     */
    private static boolean isWorkloadSession(String sessionActorType, List<String> sessionRoles) {
        if (sessionActorType != null
                && WORKLOAD_ACTOR_TYPES.contains(sessionActorType.trim().toUpperCase(Locale.ROOT))) {
            return true;
        }
        return sessionRoles != null && sessionRoles.stream()
                .filter(Objects::nonNull)
                .map(r -> r.trim().toUpperCase(Locale.ROOT))
                .anyMatch(WORKLOAD_ACTOR_TYPES::contains);
    }

    private UUID parseUuid(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return UUID.fromString(value.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
