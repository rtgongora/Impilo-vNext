package zw.gov.mohcc.impilo.vito.migration.v11.wave21;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import zw.gov.mohcc.impilo.vito.core.FederationAuthorityGuard;
import zw.gov.mohcc.impilo.vito.core.FederationAuthorityGuard.FederationNotAuthorizedException;

import static org.assertj.core.api.Assertions.*;

/**
 * Wave 21 — Comprehensive federation authority violation tests.
 *
 * Proves:
 * A) Non-national pods are denied merge operations (403 FEDERATION_AUTHORITY_VIOLATION)
 * B) National pod is allowed merge operations
 * C) Various pod ID formats are handled correctly
 * D) The guard produces the correct error code for downstream error envelopes
 */
@DisplayName("Wave 21 — Federation Authority Violations")
class FederationAuthorityViolationWave21Test {

    private final FederationAuthorityGuard guard = new FederationAuthorityGuard();

    @Nested
    @DisplayName("Non-national pod merge denial")
    class NonNationalDenial {

        @Test
        @DisplayName("Facility pod denied merge")
        void facilityPodDenied() {
            assertThatThrownBy(() -> guard.requireNationalPodForMerge("pod-harare-central"))
                    .isInstanceOf(FederationNotAuthorizedException.class)
                    .hasMessageContaining("FEDERATION_AUTHORITY_VIOLATION");
        }

        @Test
        @DisplayName("District pod denied merge")
        void districtPodDenied() {
            assertThatThrownBy(() -> guard.requireNationalPodForMerge("pod-mashonaland-west"))
                    .isInstanceOf(FederationNotAuthorizedException.class);
        }

        @Test
        @DisplayName("Offline pod denied merge")
        void offlinePodDenied() {
            assertThatThrownBy(() -> guard.requireNationalPodForMerge("pod-offline-edge-1"))
                    .isInstanceOf(FederationNotAuthorizedException.class);
        }

        @Test
        @DisplayName("UUID-style pod denied merge")
        void uuidPodDenied() {
            assertThatThrownBy(() -> guard.requireNationalPodForMerge("550e8400-e29b-41d4-a716-446655440000"))
                    .isInstanceOf(FederationNotAuthorizedException.class);
        }

        @Test
        @DisplayName("Empty string pod denied merge")
        void emptyPodDenied() {
            assertThatThrownBy(() -> guard.requireNationalPodForMerge(""))
                    .isInstanceOf(FederationNotAuthorizedException.class);
        }

        @Test
        @DisplayName("Null pod denied merge")
        void nullPodDenied() {
            assertThatThrownBy(() -> guard.requireNationalPodForMerge(null))
                    .isInstanceOf(FederationNotAuthorizedException.class);
        }

        @Test
        @DisplayName("Exception contains pod ID for audit")
        void exceptionContainsPodId() {
            FederationNotAuthorizedException ex = catchThrowableOfType(
                    FederationNotAuthorizedException.class,
                    () -> guard.requireNationalPodForMerge("rogue-pod"));
            assertThat(ex.getPodId()).isEqualTo("rogue-pod");
        }
    }

    @Nested
    @DisplayName("National pod allowed")
    class NationalAllowed {

        @Test
        @DisplayName("National pod allowed merge")
        void nationalPodAllowed() {
            assertThatCode(() -> guard.requireNationalPodForMerge("national"))
                    .doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("Violation produces outbox event (structural)")
    class ViolationOutboxStructure {

        @Test
        @DisplayName("FederationNotAuthorizedException message is the error code")
        void exceptionMessageIsErrorCode() {
            FederationNotAuthorizedException ex = catchThrowableOfType(
                    FederationNotAuthorizedException.class,
                    () -> guard.requireNationalPodForMerge("pod-x"));
            assertThat(ex.getMessage()).isEqualTo("FEDERATION_AUTHORITY_VIOLATION");
        }
    }
}
