package zw.gov.mohcc.impilo.experience.controller;

import jakarta.servlet.http.Cookie;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import zw.gov.mohcc.impilo.experience.auth.session.OidcSessionService;
import zw.gov.mohcc.impilo.experience.auth.session.WebAuthSessionProperties;
import zw.gov.mohcc.impilo.experience.auth.session.WebAuthSessionStore;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Required-action restriction for constrained recovery sessions: only enrollment of a
 * replacement approved factor may be initiated. Recovery-code regeneration and password
 * change would preserve account access without fresh ordinary authentication and are refused
 * with the canonical RECOVERY_REQUIRED decision. Ordinary sessions keep the full action set.
 */
class OidcSessionControllerRecoveryActionTest {

    private OidcSessionService sessions;
    private OidcSessionController controller;

    @BeforeEach
    void setUp() {
        sessions = mock(OidcSessionService.class);
        controller = new OidcSessionController(sessions, new WebAuthSessionProperties());
        when(sessions.begin(any(), any(), any(), any(), any()))
                .thenReturn(URI.create("https://idp/auth"));
        when(sessions.begin(any(), any(), any(), any(), any(), any()))
                .thenReturn(URI.create("https://idp/auth"));
    }

    private MockHttpServletRequest requestWithSession(boolean recovery) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/internal/v1/auth/oidc/action");
        request.setCookies(new Cookie(WebAuthSessionStore.SESSION_COOKIE, "session-1"));
        when(sessions.session("session-1")).thenReturn(Optional.of(new WebAuthSessionStore.SessionData(
                "user-1", "acctok", "reftok", "idtok", Instant.now().plusSeconds(300),
                "csrf", "urn:impilo:aal1", List.of(recovery ? "recovery-code" : "pwd"),
                Instant.now(), null, "flow", Map.of(), recovery,
                recovery ? Instant.now().plusSeconds(900) : null)));
        return request;
    }

    @ParameterizedTest(name = "recovery session action={0} -> allowed={1}")
    @CsvSource({
            "CONFIGURE_TOTP,                   true",
            "webauthn-register,                true",
            "webauthn-register-passwordless,   true",
            "CONFIGURE_RECOVERY_AUTHN_CODES,   false",
            "UPDATE_PASSWORD,                  false",
    })
    void recoveryActionRestriction(String action, boolean allowed) {
        ResponseEntity<Map<String, Object>> response = controller.action(
                requestWithSession(true), null, Map.of("action", action, "returnTo", "/settings/security"));
        if (allowed) {
            assertEquals(200, response.getStatusCode().value(), action);
        } else {
            assertEquals(403, response.getStatusCode().value(), action);
            Map<String, Object> error = (Map<String, Object>) response.getBody().get("error");
            assertEquals("RECOVERY_REQUIRED", error.get("code"));
            assertEquals("RECOVERY_ACTION_NOT_PERMITTED", error.get("reason_code"));
        }
    }

    @ParameterizedTest(name = "ordinary session action={0} stays allowed")
    @CsvSource({"CONFIGURE_TOTP", "CONFIGURE_RECOVERY_AUTHN_CODES", "UPDATE_PASSWORD"})
    void ordinarySessionKeepsFullActionSet(String action) {
        ResponseEntity<Map<String, Object>> response = controller.action(
                requestWithSession(false), null, Map.of("action", action, "returnTo", "/settings/security"));
        assertEquals(200, response.getStatusCode().value(), action);
    }
}
