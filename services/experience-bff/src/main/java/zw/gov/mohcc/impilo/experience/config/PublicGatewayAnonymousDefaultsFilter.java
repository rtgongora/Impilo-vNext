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
 * overridden, most non-GET methods are untouched (the write lanes — SOS, feedback,
 * get-involved — carry client-supplied headers so their abuse traceability is preserved),
 * and every other route keeps the strict v1.1 contract. Must run BEFORE the V11HeaderFilter
 * (registered at {@code HIGHEST_PRECEDENCE + 9} in {@link FilterConfig}).</p>
 *
 * <p><strong>Exception — client-telemetry beacon.</strong> The client-observability sink
 * flushes via {@code navigator.sendBeacon}, which cannot set request headers at all, so a
 * genuine beacon arrives with none of the four v1.1 headers <em>and</em> no
 * {@code Idempotency-Key} (which the companion IdempotencyFilter otherwise requires on
 * POST). For the single fire-and-forget telemetry path this filter therefore also
 * synthesizes the four headers and a fresh idempotency key, so the beacon lane works
 * headerless. This lane is best-effort, PII-free, rate-limited and fail-closed in
 * {@code ClientTelemetryIntakeService}; a per-request random idempotency key is correct
 * because each beacon batch is append-only (no dedup semantics to protect).</p>
 */
public class PublicGatewayAnonymousDefaultsFilter extends OncePerRequestFilter {

    /**
     * Public default tenant for anonymous GET lanes. NOTE the estate currently runs TWO
     * "default tenant" UUIDs: this one (which the TUSO facility master's 1,775 rows live
     * under, so public find-care depends on it) and the golden …-4000-8000-…001 tenant
     * (which anonymous WRITES store under, because the browser api-client's tenant passes
     * through untouched on POST). Cross-lane reads that must survive the split (e.g. the
     * public SOS status lookup) fall back to reference-only resolution service-side —
     * see daidzai EmergencyService.findRequestByReference. Unifying the two default
     * tenants is a platform-level cleanup tracked in the remediation report.
     */
    static final String PUBLIC_DEFAULT_TENANT = "00000000-0000-0000-0000-000000000001";
    static final String PUBLIC_POD = "national-spine";

    /** The one anonymous WRITE lane that must tolerate a headerless {@code sendBeacon} POST. */
    static final String CLIENT_TELEMETRY_PATH = "/internal/v1/public/gateway/client-telemetry";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        boolean isGet = "GET".equalsIgnoreCase(request.getMethod());
        boolean isTelemetryBeacon = "POST".equalsIgnoreCase(request.getMethod())
                && CLIENT_TELEMETRY_PATH.equals(request.getRequestURI());
        if (!isGet && !isTelemetryBeacon) {
            filterChain.doFilter(request, response);
            return;
        }
        // Tenant + pod are OVERRIDDEN (not merely defaulted) on this namespace. These are
        // anonymous public lanes serving the public national-spine tenant's public data; the
        // caller's tenant is never authoritative here. Live edge traffic (Traefik → BFF) does
        // NOT strip inbound trust headers, so a signed-in browser's own X-Tenant-ID (the moh-zw
        // dev/app tenant) would otherwise leak through and drive downstream lookups against the
        // wrong tenant — 502s on the public facility profile, empty advisory/find-care results.
        // Forcing the public tenant makes every public-gateway read resolve the public data.
        Map<String, String> overrides = new LinkedHashMap<>();
        overrides.put(CompanionHeaders.TENANT_ID, PUBLIC_DEFAULT_TENANT);
        overrides.put(CompanionHeaders.POD_ID, PUBLIC_POD);
        // Request/correlation ids are filled only when missing — a caller-supplied trace id is
        // legitimate and preserved for correlation.
        Map<String, String> defaults = new LinkedHashMap<>();
        putIfMissing(request, defaults, CompanionHeaders.REQUEST_ID, UUID.randomUUID().toString());
        putIfMissing(request, defaults, CompanionHeaders.CORRELATION_ID, UUID.randomUUID().toString());
        if (isTelemetryBeacon) {
            // sendBeacon cannot carry an Idempotency-Key; each telemetry batch is append-only.
            putIfMissing(request, defaults, CompanionHeaders.IDEMPOTENCY_KEY, UUID.randomUUID().toString());
        }
        filterChain.doFilter(new DefaultedHeaderRequest(request, overrides, defaults), response);
    }

    private static void putIfMissing(HttpServletRequest request, Map<String, String> defaults,
                                     String header, String value) {
        String existing = request.getHeader(header);
        if (existing == null || existing.isBlank()) {
            defaults.put(header, value);
        }
    }

    /**
     * Read-only view of the request with public-lane headers applied. {@code overrides}
     * (tenant, pod) always win over any caller-supplied value; {@code defaults}
     * (request/correlation/idempotency ids) apply only when the caller sent none.
     */
    private static final class DefaultedHeaderRequest extends HttpServletRequestWrapper {

        private final Map<String, String> overrides;
        private final Map<String, String> defaults;

        private DefaultedHeaderRequest(HttpServletRequest request,
                                       Map<String, String> overrides,
                                       Map<String, String> defaults) {
            super(request);
            this.overrides = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
            this.overrides.putAll(overrides);
            this.defaults = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
            this.defaults.putAll(defaults);
        }

        @Override
        public String getHeader(String name) {
            String override = overrides.get(name);
            if (override != null) return override;
            String value = super.getHeader(name);
            return value != null ? value : defaults.get(name);
        }

        @Override
        public Enumeration<String> getHeaders(String name) {
            String override = overrides.get(name);
            if (override != null) {
                return Collections.enumeration(Collections.singletonList(override));
            }
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
            overrides.keySet().forEach(name -> names.putIfAbsent(name, Boolean.TRUE));
            defaults.keySet().forEach(name -> names.putIfAbsent(name, Boolean.TRUE));
            return Collections.enumeration(names.keySet());
        }
    }
}
