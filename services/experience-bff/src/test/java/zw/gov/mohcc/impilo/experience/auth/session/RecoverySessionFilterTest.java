package zw.gov.mohcc.impilo.experience.auth.session;

import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Route restriction for constrained recovery sessions: only the OIDC auth routes and the
 * account-security settings surface are reachable; everything else returns the canonical
 * RECOVERY_REQUIRED decision as 403.
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

    @Test
    @DisplayName("Recovery session is refused outside the allowlist with RECOVERY_REQUIRED")
    void deniesRecoverySessionOutsideAllowlist() throws Exception {
        when(sessions.session("session-1")).thenReturn(Optional.of(session(true)));
        for (String path : List.of(
                "/internal/v1/patients/42",
                "/internal/v1/clinical/encounters",
                "/internal/v1/admin/users",
                "/internal/v1/claims/submit",
                "/internal/v1/marketplace/orders",
                "/internal/v1/work-context/select")) {
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(request("GET", path, true), response, new MockFilterChain());
            assertEquals(403, response.getStatus(), path);
            assertTrue(response.getContentAsString().contains("\"RECOVERY_REQUIRED\""), path);
            assertTrue(response.getContentAsString().contains("CONSTRAINED_RECOVERY_SESSION"), path);
        }
    }

    @Test
    @DisplayName("Recovery session may reach OIDC auth routes and account-security settings")
    void permitsRecoverySurface() throws Exception {
        for (String path : List.of(
                "/internal/v1/auth/oidc/session",
                "/internal/v1/auth/oidc/logout",
                "/internal/v1/auth/oidc/action",
                "/internal/v1/auth/oidc/authorize",
                "/internal/v1/auth/logout",
                "/internal/v1/settings/security",
                "/internal/v1/settings/security/credentials/cred-1",
                "/internal/v1/settings/security/sessions/session-2")) {
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(request("GET", path, true), response, new MockFilterChain());
            assertEquals(200, response.getStatus(), path);
        }
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
        filter.doFilter(request("GET", "/internal/v1/patients/42", false),
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
