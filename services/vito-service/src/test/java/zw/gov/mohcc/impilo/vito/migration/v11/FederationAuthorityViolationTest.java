package zw.gov.mohcc.impilo.vito.migration.v11;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Wave 0 contract tests for federation authority rules.
 *
 * These tests exercise the {@link VitoV11Contracts#requireFederationAuthorityForMerge} harness
 * using pure JUnit 5 — no Spring, no MockMvc, no @Disabled.
 *
 * Per the v1.1 companion spec, patient merge operations must only be executed
 * by the "national" pod. Any non-national pod MUST be rejected.
 *
 * Wave 2 implementation target:
 *   services/vito-service/src/main/java/zw/gov/mohcc/impilo/vito/core/FederationAuthorityGuard.java
 *   (will use shared-kernel-java ConsistencyGate to evaluate actions)
 */
@DisplayName("V1.1 Federation Authority Violation Guard (Wave 0 — harness-based)")
class FederationAuthorityViolationTest {

    @Test
    @DisplayName("Patient merge rejected when podId is not 'national'")
    void mergeRejectedForNonNationalPod() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> VitoV11Contracts.requireFederationAuthorityForMerge("pod-harare-central"));
        assertEquals("FEDERATION_NOT_AUTHORIZED", ex.getMessage());
    }

    @Test
    @DisplayName("Patient merge rejected for offline pod")
    void mergeRejectedForOfflinePod() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> VitoV11Contracts.requireFederationAuthorityForMerge("pod-offline-1"));
        assertEquals("FEDERATION_NOT_AUTHORIZED", ex.getMessage());
    }

    @Test
    @DisplayName("Patient merge allowed when podId is 'national'")
    void mergeAllowedForNationalPod() {
        assertDoesNotThrow(
                () -> VitoV11Contracts.requireFederationAuthorityForMerge("national"));
    }

    @Test
    @DisplayName("Null podId rejected as non-national")
    void nullPodIdRejected() {
        assertThrows(IllegalStateException.class,
                () -> VitoV11Contracts.requireFederationAuthorityForMerge(null));
    }
}
