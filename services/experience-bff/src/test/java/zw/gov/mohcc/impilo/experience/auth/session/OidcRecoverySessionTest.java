package zw.gov.mohcc.impilo.experience.auth.session;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Constrained recovery semantics at the BFF boundary: a recovery-code login yields a
 * restricted 15-minute session diverted to the account-security landing route with an
 * opaque single-use continuation; only a later fresh ordinary authentication consumes the
 * continuation and resumes the original destination.
 */
class OidcRecoverySessionTest {

    private static final String ISSUER = "https://impilo.mohcc.gov.zw:8480/realms/impilo";

    private WebAuthSessionProperties properties;
    private WebAuthSessionStore store;
    private RestTemplate restTemplate;
    private JwtDecoder jwtDecoder;
    private OidcSessionService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        properties = new WebAuthSessionProperties();
        properties.setEnabled(true);
        properties.setClientSecret("test-secret");
        store = mock(WebAuthSessionStore.class);
        restTemplate = mock(RestTemplate.class);
        jwtDecoder = mock(JwtDecoder.class);
        ObjectProvider<JwtDecoder> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(jwtDecoder);
        service = new OidcSessionService(properties, store, restTemplate, provider);
    }

    // ── AMR classification ────────────────────────────────────────────────

    @Test
    @DisplayName("Recovery AMR markers classify as constrained recovery; ordinary factors do not")
    void classifiesRecoveryAmr() {
        assertTrue(OidcSessionService.isRecoveryAmr(List.of("pwd", "auth-recovery-authn-code-form")));
        assertTrue(OidcSessionService.isRecoveryAmr(List.of("recovery-code")));
        assertFalse(OidcSessionService.isRecoveryAmr(List.of("pwd", "otp")));
        assertFalse(OidcSessionService.isRecoveryAmr(List.of("webauthn")));
        assertFalse(OidcSessionService.isRecoveryAmr(List.of()));
    }

    // ── Recovery login establishes a restricted session + continuation ───

    @Test
    @DisplayName("Recovery login yields restricted session, 15-min expiry and recovery landing redirect")
    void recoveryLoginEstablishesConstrainedSession() {
        stubTokenExchange(List.of("pwd", "auth-recovery-authn-code-form"));
        stubTransaction(tx("/work/clinical?tab=queue", null, null));
        when(store.newOpaqueValue()).thenReturn("session-1", "csrf-1");
        when(store.saveContinuation("/work/clinical?tab=queue")).thenReturn("continuation-1");

        OidcSessionService.EstablishedSession established = service.complete("state-1", "code-1");

        assertTrue(established.data().recovery());
        assertNotNull(established.data().recoveryExpiresAt());
        assertFalse(established.data().recoveryExpiresAt()
                .isAfter(Instant.now().plusSeconds(properties.getRecoverySessionTtlSeconds() + 5)));
        assertEquals("/settings/security?recovery=1&continuation=continuation-1",
                established.returnTo());

        ArgumentCaptor<WebAuthSessionStore.SessionData> saved =
                ArgumentCaptor.forClass(WebAuthSessionStore.SessionData.class);
        verify(store).saveSession(eq("session-1"), saved.capture());
        assertTrue(saved.getValue().recovery());
        verify(store).saveContinuation("/work/clinical?tab=queue");
    }

    @Test
    @DisplayName("Ordinary login is not classified as recovery and follows returnTo directly")
    void ordinaryLoginUnaffected() {
        stubTokenExchange(List.of("pwd", "otp"));
        stubTransaction(tx("/home", null, null));
        when(store.newOpaqueValue()).thenReturn("session-2", "csrf-2");

        OidcSessionService.EstablishedSession established = service.complete("state-1", "code-1");

        assertFalse(established.data().recovery());
        assertNull(established.data().recoveryExpiresAt());
        assertEquals("/home", established.returnTo());
        verify(store, never()).saveContinuation(anyString());
    }

    // ── Continuation: single use, consumed only by fresh ordinary auth ───

    @Test
    @DisplayName("Fresh ordinary authentication consumes the continuation exactly once and resumes")
    void freshAuthenticationConsumesContinuation() {
        stubTokenExchange(List.of("pwd", "otp"));
        stubTransaction(tx("/", "prev-recovery-session", "continuation-1"));
        when(store.newOpaqueValue()).thenReturn("session-3", "csrf-3");
        when(store.findSession("prev-recovery-session")).thenReturn(Optional.of(recoverySession()));
        when(store.consumeContinuation("continuation-1"))
                .thenReturn(Optional.of("/work/clinical?tab=queue"));

        OidcSessionService.EstablishedSession established = service.complete("state-1", "code-1");

        assertFalse(established.data().recovery());
        assertEquals("/work/clinical?tab=queue", established.returnTo());
        verify(store).consumeContinuation("continuation-1");
        // Recovery session terminated by the fresh authentication.
        verify(store).deleteSession("prev-recovery-session");
    }

    @Test
    @DisplayName("A replayed or expired continuation is rejected and falls back to the transaction returnTo")
    void replayedContinuationRejected() {
        stubTokenExchange(List.of("pwd", "otp"));
        stubTransaction(tx("/fallback", null, "continuation-used"));
        when(store.newOpaqueValue()).thenReturn("session-4", "csrf-4");
        when(store.consumeContinuation("continuation-used")).thenReturn(Optional.empty());

        OidcSessionService.EstablishedSession established = service.complete("state-1", "code-1");

        assertEquals("/fallback", established.returnTo());
    }

    @Test
    @DisplayName("A recovery re-login does NOT consume the continuation — it is preserved for ordinary auth")
    void recoveryReloginPreservesContinuation() {
        stubTokenExchange(List.of("pwd", "auth-recovery-authn-code-form"));
        stubTransaction(tx("/", null, "continuation-1"));
        when(store.newOpaqueValue()).thenReturn("session-5", "csrf-5");

        OidcSessionService.EstablishedSession established = service.complete("state-1", "code-1");

        assertTrue(established.data().recovery());
        assertEquals("/settings/security?recovery=1&continuation=continuation-1",
                established.returnTo());
        verify(store, never()).consumeContinuation(anyString());
    }

    // ── Protocol protections retained ─────────────────────────────────────

    @Test
    @DisplayName("Callback replay (consumed state) fails closed with OIDC_TRANSACTION_EXPIRED")
    void callbackReplayRejected() {
        when(store.consumeTransaction("replayed-state")).thenReturn(Optional.empty());
        OidcSessionService.OidcProtocolException error = assertThrows(
                OidcSessionService.OidcProtocolException.class,
                () -> service.complete("replayed-state", "code-1"));
        assertEquals("OIDC_TRANSACTION_EXPIRED", error.code());
    }

    @Test
    @DisplayName("Nonce mismatch in the ID token fails closed")
    void nonceMismatchRejected() {
        stubTokenExchange(List.of("pwd", "otp"), "different-nonce");
        stubTransaction(tx("/home", null, null));
        OidcSessionService.OidcProtocolException error = assertThrows(
                OidcSessionService.OidcProtocolException.class,
                () -> service.complete("state-1", "code-1"));
        assertEquals("OIDC_NONCE_MISMATCH", error.code());
    }

    @Test
    @DisplayName("Continuation ids must be opaque tokens; malformed values are dropped")
    void malformedContinuationIdIgnored() {
        when(store.newOpaqueValue()).thenReturn("state-x", "nonce-x", "ver-a", "ver-b");
        service.begin("/home", null, null, null, null, "not/valid?id");
        ArgumentCaptor<WebAuthSessionStore.AuthTransaction> saved =
                ArgumentCaptor.forClass(WebAuthSessionStore.AuthTransaction.class);
        verify(store).saveTransaction(saved.capture());
        assertNull(saved.getValue().continuationId());
    }

    @Test
    @DisplayName("Well-formed continuation ids are carried into the transaction")
    void wellFormedContinuationIdCarried() {
        when(store.newOpaqueValue()).thenReturn("state-x", "nonce-x", "ver-a", "ver-b");
        service.begin("/home", null, null, null, null, "AbC123_-AbC123_-AbC1");
        ArgumentCaptor<WebAuthSessionStore.AuthTransaction> saved =
                ArgumentCaptor.forClass(WebAuthSessionStore.AuthTransaction.class);
        verify(store).saveTransaction(saved.capture());
        assertEquals("AbC123_-AbC123_-AbC1", saved.getValue().continuationId());
    }

    // ── CP3 closure proofs: lifetime, step-up laundering, forced fresh auth ─

    @Test
    @DisplayName("Recovery session lifetime is at most 15 minutes by default")
    void recoveryLifetimeAtMostFifteenMinutes() {
        assertTrue(new WebAuthSessionProperties().getRecoverySessionTtlSeconds() <= 900,
                "default recovery TTL must not exceed 900 seconds");
    }

    @Test
    @DisplayName("Step-up cannot launder a recovery session into ordinary AAL2 — classification is re-derived from AMR")
    void stepUpCannotLaunderRecoveryIntoAal2() {
        // The user step-ups from a recovery session but authenticates AGAIN with a recovery
        // code. Even though Keycloak minted numeric acr aal2 in the stub, the new session
        // must remain CONSTRAINED_RECOVERY: authority follows AMR, not the numeric claim.
        stubTokenExchange(List.of("pwd", "auth-recovery-authn-code-form"));
        stubTransaction(tx("/work/clinical", "prev-recovery-session", null));
        when(store.newOpaqueValue()).thenReturn("session-6", "csrf-6");
        when(store.findSession("prev-recovery-session")).thenReturn(Optional.of(recoverySession()));
        when(store.saveContinuation("/work/clinical")).thenReturn("continuation-6");

        OidcSessionService.EstablishedSession established = service.complete("state-1", "code-1");

        assertTrue(established.data().recovery(), "recovery AMR must keep the session constrained");
        assertNotNull(established.data().recoveryExpiresAt());
        // Still diverted to the recovery landing, never the protected destination.
        assertTrue(established.returnTo().startsWith("/settings/security?recovery=1"));
    }

    @Test
    @DisplayName("Step-up/action from an existing session always forces full fresh authentication at the IdP")
    void stepUpForcesFreshAuthentication() {
        when(store.newOpaqueValue()).thenReturn("state-x", "nonce-x", "ver-a", "ver-b");
        java.net.URI authorize = service.begin("/home", "urn:impilo:aal2", null,
                "existing-session", null, null);
        String query = authorize.getQuery();
        assertTrue(query.contains("prompt=login"), "must force re-authentication");
        assertTrue(query.contains("max_age=0"), "must not accept a cached IdP SSO session");
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private static WebAuthSessionStore.AuthTransaction tx(String returnTo, String previousSessionId,
                                                          String continuationId) {
        return new WebAuthSessionStore.AuthTransaction(
                "state-1", "nonce-1", "verifier-1", returnTo, null, null,
                previousSessionId, Instant.now(), continuationId);
    }

    private void stubTransaction(WebAuthSessionStore.AuthTransaction tx) {
        when(store.consumeTransaction("state-1")).thenReturn(Optional.of(tx));
    }

    private void stubTokenExchange(List<String> amr) {
        stubTokenExchange(amr, "nonce-1");
    }

    @SuppressWarnings("unchecked")
    private void stubTokenExchange(List<String> amr, String idTokenNonce) {
        JsonNode tokens;
        try {
            tokens = new ObjectMapper().readTree(
                    "{\"access_token\":\"acctok\",\"refresh_token\":\"reftok\","
                            + "\"id_token\":\"idtok\",\"expires_in\":300}");
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(), eq(JsonNode.class)))
                .thenReturn(ResponseEntity.ok(tokens));

        Jwt idJwt = Jwt.withTokenValue("idtok").header("alg", "RS256")
                .claim("iss", ISSUER)
                .claim("aud", List.of("experience-ui"))
                .claim("nonce", idTokenNonce)
                .claim("sub", "user-1")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .build();
        Jwt accessJwt = Jwt.withTokenValue("acctok").header("alg", "RS256")
                .claim("iss", ISSUER)
                .claim("sub", "user-1")
                .claim("acr", "urn:impilo:aal2")
                .claim("amr", amr)
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .build();
        when(jwtDecoder.decode("idtok")).thenReturn(idJwt);
        when(jwtDecoder.decode("acctok")).thenReturn(accessJwt);
    }

    private static WebAuthSessionStore.SessionData recoverySession() {
        return new WebAuthSessionStore.SessionData(
                "user-1", "acctok", "reftok", "idtok",
                Instant.now().plusSeconds(300), "csrf", "urn:impilo:aal2",
                List.of("pwd", "auth-recovery-authn-code-form"), Instant.now(), null,
                "flow-1", Map.of(), true, Instant.now().plusSeconds(900));
    }
}
