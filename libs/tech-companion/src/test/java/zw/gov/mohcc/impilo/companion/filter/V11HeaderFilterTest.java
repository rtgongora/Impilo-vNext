package zw.gov.mohcc.impilo.companion.filter;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for V11HeaderFilter path matching and enforcement logic.
 */
class V11HeaderFilterTest {

    @Nested
    class PathMatching {
        @Test
        void internalV1PathIsV11() {
            assertTrue(V11HeaderFilter.isV11Path("/internal/v1/patients"));
        }

        @Test
        void externalV1PathIsV11() {
            assertTrue(V11HeaderFilter.isV11Path("/external/v1/facility/search"));
        }

        @ParameterizedTest
        @ValueSource(strings = {"/v1/legacy", "/actuator/health", "/swagger/index.html", "/v3/api-docs/"})
        void nonV11PathsAreSkipped(String path) {
            assertFalse(V11HeaderFilter.isV11Path(path));
        }

        @Test
        void nullPathReturnsFalse() {
            assertFalse(V11HeaderFilter.isV11Path(null));
        }
    }

    @Nested
    class HeaderValidation {
        /**
         * Pure function test: validates the static header check logic.
         * The actual filter integration is tested in the contract harness.
         */
        @Test
        void hardRequiredHeadersAreDefinedCorrectly() {
            assertEquals(4, zw.gov.mohcc.impilo.companion.context.CompanionHeaders.HARD_REQUIRED.length);
            assertEquals("X-Tenant-ID", zw.gov.mohcc.impilo.companion.context.CompanionHeaders.HARD_REQUIRED[0]);
            assertEquals("X-Pod-ID", zw.gov.mohcc.impilo.companion.context.CompanionHeaders.HARD_REQUIRED[1]);
            assertEquals("X-Request-ID", zw.gov.mohcc.impilo.companion.context.CompanionHeaders.HARD_REQUIRED[2]);
            assertEquals("X-Correlation-ID", zw.gov.mohcc.impilo.companion.context.CompanionHeaders.HARD_REQUIRED[3]);
        }
    }
}
