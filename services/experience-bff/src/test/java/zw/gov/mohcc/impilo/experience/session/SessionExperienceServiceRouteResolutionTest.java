package zw.gov.mohcc.impilo.experience.session;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@code resolveDefaultRoute} landing-parity (Phase F6) — a separate, mock-free test class so
 * these pure-function cases don't drag in {@code SessionExperienceServiceTest}'s Mockito
 * fixture (strict-stubs mode fails a test that never touches the mocked collaborators).
 *
 * <p>The TS twin lives in identity-context.ts ({@code defaultLandingPath}) and
 * resolve-post-login-destination.ts ({@code resolvePostLoginDestination}) — see
 * {@code ui/one-ui-shell/src/lib/__tests__/resolve-post-login-destination.test.ts} and
 * {@code identity-context.test.ts}, which assert the SAME table on that side. The two
 * languages can't share one test; this is the parity proof. Both sides must agree that a
 * provider with a facility already selected lands on {@code "/work"} — Phase F6 turned
 * {@code /provider-workspace} into an intent-resolution shim to it, not the destination
 * itself.</p>
 */
class SessionExperienceServiceRouteResolutionTest {

    @Test
    void workTabWithFacilitySelected_landsOnWork() {
        assertEquals("/work", SessionExperienceService.resolveDefaultRoute(
                "work", null, List.of(Map.of("facilityId", "F1")), true));
    }

    @Test
    void workTabSingleAssignmentNoFacility_landsOnFacilityPicker() {
        assertEquals("/facility", SessionExperienceService.resolveDefaultRoute(
                "work", null, List.of(Map.of("facilityId", "F1")), false));
    }

    @Test
    void workTabMultipleAssignmentsNoFacility_landsOnContextChooser() {
        assertEquals("/auth/context-chooser", SessionExperienceService.resolveDefaultRoute(
                "work", null, List.of(Map.of("facilityId", "F1"), Map.of("facilityId", "F2")), false));
    }

    @Test
    void providerSuspended_overridesEverythingElse() {
        assertEquals("/provider/status", SessionExperienceService.resolveDefaultRoute(
                "work", "provider_suspended", List.of(Map.of("facilityId", "F1")), true));
    }

    @Test
    void professionalTab_landsOnProfessional() {
        assertEquals("/professional", SessionExperienceService.resolveDefaultRoute(
                "professional", null, List.of(), false));
    }

    @Test
    void personalTab_landsOnHome() {
        assertEquals("/home", SessionExperienceService.resolveDefaultRoute(
                "personal", null, List.of(), false));
    }
}
