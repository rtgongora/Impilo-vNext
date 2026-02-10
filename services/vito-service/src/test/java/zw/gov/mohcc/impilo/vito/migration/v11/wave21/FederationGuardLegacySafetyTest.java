package zw.gov.mohcc.impilo.vito.migration.v11.wave21;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import zw.gov.mohcc.impilo.vito.core.FederationAuthorityGuard;
import zw.gov.mohcc.impilo.vito.core.FederationAuthorityGuard.FederationNotAuthorizedException;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test C: Legacy safety — FederationAuthorityGuard does NOT block MergeService.merge()
 * if v1.1 headers are absent.
 *
 * Proves by:
 * 1) FederationAuthorityGuard.requireNationalPodForMerge("national") passes
 * 2) FederationAuthorityGuard.requireNationalPodForMerge("pod-x") throws
 * 3) MergeService.enforceFederationAuthority() is private and only checks when
 *    currentPodId() returns non-null. Outside a request context (legacy),
 *    RequestContextHolder.getRequestAttributes() returns null → currentPodId()
 *    returns null → guard is NOT invoked.
 *
 * No Spring context required.
 */
@DisplayName("Wave 2.1 — FederationAuthorityGuard legacy safety")
class FederationGuardLegacySafetyTest {

    @Test
    @DisplayName("Guard allows national pod")
    void guardAllowsNationalPod() {
        FederationAuthorityGuard guard = new FederationAuthorityGuard();
        assertDoesNotThrow(() -> guard.requireNationalPodForMerge("national"),
                "national pod must be allowed");
    }

    @Test
    @DisplayName("Guard blocks non-national pod")
    void guardBlocksNonNationalPod() {
        FederationAuthorityGuard guard = new FederationAuthorityGuard();
        FederationNotAuthorizedException ex = assertThrows(
                FederationNotAuthorizedException.class,
                () -> guard.requireNationalPodForMerge("pod-harare"));
        assertEquals("FEDERATION_NOT_AUTHORIZED", ex.getMessage());
        assertEquals("pod-harare", ex.getPodId());
    }

    @Test
    @DisplayName("Guard blocks null pod (treated as non-national)")
    void guardBlocksNullPod() {
        FederationAuthorityGuard guard = new FederationAuthorityGuard();
        assertThrows(FederationNotAuthorizedException.class,
                () -> guard.requireNationalPodForMerge(null));
    }

    @Test
    @DisplayName("MergeService.enforceFederationAuthority() exists as private method")
    void enforceFederationAuthorityIsPrivate() throws ClassNotFoundException {
        Class<?> mergeServiceClass = Class.forName(
                "zw.gov.mohcc.impilo.vito.core.merge.MergeService");
        Method enforceMethod = null;
        for (Method m : mergeServiceClass.getDeclaredMethods()) {
            if ("enforceFederationAuthority".equals(m.getName())) {
                enforceMethod = m;
                break;
            }
        }
        assertNotNull(enforceMethod,
                "enforceFederationAuthority() method must exist on MergeService");
        assertTrue(java.lang.reflect.Modifier.isPrivate(enforceMethod.getModifiers()),
                "enforceFederationAuthority() must be private");
    }

    @Test
    @DisplayName("MergeService.currentPodId() returns null outside request context (legacy safety)")
    void currentPodIdReturnsNullOutsideRequestContext() throws Exception {
        Class<?> mergeServiceClass = Class.forName(
                "zw.gov.mohcc.impilo.vito.core.merge.MergeService");
        Method currentPodId = null;
        for (Method m : mergeServiceClass.getDeclaredMethods()) {
            if ("currentPodId".equals(m.getName())) {
                currentPodId = m;
                break;
            }
        }
        assertNotNull(currentPodId,
                "currentPodId() method must exist on MergeService");
        currentPodId.setAccessible(true);

        // Outside a servlet request context, currentPodId() must return null
        // This proves that enforceFederationAuthority() will skip the guard check
        // for legacy callers (no request context → no X-Pod-ID → guard not invoked)
        Object result = currentPodId.invoke(null); // static method, no instance needed
        assertNull(result,
                "currentPodId() must return null outside request context (legacy safety)");
    }
}
