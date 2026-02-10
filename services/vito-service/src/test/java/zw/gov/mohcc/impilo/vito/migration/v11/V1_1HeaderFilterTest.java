package zw.gov.mohcc.impilo.vito.migration.v11;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Wave 0 contract tests for the v1.1 header enforcement filter.
 *
 * These tests exercise the {@link VitoV11Contracts#requireV11Headers} harness
 * using pure JUnit 5 — no Spring, no MockMvc, no @Disabled.
 *
 * Required v1.1 headers on all /internal/v1/** and /external/v1/** paths:
 *   - X-Tenant-ID
 *   - X-Pod-ID
 *   - X-Request-ID
 *   - X-Correlation-ID
 *
 * Wave 2 implementation target:
 *   services/vito-service/src/main/java/zw/gov/mohcc/impilo/vito/config/V1_1HeaderFilter.java
 */
@DisplayName("V1.1 Header Filter Contract (Wave 0 — harness-based)")
class V1_1HeaderFilterTest {

    private Map<String, String> allHeaders() {
        Map<String, String> h = new HashMap<>();
        h.put("X-Tenant-ID", "tenant-zw");
        h.put("X-Pod-ID", "pod-harare-central");
        h.put("X-Request-ID", "req-001");
        h.put("X-Correlation-ID", "corr-001");
        return h;
    }

    @Nested
    @DisplayName("Missing individual headers")
    class MissingHeaders {

        @Test
        @DisplayName("Missing X-Tenant-ID throws MISSING_REQUIRED_HEADER")
        void missingTenantIdThrows() {
            Map<String, String> headers = allHeaders();
            headers.remove("X-Tenant-ID");
            IllegalStateException ex = assertThrows(IllegalStateException.class,
                    () -> VitoV11Contracts.requireV11Headers(headers));
            assertTrue(ex.getMessage().contains("X-Tenant-ID"),
                    "Error should mention X-Tenant-ID: " + ex.getMessage());
            assertTrue(ex.getMessage().contains("MISSING_REQUIRED_HEADER"),
                    "Error should contain MISSING_REQUIRED_HEADER: " + ex.getMessage());
        }

        @Test
        @DisplayName("Missing X-Pod-ID throws MISSING_REQUIRED_HEADER")
        void missingPodIdThrows() {
            Map<String, String> headers = allHeaders();
            headers.remove("X-Pod-ID");
            IllegalStateException ex = assertThrows(IllegalStateException.class,
                    () -> VitoV11Contracts.requireV11Headers(headers));
            assertTrue(ex.getMessage().contains("X-Pod-ID"),
                    "Error should mention X-Pod-ID: " + ex.getMessage());
        }

        @Test
        @DisplayName("Missing X-Request-ID throws MISSING_REQUIRED_HEADER")
        void missingRequestIdThrows() {
            Map<String, String> headers = allHeaders();
            headers.remove("X-Request-ID");
            IllegalStateException ex = assertThrows(IllegalStateException.class,
                    () -> VitoV11Contracts.requireV11Headers(headers));
            assertTrue(ex.getMessage().contains("X-Request-ID"),
                    "Error should mention X-Request-ID: " + ex.getMessage());
        }

        @Test
        @DisplayName("Missing X-Correlation-ID throws MISSING_REQUIRED_HEADER")
        void missingCorrelationIdThrows() {
            Map<String, String> headers = allHeaders();
            headers.remove("X-Correlation-ID");
            IllegalStateException ex = assertThrows(IllegalStateException.class,
                    () -> VitoV11Contracts.requireV11Headers(headers));
            assertTrue(ex.getMessage().contains("X-Correlation-ID"),
                    "Error should mention X-Correlation-ID: " + ex.getMessage());
        }
    }

    @Nested
    @DisplayName("Blank header values treated as missing")
    class BlankHeaders {

        @Test
        @DisplayName("Blank X-Tenant-ID throws MISSING_REQUIRED_HEADER")
        void blankTenantIdThrows() {
            Map<String, String> headers = allHeaders();
            headers.put("X-Tenant-ID", "   ");
            assertThrows(IllegalStateException.class,
                    () -> VitoV11Contracts.requireV11Headers(headers));
        }
    }

    @Nested
    @DisplayName("Null headers map")
    class NullHeaders {

        @Test
        @DisplayName("Null headers map throws MISSING_REQUIRED_HEADER")
        void nullHeadersThrows() {
            assertThrows(IllegalStateException.class,
                    () -> VitoV11Contracts.requireV11Headers(null));
        }
    }

    @Nested
    @DisplayName("All headers present")
    class AllPresent {

        @Test
        @DisplayName("All required headers present passes without exception")
        void allHeadersPresentPasses() {
            assertDoesNotThrow(() -> VitoV11Contracts.requireV11Headers(allHeaders()));
        }
    }
}
