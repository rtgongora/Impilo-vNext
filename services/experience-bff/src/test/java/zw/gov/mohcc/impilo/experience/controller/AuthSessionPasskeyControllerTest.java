package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.experience.client.RulesServiceClient;
import zw.gov.mohcc.impilo.experience.client.VarapiServiceClient;
import zw.gov.mohcc.impilo.experience.client.VitoServiceClient;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * L1 passkey (WebAuthn passwordless) — initiate + auth-code callback, proven against a
 * WireMock Keycloak: {@code authorize} URL construction and {@code token} auth-code exchange.
 *
 * <p>Security invariant under test: a session is minted ONLY on a successful real token
 * exchange. A missing code, or a rejected/empty exchange, is a uniform auth-failure — never
 * a fabricated session. Password/ROPC login is untouched.</p>
 *
 * <p>NOTE (2026-07): the experience-bff test module is transiently blocked from running by an
 * unrelated stale test in a concurrent session; this test is written to pass once that clears.</p>
 */
class AuthSessionPasskeyControllerTest {

    private static final String REALM = "impilo";
    private static final String TOKEN_PATH = "/realms/" + REALM + "/protocol/openid-connect/token";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private WireMockServer keycloak;
    private AuthSessionController controller;

    @BeforeEach
    void setUp() {
        keycloak = new WireMockServer(wireMockConfig().dynamicPort());
        keycloak.start();

        controller = new AuthSessionController(
                new RestTemplate(),
                Mockito.mock(VarapiServiceClient.class),
                Mockito.mock(VitoServiceClient.class),
                Mockito.mock(RulesServiceClient.class),
                null);
        ReflectionTestUtils.setField(controller, "keycloakUrl", keycloak.baseUrl());
        ReflectionTestUtils.setField(controller, "realm", REALM);
        ReflectionTestUtils.setField(controller, "passkeyEnabled", true);
        ReflectionTestUtils.setField(controller, "passkeyClientId", "impilo-passkey");
        ReflectionTestUtils.setField(controller, "passkeyRedirectUri", "http://localhost:3000/auth/login/passkey/callback");
        ReflectionTestUtils.setField(controller, "passkeyScope", "openid profile email");
    }

    @AfterEach
    void tearDown() {
        keycloak.stop();
    }

    // ── initiate ──────────────────────────────────────────────────────────

    @Test
    @SuppressWarnings("unchecked")
    void initiateBuildsAuthorizeUrlWithPkceAndReturnsVerifier() {
        var response = controller.passkeyInitiate("req-1", "corr-1", Map.of());
        assertEquals(HttpStatus.OK, response.getStatusCode());

        Map<String, Object> data = (Map<String, Object>) response.getBody().get("data");
        Map<String, Object> attrs = (Map<String, Object>) data.get("attributes");
        String url = (String) attrs.get("authorizeUrl");
        String codeVerifier = (String) attrs.get("codeVerifier");
        String state = (String) attrs.get("state");

        assertNotNull(codeVerifier);
        assertNotNull(state);
        assertTrue(url.startsWith(keycloak.baseUrl() + "/realms/" + REALM + "/protocol/openid-connect/auth"));
        assertTrue(url.contains("client_id=impilo-passkey"), url);
        assertTrue(url.contains("response_type=code"), url);
        assertTrue(url.contains("code_challenge_method=S256"), url);
        assertTrue(url.contains("redirect_uri=http%3A%2F%2Flocalhost%3A3000%2Fauth%2Flogin%2Fpasskey%2Fcallback"), url);
        assertTrue(url.contains("state=" + state), url);
        // PKCE correctness: the challenge in the URL is the S256 of the returned verifier.
        assertTrue(url.contains("code_challenge=" + s256Base64Url(codeVerifier)), url);
        // A plain sign-in must NOT carry the device-registration action.
        assertFalse(url.contains("kc_action="), url);
        assertEquals(Boolean.FALSE, attrs.get("register"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void initiateWithRegisterAppendsWebauthnRegisterAction() {
        var response = controller.passkeyInitiate("req-1", "corr-1", Map.of("register", true, "email", "grace@example.com"));
        Map<String, Object> data = (Map<String, Object>) response.getBody().get("data");
        Map<String, Object> attrs = (Map<String, Object>) data.get("attributes");
        String url = (String) attrs.get("authorizeUrl");

        assertTrue(url.contains("kc_action=webauthn-register-passwordless"), url);
        assertTrue(url.contains("login_hint=grace%40example.com"), url);
        assertEquals(Boolean.TRUE, attrs.get("register"));
    }

    @Test
    void initiateReturns501WhenFlagOff() {
        ReflectionTestUtils.setField(controller, "passkeyEnabled", false);
        var response = controller.passkeyInitiate("req-1", "corr-1", Map.of());
        assertEquals(HttpStatus.NOT_IMPLEMENTED, response.getStatusCode());
        assertEquals("PASSKEY_NOT_ENABLED", nestedCode(response));
    }

    // ── callback ──────────────────────────────────────────────────────────

    @Test
    @SuppressWarnings("unchecked")
    void callbackExchangesCodeForRealSession() {
        keycloak.stubFor(post(urlEqualTo(TOKEN_PATH))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(tokenResponseJson(jwt("kc-sub-123", "grace@example.com", "Grace Mavenge", "CITIZEN")))));

        var response = controller.passkeyCallback("req-1", "corr-1",
                Map.of("code", "kc-auth-code", "codeVerifier", "verifier-xyz"));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        Map<String, Object> data = (Map<String, Object>) response.getBody().get("data");
        assertEquals("auth_token", data.get("type"));
        Map<String, Object> attrs = (Map<String, Object>) data.get("attributes");
        assertNotNull(attrs.get("token"));
        Map<String, Object> user = (Map<String, Object>) attrs.get("user");
        assertEquals("grace@example.com", user.get("email"));
        assertEquals("CITIZEN", user.get("actorType"));
        assertEquals("passkey", user.get("method"));

        // Proves the exchange was a real auth-code grant carrying the PKCE verifier.
        keycloak.verify(postRequestedFor(urlEqualTo(TOKEN_PATH))
                .withRequestBody(containing("grant_type=authorization_code"))
                .withRequestBody(containing("code=kc-auth-code"))
                .withRequestBody(containing("code_verifier=verifier-xyz"))
                .withRequestBody(containing("client_id=impilo-passkey")));
    }

    @Test
    void callbackWithMissingCodeIsAuthFailureAndNeverCallsKeycloak() {
        var response = controller.passkeyCallback("req-1", "corr-1", Map.of("codeVerifier", "verifier-xyz"));
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertEquals("INVALID_CREDENTIALS", nestedCode(response));
        keycloak.verify(0, postRequestedFor(urlEqualTo(TOKEN_PATH)));
    }

    @Test
    void callbackReturnsAuthFailureWhenExchangeRejected() {
        keycloak.stubFor(post(urlEqualTo(TOKEN_PATH))
                .willReturn(aResponse()
                        .withStatus(401)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"error\":\"invalid_grant\"}")));

        var response = controller.passkeyCallback("req-1", "corr-1",
                Map.of("code", "bad-code", "codeVerifier", "verifier-xyz"));
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertEquals("INVALID_CREDENTIALS", nestedCode(response));
    }

    @Test
    void callbackReturns501WhenFlagOff() {
        ReflectionTestUtils.setField(controller, "passkeyEnabled", false);
        var response = controller.passkeyCallback("req-1", "corr-1", Map.of("code", "kc-auth-code"));
        assertEquals(HttpStatus.NOT_IMPLEMENTED, response.getStatusCode());
        keycloak.verify(0, postRequestedFor(urlEqualTo(TOKEN_PATH)));
    }

    // ── helpers ───────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private static String nestedCode(ResponseEntity<Map<String, Object>> response) {
        Map<String, Object> error = (Map<String, Object>) response.getBody().get("error");
        return (String) error.get("code");
    }

    private static String tokenResponseJson(String accessJwt) {
        return "{\"access_token\":\"" + accessJwt + "\","
                + "\"refresh_token\":\"refresh-abc\","
                + "\"expires_in\":28800,"
                + "\"refresh_expires_in\":172800,"
                + "\"token_type\":\"Bearer\"}";
    }

    /** Minimal unsigned JWT (header.payload.sig) — the BFF decodes only the base64url payload. */
    private static String jwt(String sub, String email, String name, String... roles) {
        try {
            var rolesArray = MAPPER.createArrayNode();
            for (String r : roles) rolesArray.add(r);
            var payload = MAPPER.createObjectNode();
            payload.put("sub", sub);
            payload.put("email", email);
            payload.put("name", name);
            payload.set("realm_access", MAPPER.createObjectNode().set("roles", rolesArray));
            String header = base64Url("{\"alg\":\"RS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
            String body = base64Url(MAPPER.writeValueAsBytes(payload));
            return header + "." + body + ".sig";
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String s256Base64Url(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return base64Url(digest);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String base64Url(byte[] b) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }
}
