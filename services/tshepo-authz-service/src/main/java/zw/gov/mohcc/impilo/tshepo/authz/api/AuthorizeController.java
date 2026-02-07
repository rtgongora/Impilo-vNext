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

    private final PolicyEngine policyEngine;
    private final SessionAssuranceRouter sessionRouter;
    private final ObjectMapper objectMapper;

    public AuthorizeController(PolicyEngine policyEngine,
                                SessionAssuranceRouter sessionRouter,
                                ObjectMapper objectMapper) {
        this.policyEngine = policyEngine;
        this.sessionRouter = sessionRouter;
        this.objectMapper = objectMapper;
    }

    /**
     * Primary HTTP ext_authz check endpoint.
     * Accepts all HTTP methods — Envoy forwards the original request's method.
     */
    @RequestMapping(method = {RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT,
                              RequestMethod.DELETE, RequestMethod.PATCH, RequestMethod.HEAD,
                              RequestMethod.OPTIONS})
    public ResponseEntity<AuthzResponse> authorize(HttpServletRequest request) {

        // Extract original method/path from Envoy forwarded request
        String originalMethod = request.getHeader(":method") != null
                ? request.getHeader(":method")
                : request.getMethod();
        String originalPath = request.getHeader(":path") != null
                ? request.getHeader(":path")
                : request.getRequestURI();

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

        if (authorization != null && !authorization.isBlank()) {
            try {
                SessionInfo session = sessionRouter.validateSession(authorization);
                roles = session.roles();
                loaLevel = session.loaLevel();
                sessionId = session.sessionId();

                // Enrich from session
                if ((actorId == null || actorId.isBlank()) && session.actorId() != null) {
                    actorId = session.actorId();
                }
                if ((actorType == null || actorType.isBlank()) && session.actorType() != null) {
                    actorType = session.actorType();
                }
                if (tenantId == null && session.tenantId() != null) {
                    tenantId = session.tenantId();
                }
            } catch (SessionValidationException e) {
                log.warn("Session validation failed: {} — {}", e.getErrorCode(), e.getMessage());
            }
        }

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

        // Build internal request
        AuthzInternalRequest authzRequest = new AuthzInternalRequest(
                tenantId, actorId, actorType, roles, purposeOfUse,
                deviceFingerprint, correlationId, facilityId, workspaceId,
                shiftId, originalMethod, originalPath, action, resourceType,
                resourceId, loaLevel, sessionId,
                authorization != null ? authorization : ""
        );

        log.debug("ext_authz check: actor={}, action={}, resource={}, purpose={}, correlation={}",
                actorId, action, resourceType, purposeOfUse, correlationId);

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

    private UUID parseUuid(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return UUID.fromString(value.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
