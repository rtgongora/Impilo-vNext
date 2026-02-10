package zw.gov.mohcc.impilo.vito.migration.v11;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Wave 0 contract tests for idempotency key enforcement on v1.1 command endpoints.
 *
 * These tests exercise the {@link VitoV11Contracts#requireIdempotencyKey} harness
 * using pure JUnit 5 — no Spring, no MockMvc, no @Disabled.
 *
 * All POST/PUT/PATCH requests to /internal/v1/** must include an Idempotency-Key header.
 * Missing keys must produce IDEMPOTENCY_KEY_REQUIRED.
 *
 * Wave 2 implementation targets:
 *   services/vito-service/src/main/java/zw/gov/mohcc/impilo/vito/config/IdempotencyFilter.java
 *   services/vito-service/src/main/java/zw/gov/mohcc/impilo/vito/core/IdempotencyStore.java
 */
@DisplayName("V1.1 Idempotency Key Enforcement (Wave 0 — harness-based)")
class IdempotencyKeyRequiredTest {

    private Map<String, String> headersWithoutIdempotencyKey() {
        Map<String, String> h = new HashMap<>();
        h.put("X-Tenant-ID", "tenant-zw");
        h.put("X-Pod-ID", "pod-1");
        h.put("X-Request-ID", "req-1");
        h.put("X-Correlation-ID", "corr-1");
        return h;
    }

    private Map<String, String> headersWithIdempotencyKey() {
        Map<String, String> h = headersWithoutIdempotencyKey();
        h.put("Idempotency-Key", "idem-abc-123");
        return h;
    }

    @Test
    @DisplayName("POST without Idempotency-Key throws IDEMPOTENCY_KEY_REQUIRED")
    void postWithoutIdempotencyKeyThrows() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> VitoV11Contracts.requireIdempotencyKey(
                        "POST", "/internal/v1/patients", headersWithoutIdempotencyKey()));
        assertEquals("IDEMPOTENCY_KEY_REQUIRED", ex.getMessage());
    }

    @Test
    @DisplayName("PUT without Idempotency-Key throws IDEMPOTENCY_KEY_REQUIRED")
    void putWithoutIdempotencyKeyThrows() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> VitoV11Contracts.requireIdempotencyKey(
                        "PUT", "/internal/v1/patients/123", headersWithoutIdempotencyKey()));
        assertEquals("IDEMPOTENCY_KEY_REQUIRED", ex.getMessage());
    }

    @Test
    @DisplayName("PATCH without Idempotency-Key throws IDEMPOTENCY_KEY_REQUIRED")
    void patchWithoutIdempotencyKeyThrows() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> VitoV11Contracts.requireIdempotencyKey(
                        "PATCH", "/internal/v1/patients/123", headersWithoutIdempotencyKey()));
        assertEquals("IDEMPOTENCY_KEY_REQUIRED", ex.getMessage());
    }

    @Test
    @DisplayName("POST with Idempotency-Key passes")
    void postWithIdempotencyKeyPasses() {
        assertDoesNotThrow(() -> VitoV11Contracts.requireIdempotencyKey(
                "POST", "/internal/v1/patients", headersWithIdempotencyKey()));
    }

    @Test
    @DisplayName("GET requests do NOT require Idempotency-Key")
    void getRequestsExempt() {
        assertDoesNotThrow(() -> VitoV11Contracts.requireIdempotencyKey(
                "GET", "/internal/v1/patients/123", headersWithoutIdempotencyKey()));
    }

    @Test
    @DisplayName("DELETE requests do NOT require Idempotency-Key")
    void deleteRequestsExempt() {
        assertDoesNotThrow(() -> VitoV11Contracts.requireIdempotencyKey(
                "DELETE", "/internal/v1/patients/123", headersWithoutIdempotencyKey()));
    }

    @Test
    @DisplayName("Non-v1.1 path is exempt from Idempotency-Key")
    void legacyPathExempt() {
        assertDoesNotThrow(() -> VitoV11Contracts.requireIdempotencyKey(
                "POST", "/v1/clients", headersWithoutIdempotencyKey()));
    }
}
