package zw.gov.mohcc.impilo.experience.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;

import java.io.IOException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Gateway public lane (ADR gateway-public-lane-security): a truly anonymous caller — a
 * deep link, a shared URL, curl, any non-shell client — arrives with NO platform headers
 * at all, but the companion {@code V11HeaderFilter} hard-requires the four v1.1 headers
 * on every {@code /internal/v1/**} route, which made the "public" gateway namespace
 * return 400 {@code MISSING_REQUIRED_HEADER} to exactly the callers it exists for
 * (rig-caught in the W1 runtime proof).
 *
 * <p>For <strong>GET</strong> requests inside the single public gateway namespace
 * ({@code /internal/v1/public/gateway/**}) this filter synthesizes service-originated
 * defaults — the public default tenant, the national pod, and fresh request/correlation
 * ids — for whichever of the four headers are missing. Caller-supplied values are never
 * overridden, non-GET methods are untouched (the lane is GET-only in W1), and every
 * other route keeps the strict v1.1 contract. Must run BEFORE the V11HeaderFilter
 * (registered at {@code HIGHEST_PRECEDENCE + 9} in {@link FilterConfig}).</p>
 */
public class PublicGatewayAnonymousDefaultsFilter extends OncePerRequestFilter {

    /** Same public default tenant the BFF's anonymous-safe downstream calls use. */
    static final String PUBLIC_DEFAULT_TENANT = "00000000-0000-0000-0000-000000000001";
    static final String PUBLIC_POD = "national-spine";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (!"GET".equalsIgnoreCase(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }
        Map<String, String> defaults = new LinkedHashMap<>();
        putIfMissing(request, defaults, CompanionHeaders.TENANT_ID, PUBLIC_DEFAULT_TENANT);
        putIfMissing(request, defaults, CompanionHeaders.POD_ID, PUBLIC_POD);
        putIfMissing(request, defaults, CompanionHeaders.REQUEST_ID, UUID.randomUUID().toString());
        putIfMissing(request, defaults, CompanionHeaders.CORRELATION_ID, UUID.randomUUID().toString());
        if (defaults.isEmpty()) {
            filterChain.doFilter(request, response);
            return;
        }
        filterChain.doFilter(new DefaultedHeaderRequest(request, defaults), response);
    }

    private static void putIfMissing(HttpServletRequest request, Map<String, String> defaults,
                                     String header, String value) {
        String existing = request.getHeader(header);
        if (existing == null || existing.isBlank()) {
            defaults.put(header, value);
        }
    }

    /** Read-only view of the request with the missing platform headers defaulted. */
    private static final class DefaultedHeaderRequest extends HttpServletRequestWrapper {

        private final Map<String, String> defaults;

        private DefaultedHeaderRequest(HttpServletRequest request, Map<String, String> defaults) {
            super(request);
            Map<String, String> caseInsensitive = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
            caseInsensitive.putAll(defaults);
            this.defaults = caseInsensitive;
        }

        @Override
        public String getHeader(String name) {
            String value = super.getHeader(name);
            return value != null ? value : defaults.get(name);
        }

        @Override
        public Enumeration<String> getHeaders(String name) {
            Enumeration<String> values = super.getHeaders(name);
            if (values != null && values.hasMoreElements()) {
                return values;
            }
            String defaulted = defaults.get(name);
            return defaulted == null
                    ? Collections.emptyEnumeration()
                    : Collections.enumeration(Collections.singletonList(defaulted));
        }

        @Override
        public Enumeration<String> getHeaderNames() {
            Map<String, Boolean> names = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
            Enumeration<String> original = super.getHeaderNames();
            while (original != null && original.hasMoreElements()) {
                names.put(original.nextElement(), Boolean.TRUE);
            }
            defaults.keySet().forEach(name -> names.putIfAbsent(name, Boolean.TRUE));
            return Collections.enumeration(names.keySet());
        }
    }
}
