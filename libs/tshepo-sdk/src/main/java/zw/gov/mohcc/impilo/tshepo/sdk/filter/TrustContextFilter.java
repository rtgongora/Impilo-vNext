package zw.gov.mohcc.impilo.tshepo.sdk.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.OncePerRequestFilter;
import zw.gov.mohcc.impilo.tshepo.contracts.enums.AccessMode;
import zw.gov.mohcc.impilo.tshepo.contracts.headers.TrustHeaders;
import zw.gov.mohcc.impilo.tshepo.sdk.TrustContext;
import zw.gov.mohcc.impilo.tshepo.sdk.TrustContextHolder;

import java.io.IOException;
import java.util.UUID;

/**
 * OncePerRequestFilter that extracts trust headers from every inbound HTTP request,
 * builds a {@link TrustContext}, and stores it in {@link TrustContextHolder}.
 *
 * <p>Mandatory headers: X-Tenant-Id, X-Actor-Id, X-Actor-Type,
 * X-Purpose-Of-Use, X-Device-Fingerprint, X-Correlation-Id.
 * Requests missing mandatory headers are rejected with 400.</p>
 *
 * <p>Add this filter to downstream services that need trust context.
 * TSHEPO's ext_authz decision happens upstream in Envoy; this filter
 * only reads the headers that Envoy has already validated.</p>
 */
public class TrustContextFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(TrustContextFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                     HttpServletResponse response,
                                     FilterChain chain) throws ServletException, IOException {
        try {
            String tenantIdStr = request.getHeader(TrustHeaders.TENANT_ID);
            String actorId = request.getHeader(TrustHeaders.ACTOR_ID);
            String actorType = request.getHeader(TrustHeaders.ACTOR_TYPE);
            String purposeOfUse = request.getHeader(TrustHeaders.PURPOSE_OF_USE);
            String deviceFingerprint = request.getHeader(TrustHeaders.DEVICE_FINGERPRINT);
            String correlationIdStr = request.getHeader(TrustHeaders.CORRELATION_ID);

            // Validate mandatory headers
            if (tenantIdStr == null || actorId == null || actorType == null
                    || purposeOfUse == null || deviceFingerprint == null || correlationIdStr == null) {
                log.warn("Missing mandatory trust headers on {} {}",
                        request.getMethod(), request.getRequestURI());
                response.sendError(400, "Missing mandatory trust headers");
                return;
            }

            UUID tenantId;
            UUID correlationId;
            try {
                tenantId = UUID.fromString(tenantIdStr);
                correlationId = UUID.fromString(correlationIdStr);
            } catch (IllegalArgumentException e) {
                response.sendError(400, "Invalid UUID format in trust headers");
                return;
            }

            // Optional context headers
            UUID facilityId = parseUuid(request.getHeader(TrustHeaders.FACILITY_ID));
            UUID workspaceId = parseUuid(request.getHeader(TrustHeaders.WORKSPACE_ID));
            String shiftId = request.getHeader(TrustHeaders.SHIFT_ID);

            // Detect access mode
            String modeStr = request.getHeader(TrustHeaders.ACCESS_MODE);
            AccessMode mode = "INTERNAL".equalsIgnoreCase(modeStr)
                    ? AccessMode.INTERNAL : AccessMode.EXTERNAL;

            TrustContext ctx = new TrustContext(
                    tenantId, actorId, actorType, purposeOfUse,
                    deviceFingerprint, correlationId,
                    facilityId, workspaceId, shiftId, mode
            );

            TrustContextHolder.set(ctx);
            chain.doFilter(request, response);
        } finally {
            TrustContextHolder.clear();
        }
    }

    private static UUID parseUuid(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
