package zw.gov.mohcc.impilo.tshepo.authz.grpc;

import com.google.rpc.Code;
import com.google.rpc.Status;
import io.envoyproxy.envoy.service.auth.v3.*;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
 * gRPC implementation of the Envoy ext_authz Authorization service.
 *
 * <p>Envoy calls {@code Check()} on every inbound request (port 9090 gRPC).
 * This service extracts trust headers from the check request, optionally
 * validates the bearer token via the SessionAssuranceRouter, then delegates
 * to the PolicyEngine for the authorization decision.</p>
 *
 * <p>Returns:
 * <ul>
 *   <li>{@code OkHttpResponse} with obligation headers for ALLOW decisions</li>
 *   <li>{@code DeniedHttpResponse} with 403 for DENY decisions</li>
 *   <li>{@code DeniedHttpResponse} with 401 for STEP_UP_REQUIRED decisions</li>
 * </ul>
 * </p>
 */
@GrpcService
public class ExtAuthzGrpcService extends AuthorizationGrpc.AuthorizationImplBase {

    private static final Logger log = LoggerFactory.getLogger(ExtAuthzGrpcService.class);

    private final PolicyEngine policyEngine;
    private final SessionAssuranceRouter sessionRouter;

    public ExtAuthzGrpcService(PolicyEngine policyEngine,
                                SessionAssuranceRouter sessionRouter) {
        this.policyEngine = policyEngine;
        this.sessionRouter = sessionRouter;
    }

    @Override
    public void check(CheckRequest request, StreamObserver<CheckResponse> responseObserver) {
        try {
            // Extract headers from the check request
            Map<String, String> headers = Collections.emptyMap();
            String method = "";
            String path = "";

            if (request.hasAttributes() && request.getAttributes().hasRequest()
                    && request.getAttributes().getRequest().hasHttp()) {

                AttributeContext.Request.HttpRequest http =
                        request.getAttributes().getRequest().getHttp();
                headers = http.getHeadersMap();
                method = http.getMethod();
                path = http.getPath();
            }

            // Extract trust headers
            String tenantIdStr = headers.getOrDefault(TrustHeaders.TENANT_ID, "");
            String actorId = headers.getOrDefault(TrustHeaders.ACTOR_ID, "");
            String actorType = headers.getOrDefault(TrustHeaders.ACTOR_TYPE, "");
            String purposeOfUse = headers.getOrDefault(TrustHeaders.PURPOSE_OF_USE, "");
            String deviceFingerprint = headers.getOrDefault(TrustHeaders.DEVICE_FINGERPRINT, "");
            String correlationIdStr = headers.getOrDefault(TrustHeaders.CORRELATION_ID, "");
            String facilityIdStr = headers.getOrDefault(TrustHeaders.FACILITY_ID, "");
            String workspaceIdStr = headers.getOrDefault(TrustHeaders.WORKSPACE_ID, "");
            String shiftId = headers.getOrDefault(TrustHeaders.SHIFT_ID, "");
            String providerId = headers.getOrDefault(TrustHeaders.PROVIDER_ID, "");
            String departmentId = headers.getOrDefault(TrustHeaders.DEPARTMENT_ID, "");
            String wardId = headers.getOrDefault(TrustHeaders.WARD_ID, "");
            String programmeId = headers.getOrDefault(TrustHeaders.PROGRAMME_ID, "");
            String subjectId = headers.getOrDefault(TrustHeaders.SUBJECT_ID, "");
            String assuranceLevel = headers.getOrDefault(TrustHeaders.ASSURANCE_LEVEL, "");
            String authorization = headers.getOrDefault("authorization", "");

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

            if (!authorization.isBlank()) {
                try {
                    SessionInfo session = sessionRouter.validateSession(authorization);
                    roles = session.roles();
                    loaLevel = session.loaLevel();
                    sessionId = session.sessionId();

                    // Session info can override/enrich header values
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
                    // Don't deny yet — let PolicyEngine handle missing actor context
                }
            }

            // Derive action and resource type from path
            String action = AuthzInternalRequest.deriveAction(method, path);
            String resourceType = AuthzInternalRequest.deriveResourceType(path);
            String resourceId = AuthzInternalRequest.deriveResourceId(path);

            // Build internal request
            AuthzInternalRequest authzRequest = new AuthzInternalRequest(
                    tenantId, actorId, actorType, roles, purposeOfUse,
                    deviceFingerprint, correlationId, facilityId, workspaceId,
                    shiftId, method, path, action, resourceType, resourceId,
                    loaLevel, sessionId, authorization,
                    emptyToNull(providerId), emptyToNull(departmentId),
                    emptyToNull(wardId), emptyToNull(programmeId),
                    emptyToNull(subjectId), emptyToNull(assuranceLevel)
            );

            // Validate mandatory headers
            if (tenantId == null || actorId.isBlank() || actorType.isBlank() || purposeOfUse.isBlank()) {
                log.warn("gRPC ext_authz DENY: missing mandatory trust headers, correlation={}",
                        correlationId);
                responseObserver.onNext(buildDeniedResponse(403,
                        "Missing mandatory trust headers: x-tenant-id, x-actor-id, x-actor-type, x-purpose-of-use"));
                responseObserver.onCompleted();
                return;
            }

            // Evaluate policy
            AuthzResponse authzResponse = policyEngine.evaluate(authzRequest);

            // Build gRPC response
            CheckResponse checkResponse = buildCheckResponse(authzResponse);
            responseObserver.onNext(checkResponse);
            responseObserver.onCompleted();

        } catch (Exception e) {
            log.error("gRPC ext_authz error: {}", e.getMessage(), e);
            responseObserver.onNext(buildDeniedResponse(500,
                    "Internal authorization error"));
            responseObserver.onCompleted();
        }
    }

    private CheckResponse buildCheckResponse(AuthzResponse authzResponse) {
        if (authzResponse.verdict() == Verdict.ALLOW) {
            // Build OkHttpResponse with obligation headers
            OkHttpResponse.Builder okBuilder = OkHttpResponse.newBuilder();

            if (authzResponse.headerMutations() != null) {
                for (Map.Entry<String, String> entry : authzResponse.headerMutations().entrySet()) {
                    okBuilder.addHeaders(HeaderValueOption.newBuilder()
                            .setHeader(HeaderValue.newBuilder()
                                    .setKey(entry.getKey())
                                    .setValue(entry.getValue())
                                    .build())
                            .setAppend(false)
                            .build());
                }
            }

            return CheckResponse.newBuilder()
                    .setStatus(Status.newBuilder().setCode(Code.OK_VALUE).build())
                    .setOkResponse(okBuilder.build())
                    .build();

        } else if (authzResponse.verdict() == Verdict.STEP_UP_REQUIRED) {
            return buildDeniedResponse(401,
                    "{\"error\":\"STEP_UP_REQUIRED\",\"methods\":" +
                    (authzResponse.stepUpMethods() != null ? authzResponse.stepUpMethods() : "[]") +
                    "}");

        } else {
            // DENY
            return buildDeniedResponse(403,
                    "{\"error\":\"" + authzResponse.errorCode() +
                    "\",\"message\":\"" + sanitize(authzResponse.errorMessage()) + "\"}");
        }
    }

    private CheckResponse buildDeniedResponse(int httpStatusCode, String body) {
        HttpStatus.StatusCode statusCode = switch (httpStatusCode) {
            case 401 -> HttpStatus.StatusCode.Unauthorized;
            case 403 -> HttpStatus.StatusCode.Forbidden;
            case 500 -> HttpStatus.StatusCode.InternalServerError;
            default -> HttpStatus.StatusCode.Forbidden;
        };

        int rpcCode = switch (httpStatusCode) {
            case 401 -> Code.UNAUTHENTICATED_VALUE;
            case 403 -> Code.PERMISSION_DENIED_VALUE;
            case 500 -> Code.INTERNAL_VALUE;
            default -> Code.PERMISSION_DENIED_VALUE;
        };

        DeniedHttpResponse denied = DeniedHttpResponse.newBuilder()
                .setStatus(HttpStatus.newBuilder().setCode(statusCode).build())
                .addHeaders(HeaderValueOption.newBuilder()
                        .setHeader(HeaderValue.newBuilder()
                                .setKey("content-type")
                                .setValue("application/json")
                                .build())
                        .build())
                .setBody(body)
                .build();

        return CheckResponse.newBuilder()
                .setStatus(Status.newBuilder().setCode(rpcCode).build())
                .setDeniedResponse(denied)
                .build();
    }

    private UUID parseUuid(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return UUID.fromString(value.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private String emptyToNull(String value) {
        return (value == null || value.isBlank()) ? null : value;
    }

    private String sanitize(String input) {
        if (input == null) return "";
        return input.replace("\"", "'").replace("\n", " ");
    }
}
