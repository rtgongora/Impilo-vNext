package zw.gov.mohcc.impilo.shared.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import zw.gov.mohcc.impilo.shared.visibility.VisibilityContextHolder;
import zw.gov.mohcc.impilo.shared.visibility.VisibilityHeaderParser;

import java.io.IOException;
import java.util.UUID;

/**
 * Servlet filter that extracts trust headers from every inbound request,
 * determines the {@link AccessMode} (INTERNAL vs EXTERNAL), and populates
 * {@link TrustContextHolder} for downstream consumption.
 *
 * Internal mode: Envoy has already validated via ext_authz and injected headers.
 * External mode: Request carries a Tshepo-issued bearer token; service must verify.
 *
 * Register this filter in each service's SecurityConfig or as a @Bean.
 */
public class TrustContextFilter extends OncePerRequestFilter {

    /** Header injected by Envoy after successful ext_authz for internal requests */
    private static final String INTERNAL_MARKER = "x-envoy-internal";

    /** When non-null, {@link VisibilityHeaderParser#resolve} merges {@code x-obligations} JSON. */
    private final ObjectMapper visibilityObjectMapper;

    public TrustContextFilter() {
        this(null);
    }

    public TrustContextFilter(ObjectMapper visibilityObjectMapper) {
        this.visibilityObjectMapper = visibilityObjectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            TrustContext ctx = extractContext(request);
            TrustContextHolder.set(ctx);
            VisibilityContextHolder.set(VisibilityHeaderParser.resolve(request, visibilityObjectMapper));
            filterChain.doFilter(request, response);
        } finally {
            TrustContextHolder.clear();
            VisibilityContextHolder.clear();
        }
    }

    private TrustContext extractContext(HttpServletRequest request) {
        // Determine access mode
        String internalMarker = request.getHeader(INTERNAL_MARKER);
        String explicitMode = request.getHeader(TrustContext.H_ACCESS_MODE);
        AccessMode mode;
        if ("true".equalsIgnoreCase(internalMarker) || "INTERNAL".equalsIgnoreCase(explicitMode)) {
            mode = AccessMode.INTERNAL;
        } else {
            mode = AccessMode.EXTERNAL;
        }

        return new TrustContext(
                parseUuid(request.getHeader(TrustContext.H_TENANT_ID)),
                request.getHeader(TrustContext.H_ACTOR_ID),
                request.getHeader(TrustContext.H_ACTOR_TYPE),
                request.getHeader(TrustContext.H_PURPOSE_OF_USE),
                request.getHeader(TrustContext.H_DEVICE_FINGERPRINT),
                parseUuidOrGenerate(request.getHeader(TrustContext.H_CORRELATION_ID)),
                parseUuid(request.getHeader(TrustContext.H_FACILITY_ID)),
                parseUuid(request.getHeader(TrustContext.H_WORKSPACE_ID)),
                request.getHeader(TrustContext.H_SHIFT_ID),
                mode
        );
    }

    private static UUID parseUuid(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return UUID.fromString(value.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static UUID parseUuidOrGenerate(String value) {
        UUID parsed = parseUuid(value);
        return parsed != null ? parsed : UUID.randomUUID();
    }
}
