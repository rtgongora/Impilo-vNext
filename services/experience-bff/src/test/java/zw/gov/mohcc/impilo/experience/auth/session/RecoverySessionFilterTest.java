package zw.gov.mohcc.impilo.experience.auth.session;

import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Canonical recovery boundary: every operation reachable under
 * {@code /internal/v1/settings/security/**} and the OIDC auth surface is enumerated with its
 * expected CONSTRAINED_RECOVERY disposition. The decision is a canonical ACTION:RESOURCE_TYPE
 * check (mirroring tshepo-authz {@code recoveryPermittedActions}), never a raw path prefix —
 * unknown operations under a "permitted" prefix are refused (fail closed).
 */
class RecoverySessionFilterTest {

    private OidcSessionService sessions;
    private RecoverySessionFilter filter;

    @BeforeEach
    void setUp() {
        sessions = mock(OidcSessionService.class);
        filter = new RecoverySessionFilter(sessions);
    }

    private static MockHttpServletRequest request(String method, String path, boolean withCookie) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setRequestURI(path);
        if (withCookie) {
            request.setCookies(new Cookie(WebAuthSessionStore.SESSION_COOKIE, "session-1"));
        }
        return request;
    }

    private static WebAuthSessionStore.SessionData session(boolean recovery) {
        return new WebAuthSessionStore.SessionData(
                "user-1", "acctok", "reftok", "idtok", Instant.now().plusSeconds(300),
                "csrf", "urn:impilo:aal2", List.of("pwd"), Instant.now(), null, "flow",
                Map.of(), recovery, recovery ? Instant.now().plusSeconds(900) : null);
    }

    /**
     * Exhaustive operation table for the account-security and OIDC surface. One row per
     * controller operation:
     *  - SecuritySettingsController: GET snapshot, DELETE credential, DELETE session,
     *    POST logout-others;
     *  - LostDeviceRecoveryBffController: POST create case, GET case, POST approval;
     *  - OidcSessionController: authorize, callback, session, step-up, action, logout;
     *  - legacy logout route.
     */
    @ParameterizedTest(name = "{0} {1} during recovery -> allowed={2}")
    @CsvSource({
            // --- SecuritySettingsController ------------------------------------------------
            "GET,    /internal/v1/settings/security,                                true",
            "DELETE, /internal/v1/settings/security/credentials/cred-1,             false",
            "DELETE, /internal/v1/settings/security/sessions/session-2,             true",
            "POST,   /internal/v1/settings/security/sessions/logout-others,         true",
            // --- LostDeviceRecoveryBffController (administrative recovery) ------------------
            "POST,   /internal/v1/settings/security/recovery-cases,                 false",
            "GET,    /internal/v1/settings/security/recovery-cases/11111111-1111-4111-8111-111111111111, false",
            "POST,   /internal/v1/settings/security/recovery-cases/11111111-1111-4111-8111-111111111111/approval, false",
            // --- OIDC auth surface ----------------------------------------------------------
            "GET,    /internal/v1/auth/oidc/authorize,                              true",
            "GET,    /internal/v1/auth/oidc/callback,                               true",
            "GET,    /internal/v1/auth/oidc/session,                                true",
            "POST,   /internal/v1/auth/oidc/step-up,                                true",
            "POST,   /internal/v1/auth/oidc/action,                                 true",
            "POST,   /internal/v1/auth/oidc/logout,                                 true",
            "POST,   /internal/v1/auth/logout,                                      true",
            // --- Fail-closed on unregistered operations, even under "permitted" prefixes ----
            "GET,    /internal/v1/settings/security/credentials/cred-1,             false",
            "PUT,    /internal/v1/settings/security,                                false",
            "POST,   /internal/v1/settings/security/credentials,                    false",
            "GET,    /internal/v1/auth/oidc/unknown-op,                             false",
            // --- Everything else stays denied -----------------------------------------------
            "GET,    /internal/v1/patients/42,                                      false",
            "POST,   /internal/v1/claims/submit,                                    false",
            "GET,    /internal/v1/admin/users,                                      false",
            "POST,   /internal/v1/work-context/select,                              false",
    })
    void recoveryDispositionPerOperation(String method, String path, boolean allowed) throws Exception {
        when(sessions.session("session-1")).thenReturn(Optional.of(session(true)));
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request(method, path, true), response, new MockFilterChain());
        if (allowed) {
            assertEquals(200, response.getStatus(), method + " " + path);
        } else {
            assertEquals(403, response.getStatus(), method + " " + path);
            assertTrue(response.getContentAsString().contains("\"RECOVERY_REQUIRED\""), path);
            assertTrue(response.getContentAsString().contains("CONSTRAINED_RECOVERY_SESSION"), path);
        }
    }

    @Test
    @DisplayName("Canonical mapping: credential deletion maps to DELETE:AUTH_FACTOR which is not allowlisted")
    void credentialDeletionCanonicallyDenied() {
        assertFalse(RecoverySessionFilter.isPermitted("DELETE",
                "/internal/v1/settings/security/credentials/any"));
        assertFalse(RecoverySessionFilter.PERMITTED_CANONICAL.contains("DELETE:AUTH_FACTOR"));
        assertFalse(RecoverySessionFilter.PERMITTED_CANONICAL.contains("UPDATE:AUTH_FACTOR"));
    }

    @Test
    @DisplayName("Ordinary sessions and anonymous requests pass through untouched")
    void passesThroughOrdinaryTraffic() throws Exception {
        when(sessions.session("session-1")).thenReturn(Optional.of(session(false)));
        MockHttpServletResponse withOrdinarySession = new MockHttpServletResponse();
        filter.doFilter(request("GET", "/internal/v1/patients/42", true),
                withOrdinarySession, new MockFilterChain());
        assertEquals(200, withOrdinarySession.getStatus());

        MockHttpServletResponse anonymous = new MockHttpServletResponse();
        filter.doFilter(request("DELETE", "/internal/v1/settings/security/credentials/cred-1", false),
                anonymous, new MockFilterChain());
        assertEquals(200, anonymous.getStatus());
    }

    @Test
    @DisplayName("Expired or unknown recovery session ids pass through (session gate handles auth)")
    void unknownSessionPassesThrough() throws Exception {
        when(sessions.session("session-1")).thenReturn(Optional.empty());
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request("GET", "/internal/v1/patients/42", true),
                response, new MockFilterChain());
        assertEquals(200, response.getStatus());
    }
}
