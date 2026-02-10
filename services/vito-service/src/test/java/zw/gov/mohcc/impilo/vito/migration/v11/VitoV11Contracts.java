package zw.gov.mohcc.impilo.vito.migration.v11;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Contract harness for VITO v1.1 migration (Wave 0).
 *
 * These methods represent the REQUIRED contracts from Manifest v1.1.
 * They are pure Java — no Spring, no MockMvc.
 *
 * Wave 2 will replace this harness with real filter/guard implementations:
 * - services/vito-service/src/main/java/zw/gov/mohcc/impilo/vito/config/V1_1HeaderFilter.java
 * - services/vito-service/src/main/java/zw/gov/mohcc/impilo/vito/config/IdempotencyFilter.java
 * - services/vito-service/src/main/java/zw/gov/mohcc/impilo/vito/core/FederationAuthorityGuard.java
 * - services/vito-service/src/main/java/zw/gov/mohcc/impilo/vito/events/VitoOutboxPublisher.java
 */
public final class VitoV11Contracts {

    private static final Set<String> REQUIRED_V11_HEADERS = Set.of(
            "X-Tenant-ID",
            "X-Pod-ID",
            "X-Request-ID",
            "X-Correlation-ID"
    );

    private static final Set<String> COMMAND_METHODS = Set.of("POST", "PUT", "PATCH");

    private VitoV11Contracts() {
    }

    /**
     * Asserts that all v1.1 required headers are present.
     *
     * @param headers map of header-name → header-value (case-sensitive keys expected)
     * @throws IllegalStateException with message including the first missing header name
     */
    public static void requireV11Headers(Map<String, String> headers) {
        List<String> missing = new ArrayList<>();
        for (String required : REQUIRED_V11_HEADERS) {
            if (headers == null || !headers.containsKey(required) || headers.get(required) == null || headers.get(required).isBlank()) {
                missing.add(required);
            }
        }
        if (!missing.isEmpty()) {
            throw new IllegalStateException(
                    "MISSING_REQUIRED_HEADER: " + missing.get(0)
                            + " (all missing: " + missing + ")");
        }
    }

    /**
     * For command methods (POST/PUT/PATCH) under /internal/v1/*, requires the
     * Idempotency-Key header. GET/DELETE/HEAD/OPTIONS are exempt.
     *
     * @param method  HTTP method (uppercase)
     * @param path    request path
     * @param headers map of header-name → header-value
     * @throws IllegalStateException with message "IDEMPOTENCY_KEY_REQUIRED" if missing
     */
    public static void requireIdempotencyKey(String method, String path, Map<String, String> headers) {
        if (!COMMAND_METHODS.contains(method)) {
            return; // GET, DELETE, etc. are exempt
        }
        if (path == null || !path.startsWith("/internal/v1/")) {
            return; // only v1.1 internal paths require idempotency
        }
        String key = headers != null ? headers.get("Idempotency-Key") : null;
        if (key == null || key.isBlank()) {
            throw new IllegalStateException("IDEMPOTENCY_KEY_REQUIRED");
        }
    }

    /**
     * Asserts that the given pod is authorized for federation-sensitive operations
     * (e.g., patient merge). Only "national" pod is authorized.
     *
     * @param podId the originating pod identifier
     * @throws IllegalStateException with message "FEDERATION_NOT_AUTHORIZED" if podId != "national"
     */
    public static void requireFederationAuthorityForMerge(String podId) {
        if (!"national".equals(podId)) {
            throw new IllegalStateException("FEDERATION_NOT_AUTHORIZED");
        }
    }
}
