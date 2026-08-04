package zw.gov.mohcc.impilo.experience.auth.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpMethod;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.response.MockRestResponseCreators;
import org.springframework.web.client.RestTemplate;

/**
 * Refreshing an access token must not be able to sign the person out.
 *
 * Observed in the preview estate: a work surface fanned out its page-load calls, every one of
 * them saw the same near-expiry token, and each fired its own `refresh_token` grant with the
 * same credential. Keycloak logged five REFRESH_TOKEN_ERRORs in the same millisecond
 * (`invalid_token` / "Token is not active"), and `validAccessToken` answered each one by
 * deleting the session. The next `/oidc/session` returned 401 and the shell put the person
 * back on the sign-in page — after a completed OTP, which reads as "the login failed".
 *
 * Two properties are asserted here: only an explicit refusal of the grant may end a session,
 * and concurrent callers must not stampede the identity provider.
 */
class OidcSessionRefreshTest {

    private static final String SESSION_ID = "session-under-test";

    /** Expired by an hour, so every call is forced down the refresh path. */
    private static WebAuthSessionStore.SessionData expiredSession() {
        return new WebAuthSessionStore.SessionData(
                "subject", "stale-access-token", "the-refresh-token", "id-token",
                Instant.now().minusSeconds(3600), "csrf", "urn:impilo:aal2",
                List.of("pwd", "otp"), Instant.now().minusSeconds(3600), null,
                "flow", Map.of());
    }

    private record Fixture(OidcSessionService service, WebAuthSessionStore store,
                           MockRestServiceServer keycloak) {}

    @SuppressWarnings("unchecked")
    private static Fixture fixture() {
        WebAuthSessionProperties properties = new WebAuthSessionProperties();
        properties.setEnabled(true);
        properties.setClientSecret("secret");
        properties.setInternalIssuer("http://keycloak:8080/realms/impilo");

        WebAuthSessionStore store = mock(WebAuthSessionStore.class);
        when(store.findSession(SESSION_ID)).thenReturn(Optional.of(expiredSession()));

        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer keycloak = MockRestServiceServer.createServer(restTemplate);

        ObjectProvider<JwtDecoder> decoderProvider = mock(ObjectProvider.class);
        JwtDecoder decoder = mock(JwtDecoder.class);
        when(decoder.decode(anyString())).thenReturn(
                Jwt.withTokenValue("fresh").header("alg", "RS256").claim("acr", "urn:impilo:aal2").build());
        when(decoderProvider.getIfAvailable()).thenReturn(decoder);

        return new Fixture(
                new OidcSessionService(properties, store, restTemplate, decoderProvider),
                store, keycloak);
    }

    @Test
    void identityProviderOutageDoesNotSignThePersonOut() {
        Fixture f = fixture();
        f.keycloak().expect(org.springframework.test.web.client.match.MockRestRequestMatchers.method(HttpMethod.POST))
                .andRespond(MockRestResponseCreators.withServerError());

        Optional<String> token = f.service().validAccessToken(SESSION_ID);

        assertTrue(token.isEmpty(), "a failed refresh yields no usable token");
        verify(f.store(), never()).deleteSession(anyString());
    }

    @Test
    void unreachableIdentityProviderDoesNotSignThePersonOut() {
        Fixture f = fixture();
        f.keycloak().expect(org.springframework.test.web.client.match.MockRestRequestMatchers.method(HttpMethod.POST))
                .andRespond(request -> { throw new java.net.SocketTimeoutException("read timed out"); });

        assertTrue(f.service().validAccessToken(SESSION_ID).isEmpty());
        verify(f.store(), never()).deleteSession(anyString());
    }

    @Test
    void explicitRefusalOfTheGrantEndsTheSession() {
        Fixture f = fixture();
        f.keycloak().expect(org.springframework.test.web.client.match.MockRestRequestMatchers.method(HttpMethod.POST))
                .andRespond(MockRestResponseCreators.withBadRequest()
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .body("{\"error\":\"invalid_grant\"}"));

        assertTrue(f.service().validAccessToken(SESSION_ID).isEmpty());
        verify(f.store(), times(1)).deleteSession(SESSION_ID);
    }

    /** The exact error Keycloak returned in the estate: invalid_token / "Token is not active". */
    @Test
    void expiredRefreshTokenEndsTheSession() {
        Fixture f = fixture();
        f.keycloak().expect(org.springframework.test.web.client.match.MockRestRequestMatchers.method(HttpMethod.POST))
                .andRespond(MockRestResponseCreators.withBadRequest()
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .body("{\"error\":\"invalid_token\",\"error_description\":\"Token is not active\"}"));

        assertTrue(f.service().validAccessToken(SESSION_ID).isEmpty());
        verify(f.store(), times(1)).deleteSession(SESSION_ID);
    }

    @Test
    void concurrentCallersRefreshOnceAndShareTheResult() throws Exception {
        WebAuthSessionProperties properties = new WebAuthSessionProperties();
        properties.setEnabled(true);
        properties.setClientSecret("secret");
        properties.setInternalIssuer("http://keycloak:8080/realms/impilo");

        // A real store would persist the refreshed session; mirror that so the losers of the
        // race re-read a token that is now valid instead of refreshing again.
        AtomicReference<WebAuthSessionStore.SessionData> stored = new AtomicReference<>(expiredSession());
        WebAuthSessionStore store = mock(WebAuthSessionStore.class);
        when(store.findSession(SESSION_ID)).thenAnswer(i -> Optional.of(stored.get()));
        doAnswer(i -> { stored.set(i.getArgument(1)); return null; })
                .when(store).saveSession(anyString(), any());

        // The winner's refresh SUCCEEDS, and is held open long enough that every other caller is
        // definitely queued behind it. That is what makes single-flight observable: the losers
        // must re-read the session the winner saved and reuse its token. If they instead each
        // ran their own refresh, this counter would reach 8.
        AtomicInteger tokenRequests = new AtomicInteger();
        RestTemplate restTemplate = new RestTemplate();
        restTemplate.getInterceptors().add((request, body, execution) -> {
            tokenRequests.incrementAndGet();
            try {
                Thread.sleep(200);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
            String json = "{\"access_token\":\"fresh-access\",\"refresh_token\":\"next-refresh\","
                    + "\"expires_in\":300,\"id_token\":\"id\"}";
            org.springframework.mock.http.client.MockClientHttpResponse response =
                    new org.springframework.mock.http.client.MockClientHttpResponse(
                            json.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                            org.springframework.http.HttpStatus.OK);
            response.getHeaders().setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
            return response;
        });

        @SuppressWarnings("unchecked")
        ObjectProvider<JwtDecoder> decoderProvider = mock(ObjectProvider.class);
        JwtDecoder decoder = mock(JwtDecoder.class);
        when(decoder.decode(anyString())).thenReturn(Jwt.withTokenValue("fresh-access")
                .header("alg", "RS256").claim("acr", "urn:impilo:aal2").build());
        when(decoderProvider.getIfAvailable()).thenReturn(decoder);
        OidcSessionService service =
                new OidcSessionService(properties, store, restTemplate, decoderProvider);

        int callers = 8;
        ExecutorService pool = Executors.newFixedThreadPool(callers);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(callers);
        for (int i = 0; i < callers; i++) {
            pool.submit(() -> {
                try {
                    start.await();
                    service.validAccessToken(SESSION_ID);
                } catch (Exception ignored) {
                    // The refresh failing is the point; only the call count is under test.
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        assertTrue(done.await(20, TimeUnit.SECONDS), "all callers finished");
        pool.shutdownNow();

        // One grant request for eight concurrent callers. This is the property that was missing:
        // the estate saw five simultaneous refreshes of a single refresh token, of which at most
        // one could ever succeed.
        assertEquals(1, tokenRequests.get(),
                "eight concurrent callers must produce exactly one refresh_token grant");
        verify(store, never()).deleteSession(anyString());
        assertEquals("fresh-access", stored.get().accessToken());
        assertEquals("next-refresh", stored.get().refreshToken());
        assertFalse(stored.get().accessTokenExpiresAt().isBefore(Instant.now()),
                "the refreshed session carries a future expiry");
    }
}
