package zw.gov.mohcc.impilo.vito.migration.v11;

import java.util.HashMap;
import java.util.Map;

/**
 * Standalone runner for Wave 0 contract tests.
 * Runs without JUnit or any external dependencies — plain javac + java.
 * Exit code 0 = all pass, 1 = failures detected.
 *
 * Exercises:
 *   1. VitoV11Contracts.requireV11Headers()
 *   2. VitoV11Contracts.requireIdempotencyKey()
 *   3. VitoV11Contracts.requireFederationAuthorityForMerge()
 *
 * Wave 2 will replace this with real Spring-integrated tests.
 */
public class Wave0ContractStandaloneRunner {

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) {
        System.out.println("=== VITO v1.1 Wave 0 Contract Tests (Standalone) ===\n");

        // ========================
        // Header enforcement tests
        // ========================
        System.out.println("--- Header Enforcement ---");

        test("Missing X-Tenant-ID throws MISSING_REQUIRED_HEADER", () -> {
            Map<String, String> headers = allHeaders();
            headers.remove("X-Tenant-ID");
            try {
                VitoV11Contracts.requireV11Headers(headers);
                throw new AssertionError("Expected IllegalStateException");
            } catch (IllegalStateException e) {
                assertContains(e.getMessage(), "X-Tenant-ID");
                assertContains(e.getMessage(), "MISSING_REQUIRED_HEADER");
            }
        });

        test("Missing X-Pod-ID throws MISSING_REQUIRED_HEADER", () -> {
            Map<String, String> headers = allHeaders();
            headers.remove("X-Pod-ID");
            try {
                VitoV11Contracts.requireV11Headers(headers);
                throw new AssertionError("Expected IllegalStateException");
            } catch (IllegalStateException e) {
                assertContains(e.getMessage(), "X-Pod-ID");
            }
        });

        test("Missing X-Request-ID throws MISSING_REQUIRED_HEADER", () -> {
            Map<String, String> headers = allHeaders();
            headers.remove("X-Request-ID");
            try {
                VitoV11Contracts.requireV11Headers(headers);
                throw new AssertionError("Expected IllegalStateException");
            } catch (IllegalStateException e) {
                assertContains(e.getMessage(), "X-Request-ID");
            }
        });

        test("Missing X-Correlation-ID throws MISSING_REQUIRED_HEADER", () -> {
            Map<String, String> headers = allHeaders();
            headers.remove("X-Correlation-ID");
            try {
                VitoV11Contracts.requireV11Headers(headers);
                throw new AssertionError("Expected IllegalStateException");
            } catch (IllegalStateException e) {
                assertContains(e.getMessage(), "X-Correlation-ID");
            }
        });

        test("Null headers map throws MISSING_REQUIRED_HEADER", () -> {
            try {
                VitoV11Contracts.requireV11Headers(null);
                throw new AssertionError("Expected IllegalStateException");
            } catch (IllegalStateException e) {
                assertContains(e.getMessage(), "MISSING_REQUIRED_HEADER");
            }
        });

        test("All required headers present passes", () -> {
            VitoV11Contracts.requireV11Headers(allHeaders());
            // no exception = pass
        });

        // ========================
        // Idempotency key tests
        // ========================
        System.out.println("\n--- Idempotency Key Enforcement ---");

        test("POST without Idempotency-Key throws IDEMPOTENCY_KEY_REQUIRED", () -> {
            try {
                VitoV11Contracts.requireIdempotencyKey("POST", "/internal/v1/patients", headersNoIdem());
                throw new AssertionError("Expected IllegalStateException");
            } catch (IllegalStateException e) {
                assertEquals("IDEMPOTENCY_KEY_REQUIRED", e.getMessage());
            }
        });

        test("PUT without Idempotency-Key throws IDEMPOTENCY_KEY_REQUIRED", () -> {
            try {
                VitoV11Contracts.requireIdempotencyKey("PUT", "/internal/v1/patients/123", headersNoIdem());
                throw new AssertionError("Expected IllegalStateException");
            } catch (IllegalStateException e) {
                assertEquals("IDEMPOTENCY_KEY_REQUIRED", e.getMessage());
            }
        });

        test("PATCH without Idempotency-Key throws IDEMPOTENCY_KEY_REQUIRED", () -> {
            try {
                VitoV11Contracts.requireIdempotencyKey("PATCH", "/internal/v1/patients/123", headersNoIdem());
                throw new AssertionError("Expected IllegalStateException");
            } catch (IllegalStateException e) {
                assertEquals("IDEMPOTENCY_KEY_REQUIRED", e.getMessage());
            }
        });

        test("POST with Idempotency-Key passes", () -> {
            Map<String, String> h = headersNoIdem();
            h.put("Idempotency-Key", "idem-123");
            VitoV11Contracts.requireIdempotencyKey("POST", "/internal/v1/patients", h);
        });

        test("GET requests exempt from Idempotency-Key", () -> {
            VitoV11Contracts.requireIdempotencyKey("GET", "/internal/v1/patients/123", headersNoIdem());
        });

        test("Legacy path exempt from Idempotency-Key", () -> {
            VitoV11Contracts.requireIdempotencyKey("POST", "/v1/clients", headersNoIdem());
        });

        // ========================
        // Federation authority tests
        // ========================
        System.out.println("\n--- Federation Authority ---");

        test("Non-national pod rejected for merge", () -> {
            try {
                VitoV11Contracts.requireFederationAuthorityForMerge("pod-harare-central");
                throw new AssertionError("Expected IllegalStateException");
            } catch (IllegalStateException e) {
                assertEquals("FEDERATION_NOT_AUTHORIZED", e.getMessage());
            }
        });

        test("Offline pod rejected for merge", () -> {
            try {
                VitoV11Contracts.requireFederationAuthorityForMerge("pod-offline-1");
                throw new AssertionError("Expected IllegalStateException");
            } catch (IllegalStateException e) {
                assertEquals("FEDERATION_NOT_AUTHORIZED", e.getMessage());
            }
        });

        test("National pod allowed for merge", () -> {
            VitoV11Contracts.requireFederationAuthorityForMerge("national");
        });

        test("Null podId rejected for merge", () -> {
            try {
                VitoV11Contracts.requireFederationAuthorityForMerge(null);
                throw new AssertionError("Expected IllegalStateException");
            } catch (IllegalStateException e) {
                assertEquals("FEDERATION_NOT_AUTHORIZED", e.getMessage());
            }
        });

        // ========================
        // Summary
        // ========================
        System.out.println("\n=== RESULTS ===");
        System.out.println("Passed: " + passed);
        System.out.println("Failed: " + failed);
        System.out.println("Total:  " + (passed + failed));
        if (failed > 0) {
            System.out.println("\n*** FAILURES DETECTED ***");
            System.exit(1);
        } else {
            System.out.println("\n*** ALL WAVE 0 CONTRACT TESTS PASSED ***");
        }
    }

    // --- helpers ---

    private static Map<String, String> allHeaders() {
        Map<String, String> h = new HashMap<>();
        h.put("X-Tenant-ID", "tenant-zw");
        h.put("X-Pod-ID", "pod-harare-central");
        h.put("X-Request-ID", "req-001");
        h.put("X-Correlation-ID", "corr-001");
        return h;
    }

    private static Map<String, String> headersNoIdem() {
        return allHeaders(); // no Idempotency-Key
    }

    private static void test(String name, Runnable body) {
        try {
            body.run();
            System.out.println("  PASS: " + name);
            passed++;
        } catch (Throwable t) {
            System.out.println("  FAIL: " + name + " — " + t.getMessage());
            failed++;
        }
    }

    private static void assertEquals(String expected, String actual) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError("Expected <" + expected + "> but was <" + actual + ">");
        }
    }

    private static void assertContains(String haystack, String needle) {
        if (haystack == null || !haystack.contains(needle)) {
            throw new AssertionError("Expected string containing '" + needle + "' but was: " + haystack);
        }
    }
}
