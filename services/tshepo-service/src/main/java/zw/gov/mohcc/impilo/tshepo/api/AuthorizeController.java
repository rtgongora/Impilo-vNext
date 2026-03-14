package zw.gov.mohcc.impilo.tshepo.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.tshepo.core.Decision;
import zw.gov.mohcc.impilo.tshepo.core.PolicyEngine;
import zw.gov.mohcc.impilo.tshepo.core.TrustHeaders;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Envoy ext_authz HTTP endpoint.
 *
 * Envoy forwards the original request's method, path, and headers here.
 * This controller parses trust headers, delegates to PolicyEngine,
 * and returns:
 *   - 200 OK with obligation headers → ALLOW (Envoy forwards to upstream)
 *   - 403 Forbidden → DENY (Envoy rejects the request)
 *   - 401 Unauthorized → STEP_UP_REQUIRED (client must complete challenge)
 */
@RestController
@RequestMapping("/v1/authorize")
public class AuthorizeController {

    private static final Logger log = LoggerFactory.getLogger(AuthorizeController.class);

    private final PolicyEngine policyEngine;

    public AuthorizeController(PolicyEngine policyEngine) {
        this.policyEngine = policyEngine;
    }

    /**
     * Primary ext_authz check endpoint.
     * Envoy sends the original request method and path as headers.
     */
    @RequestMapping(method = {RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT,
                              RequestMethod.DELETE, RequestMethod.PATCH})
    public ResponseEntity<AuthorizeResponse> authorize(HttpServletRequest request) {

        // Extract the original method/path from Envoy's forwarded request
        String originalMethod = request.getHeader(":method") != null
            ? request.getHeader(":method")
            : request.getMethod();
        String originalPath = request.getHeader(":path") != null
            ? request.getHeader(":path")
            : request.getRequestURI();

        // Parse trust headers into an authorization request
        AuthorizationRequest authzRequest = AuthorizationRequest.fromHeaders(
            headerName -> request.getHeader(headerName),
            originalMethod,
            originalPath
        );

        log.debug("ext_authz check: actor={}, action={}, resource={}, purpose={}",
            authzRequest.actorId(),
            authzRequest.action(),
            authzRequest.resourceType(),
            authzRequest.purposeOfUse());

        // Validate mandatory headers
        if (!authzRequest.isValid()) {
            log.warn("ext_authz DENY: missing mandatory trust headers, correlation={}",
                authzRequest.correlationId());
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(AuthorizeResponse.denied("MISSING_HEADERS",
                    "Required trust headers missing: x-tenant-id, x-actor-id, x-actor-type, x-purpose-of-use"));
        }

        // Evaluate policy
        Decision decision = policyEngine.evaluate(authzRequest);

        // Build response
        return switch (decision.verdict()) {
            case ALLOW -> {
                log.debug("ext_authz ALLOW: actor={}, correlation={}",
                    authzRequest.actorId(), authzRequest.correlationId());

                ResponseEntity.BodyBuilder builder = ResponseEntity.ok();

                // Inject obligation headers for Envoy to forward to downstream service
                builder.header(TrustHeaders.DECISION, "ALLOW");

                // Policy decision evidence headers (v1.1 consistency class support)
                builder.header("X-Policy-Decision", "ALLOW");
                builder.header("X-Policy-Version", decision.policyVersion());
                if (decision.reasonCodes() != null && !decision.reasonCodes().isEmpty()) {
                    builder.header("X-Decision-Reason", String.join(",", decision.reasonCodes()));
                }

                if (decision.obligations() != null) {
                    builder.header(TrustHeaders.OBLIGATIONS, decision.obligations().toJson());
                    if (decision.obligations().maxScope() != null) {
                        builder.header(TrustHeaders.MAX_SCOPE, decision.obligations().maxScope());
                    }
                    if (decision.obligations().maskFields() != null && !decision.obligations().maskFields().isEmpty()) {
                        builder.header(TrustHeaders.MASK_FIELDS,
                            String.join(",", decision.obligations().maskFields()));
                    }
                    builder.header(TrustHeaders.LOGGING_LEVEL, decision.obligations().loggingLevel());
                }

                yield builder.body(AuthorizeResponse.allowed(decision.obligations()));
            }

            case DENY -> {
                log.warn("ext_authz DENY: actor={}, reason={}, correlation={}",
                    authzRequest.actorId(), decision.denyReason(), authzRequest.correlationId());
                yield ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .header("X-Policy-Decision", "DENY")
                    .header("X-Policy-Version", decision.policyVersion())
                    .header("X-Decision-Reason", decision.denyReason())
                    .body(AuthorizeResponse.denied(decision.denyReason(), decision.denyMessage()));
            }

            case STEP_UP_REQUIRED -> {
                log.info("ext_authz STEP_UP: actor={}, methods={}, correlation={}",
                    authzRequest.actorId(), decision.stepUpMethods(), authzRequest.correlationId());
                yield ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .header("X-Policy-Decision", "STEP_UP_REQUIRED")
                    .header("X-Policy-Version", decision.policyVersion())
                    .header("X-Decision-Reason", "STEP_UP_REQUIRED")
                    .body(AuthorizeResponse.stepUpRequired(decision.stepUpMethods()));
            }
        };
    }
}
