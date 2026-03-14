package zw.gov.mohcc.impilo.federation.filter;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.companion.error.ErrorEnvelopeWriter;
import zw.gov.mohcc.impilo.federation.identity.PodIdentityVerifier;
import zw.gov.mohcc.impilo.federation.identity.PodVerificationResult;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

/**
 * Servlet filter that enforces federation pod identity on v1.1 API paths.
 *
 * <p>Applies only to {@code /internal/v1/**} and {@code /external/v1/**} paths,
 * matching the scoping contract from the tech-companion spec.</p>
 *
 * <p>For each matching request:
 * <ol>
 *   <li>Extracts X-Pod-ID header and Authorization Bearer token</li>
 *   <li>Delegates to {@link PodIdentityVerifier} for mTLS/JWT verification</li>
 *   <li>Validates that X-Pod-ID matches the pod_id in the JWT claims</li>
 *   <li>On failure, returns 403 with FEDERATION_IDENTITY_INVALID error envelope</li>
 * </ol>
 *
 * <p>National pod (pod_id="national") is always allowed through without
 * federation token verification, since national originates from the spine itself.</p>
 */
public class FederationIdentityFilter implements Filter {

    public static final String ERROR_CODE = "FEDERATION_IDENTITY_INVALID";

    private final PodIdentityVerifier podIdentityVerifier;

    public FederationIdentityFilter(PodIdentityVerifier podIdentityVerifier) {
        this.podIdentityVerifier = podIdentityVerifier;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpReq = (HttpServletRequest) request;
        HttpServletResponse httpRes = (HttpServletResponse) response;

        String path = httpReq.getRequestURI();

        // Only enforce on v1.1 API paths
        if (!isFederationPath(path)) {
            chain.doFilter(request, response);
            return;
        }

        String podId = httpReq.getHeader(CompanionHeaders.POD_ID);
        String requestId = httpReq.getHeader(CompanionHeaders.REQUEST_ID);
        String correlationId = httpReq.getHeader(CompanionHeaders.CORRELATION_ID);

        // Auto-generate IDs if absent (for error envelope)
        if (requestId == null || requestId.isBlank()) {
            requestId = UUID.randomUUID().toString();
        }
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }

        // If no pod ID header, let V11HeaderFilter handle it (it checks hard-required headers)
        if (podId == null || podId.isBlank()) {
            chain.doFilter(request, response);
            return;
        }

        // National pod bypasses federation identity verification
        if ("national".equalsIgnoreCase(podId)) {
            chain.doFilter(request, response);
            return;
        }

        // Extract Bearer token
        String authHeader = httpReq.getHeader(CompanionHeaders.AUTHORIZATION);
        String jwtToken = null;
        if (authHeader != null && authHeader.startsWith(CompanionHeaders.BEARER_PREFIX)) {
            jwtToken = authHeader.substring(CompanionHeaders.BEARER_PREFIX.length());
        }

        // Extract client certificate (from mTLS proxy header, if present)
        String clientCert = httpReq.getHeader("X-Client-Certificate");

        // Verify pod identity
        PodVerificationResult result = podIdentityVerifier.verify(clientCert, jwtToken);

        if (!result.verified()) {
            ErrorEnvelopeWriter.write(httpRes, 403,
                    ERROR_CODE,
                    "Pod identity verification failed: " + result.reason(),
                    Map.of("pod_id", podId, "reason", result.reason()),
                    requestId, correlationId);
            return;
        }

        // Verify that the verified pod ID matches the header claim
        if (!podId.equalsIgnoreCase(result.podId())) {
            ErrorEnvelopeWriter.write(httpRes, 403,
                    ERROR_CODE,
                    "X-Pod-ID header '" + podId + "' does not match verified identity '" + result.podId() + "'",
                    Map.of("claimed_pod_id", podId, "verified_pod_id", result.podId()),
                    requestId, correlationId);
            return;
        }

        chain.doFilter(request, response);
    }

    /**
     * Determines if a path is subject to federation identity enforcement.
     * Matches /internal/v1/** and /external/v1/** paths.
     */
    static boolean isFederationPath(String path) {
        return path != null
                && (path.startsWith("/internal/v1/") || path.startsWith("/external/v1/"));
    }
}
