package zw.gov.mohcc.impilo.experience.auth.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.web.client.RestTemplate;

class OidcSessionServiceTest {

    @Test
    void acceptsOnlyLocalJourneyDestinations() {
        assertEquals("/", OidcSessionService.safeReturnTo(null));
        assertEquals("/work/clinical?tab=queue", OidcSessionService.safeReturnTo("/work/clinical?tab=queue"));
    }

    @Test
    void rejectsOpenRedirectAndHeaderInjectionCandidates() {
        assertInvalid("https://attacker.example/path");
        assertInvalid("//attacker.example/path");
        assertInvalid("/\\attacker.example/path");
        assertInvalid("/safe\r\nLocation: https://attacker.example");
    }

    @Test
    @SuppressWarnings("unchecked")
    void encodesAuthorizationRequestQueryValues() {
        WebAuthSessionProperties properties = new WebAuthSessionProperties();
        properties.setEnabled(true);
        WebAuthSessionStore store = mock(WebAuthSessionStore.class);
        when(store.newOpaqueValue()).thenReturn("state", "nonce", "verifier-a", "verifier-b");
        ObjectProvider<JwtDecoder> decoder = mock(ObjectProvider.class);
        when(decoder.getIfAvailable()).thenReturn(mock(JwtDecoder.class));
        OidcSessionService service = new OidcSessionService(
                properties, store, new RestTemplate(), decoder);

        String uri = service.begin("/my/security", null, null, null, null).toASCIIString();

        assertTrue(uri.contains("scope=openid%20profile%20email%20impilo-trust-headers"));
        assertTrue(uri.contains("redirect_uri=https://impilo.mohcc.gov.zw/internal/v1/auth/oidc/callback")
                || uri.contains("redirect_uri=https%3A%2F%2Fimpilo.mohcc.gov.zw%2Finternal%2Fv1%2Fauth%2Foidc%2Fcallback"));
        assertTrue(uri.contains("code_challenge_method=S256"));
    }

    private static void assertInvalid(String candidate) {
        OidcSessionService.OidcProtocolException error = assertThrows(
                OidcSessionService.OidcProtocolException.class,
                () -> OidcSessionService.safeReturnTo(candidate));
        assertEquals("INVALID_RETURN_TO", error.code());
    }
}
