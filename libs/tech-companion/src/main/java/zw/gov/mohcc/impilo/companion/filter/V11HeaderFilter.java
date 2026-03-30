package zw.gov.mohcc.impilo.companion.filter;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.companion.context.RequestContext;
import zw.gov.mohcc.impilo.companion.context.RequestContextHolder;
import zw.gov.mohcc.impilo.companion.error.ErrorCodes;
import zw.gov.mohcc.impilo.companion.error.ErrorEnvelopeWriter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * v1.1 header enforcement filter (reusable across all Impilo services).
 *
 * Applies to {@code /internal/v1/**} and {@code /external/v1/**} paths.
 *
 * Hard rule: ALL FOUR headers must be present and non-blank:
 *   X-Tenant-ID, X-Pod-ID, X-Request-ID, X-Correlation-ID
 * If any missing => HTTP 400 with structured error envelope.
 * The error envelope always includes request_id and correlation_id
 * (auto-generated UUIDs when the caller didn't supply them).
 *
 * Populates {@link RequestContext} on the thread-local {@link RequestContextHolder}.
 *
 * Skip paths: {@code /actuator/**}, {@code /swagger/**}, {@code /v3/api-docs/**}
 *
 * Register as a Spring bean or manually in FilterRegistrationBean at Order 10.
 */
public class V11HeaderFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpReq = (HttpServletRequest) request;
        HttpServletResponse httpRes = (HttpServletResponse) response;

        String path = httpReq.getRequestURI();

        // Skip CORS preflight — OPTIONS requires no trust headers
        if ("OPTIONS".equalsIgnoreCase(httpReq.getMethod())) {
            chain.doFilter(request, response);
            return;
        }

        // Only enforce on v1.1 paths
        if (!isV11Path(path)) {
            chain.doFilter(request, response);
            return;
        }

        // Check all four hard-required headers
        List<String> missing = new ArrayList<>();
        for (String header : CompanionHeaders.HARD_REQUIRED) {
            String value = httpReq.getHeader(header);
            if (value == null || value.isBlank()) {
                missing.add(header);
            }
        }

        // Extract request/correlation IDs for envelope (auto-generate if absent)
        String requestId = httpReq.getHeader(CompanionHeaders.REQUEST_ID);
        String correlationId = httpReq.getHeader(CompanionHeaders.CORRELATION_ID);

        if (requestId == null || requestId.isBlank()) {
            requestId = UUID.randomUUID().toString();
        }
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }

        if (!missing.isEmpty()) {
            ErrorEnvelopeWriter.write(httpRes, 400,
                    ErrorCodes.MISSING_REQUIRED_HEADER,
                    "Required v1.1 headers missing: " + missing,
                    Map.of("missing", missing),
                    requestId, correlationId);
            return;
        }

        // Parse optional timeout
        Long timeoutMs = null;
        String timeoutHeader = httpReq.getHeader(CompanionHeaders.CLIENT_TIMEOUT_MS);
        if (timeoutHeader != null && !timeoutHeader.isBlank()) {
            try {
                timeoutMs = Long.parseLong(timeoutHeader);
            } catch (NumberFormatException ignored) {
                // invalid timeout header is ignored, not a hard error
            }
        }

        // Extract auth token
        String authHeader = httpReq.getHeader(CompanionHeaders.AUTHORIZATION);
        String authToken = null;
        if (authHeader != null && authHeader.startsWith(CompanionHeaders.BEARER_PREFIX)) {
            authToken = authHeader.substring(CompanionHeaders.BEARER_PREFIX.length());
        }

        // Build and bind RequestContext
        RequestContext ctx = RequestContext.of(
                httpReq.getHeader(CompanionHeaders.TENANT_ID),
                httpReq.getHeader(CompanionHeaders.POD_ID),
                requestId,
                correlationId,
                authToken,
                httpReq.getUserPrincipal(),
                timeoutMs
        );
        RequestContextHolder.set(ctx);

        try {
            chain.doFilter(request, response);
        } finally {
            RequestContextHolder.clear();
        }
    }

    /**
     * Determines if a path is a v1.1 API path subject to header enforcement.
     */
    public static boolean isV11Path(String path) {
        return path != null
                && (path.startsWith("/internal/v1") || path.startsWith("/external/v1")
                || path.startsWith("/v1/internal") || path.startsWith("/v1/external")
                || path.startsWith("/v1/catalogs") || path.startsWith("/v1/artifacts")
                || path.startsWith("/mushex/v1"));
    }
}
