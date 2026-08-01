package zw.gov.mohcc.impilo.experience.auth.session;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

/** Keycloak authorization-code + PKCE orchestration for the web BFF. */
@Service
public class OidcSessionService {
    private static final List<String> ALLOWED_ACR = List.of(
            "urn:impilo:aal1", "urn:impilo:aal2", "urn:impilo:aal3");
    private static final List<String> ALLOWED_ACTIONS = List.of(
            "CONFIGURE_TOTP", "webauthn-register", "webauthn-register-passwordless",
            "CONFIGURE_RECOVERY_AUTHN_CODES", "UPDATE_PASSWORD");

    private final WebAuthSessionProperties properties;
    private final WebAuthSessionStore store;
    private final RestTemplate restTemplate;
    private final JwtDecoder jwtDecoder;

    public OidcSessionService(WebAuthSessionProperties properties, WebAuthSessionStore store,
                              @Qualifier("idpRestTemplate") RestTemplate restTemplate,
                              ObjectProvider<JwtDecoder> jwtDecoderProvider) {
        this.properties = properties;
        this.store = store;
        this.restTemplate = restTemplate;
        this.jwtDecoder = jwtDecoderProvider.getIfAvailable();
    }

    public URI begin(String returnTo, String requestedAcr, String requiredAction,
                     String previousSessionId, String loginHint) {
        requireEnabled();
        String safeReturnTo = safeReturnTo(returnTo);
        String acr = normalizeAcr(requestedAcr);
        String action = normalizeAction(requiredAction);
        String state = store.newOpaqueValue();
        String nonce = store.newOpaqueValue();
        String verifier = store.newOpaqueValue() + store.newOpaqueValue();
        String challenge = base64Url(sha256(verifier));
        store.saveTransaction(new WebAuthSessionStore.AuthTransaction(
                state, nonce, verifier, safeReturnTo, acr, action, previousSessionId, Instant.now()));

        UriComponentsBuilder builder = UriComponentsBuilder
                .fromUriString(properties.getPublicIssuer() + "/protocol/openid-connect/auth")
                .queryParam("client_id", properties.getClientId())
                .queryParam("redirect_uri", properties.getRedirectUri())
                .queryParam("response_type", "code")
                .queryParam("scope", "openid profile email impilo-trust-headers")
                .queryParam("state", state)
                .queryParam("nonce", nonce)
                .queryParam("code_challenge", challenge)
                .queryParam("code_challenge_method", "S256");
        if (acr != null) builder.queryParam("acr_values", acr);
        if (action != null) builder.queryParam("kc_action", action);
        if (loginHint != null && !loginHint.isBlank()) builder.queryParam("login_hint", loginHint.trim());
        if (previousSessionId != null) builder.queryParam("prompt", "login").queryParam("max_age", 0);
        return builder.encode().build().toUri();
    }

    public EstablishedSession complete(String state, String code) {
        requireEnabled();
        WebAuthSessionStore.AuthTransaction tx = store.consumeTransaction(state)
                .orElseThrow(() -> new OidcProtocolException("OIDC_TRANSACTION_EXPIRED"));
        if (tx.createdAt().plusSeconds(properties.getTransactionTtlSeconds()).isBefore(Instant.now())) {
            throw new OidcProtocolException("OIDC_TRANSACTION_EXPIRED");
        }
        if (code == null || code.isBlank()) throw new OidcProtocolException("OIDC_CODE_MISSING");

        JsonNode tokens = tokenRequest(Map.of(
                "grant_type", "authorization_code",
                "code", code,
                "redirect_uri", properties.getRedirectUri(),
                "code_verifier", tx.codeVerifier()));
        String accessToken = requiredText(tokens, "access_token");
        String refreshToken = requiredText(tokens, "refresh_token");
        String idToken = requiredText(tokens, "id_token");
        Jwt idJwt = jwtDecoder.decode(idToken);
        Jwt accessJwt = jwtDecoder.decode(accessToken);
        validateIdToken(idJwt, tx.nonce());
        validateRequestedAcr(tx.requestedAcr(), accessJwt.getClaimAsString("acr"));

        String sessionId = store.newOpaqueValue();
        String csrf = store.newOpaqueValue();
        int expiresIn = tokens.path("expires_in").asInt(300);
        WebAuthSessionStore.SessionData session = sessionFromTokens(
                accessJwt, accessToken, refreshToken, idToken,
                Instant.now().plusSeconds(expiresIn), csrf, tx);
        store.saveSession(sessionId, session);
        if (tx.previousSessionId() != null) store.deleteSession(tx.previousSessionId());
        return new EstablishedSession(sessionId, session, tx.returnTo());
    }

    public Optional<WebAuthSessionStore.SessionData> session(String sessionId) {
        return store.findSession(sessionId);
    }

    public Optional<String> validAccessToken(String sessionId) {
        Optional<WebAuthSessionStore.SessionData> existing = store.findSession(sessionId);
        if (existing.isEmpty()) return Optional.empty();
        WebAuthSessionStore.SessionData session = existing.get();
        if (session.accessTokenExpiresAt().isAfter(Instant.now().plusSeconds(60))) {
            return Optional.of(session.accessToken());
        }
        try {
            JsonNode tokens = tokenRequest(Map.of("grant_type", "refresh_token", "refresh_token", session.refreshToken()));
            String accessToken = requiredText(tokens, "access_token");
            String refreshToken = tokens.path("refresh_token").asText(session.refreshToken());
            Jwt accessJwt = jwtDecoder.decode(accessToken);
            WebAuthSessionStore.SessionData refreshed = new WebAuthSessionStore.SessionData(
                    session.subject(), accessToken, refreshToken, session.idToken(),
                    Instant.now().plusSeconds(tokens.path("expires_in").asInt(300)),
                    session.csrfToken(), accessJwt.getClaimAsString("acr"), amr(accessJwt),
                    claimInstant(accessJwt, "auth_time"), session.stepUpTime(), session.flowId(), session.profile());
            store.saveSession(sessionId, refreshed);
            return Optional.of(accessToken);
        } catch (Exception e) {
            store.deleteSession(sessionId);
            return Optional.empty();
        }
    }

    public void logout(String sessionId) {
        Optional<WebAuthSessionStore.SessionData> current = store.findSession(sessionId);
        current.ifPresent(session -> {
            revoke(session.refreshToken(), "refresh_token");
            revoke(session.accessToken(), "access_token");
        });
        store.deleteSession(sessionId);
    }

    public boolean csrfMatches(String sessionId, String value) { return store.csrfMatches(sessionId, value); }

    public static String safeReturnTo(String candidate) {
        if (candidate == null || candidate.isBlank()) return "/";
        String value = candidate.trim();
        if (!value.startsWith("/") || value.startsWith("//") || value.contains("\\") ||
                value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0) {
            throw new OidcProtocolException("INVALID_RETURN_TO");
        }
        return value;
    }

    private JsonNode tokenRequest(Map<String, String> parameters) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        parameters.forEach(form::add);
        form.add("client_id", properties.getClientId());
        form.add("client_secret", properties.getClientSecret());
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        ResponseEntity<JsonNode> response = restTemplate.exchange(
                properties.getInternalIssuer() + "/protocol/openid-connect/token", HttpMethod.POST,
                new HttpEntity<>(form, headers), JsonNode.class);
        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            throw new OidcProtocolException("OIDC_TOKEN_EXCHANGE_FAILED");
        }
        return response.getBody();
    }

    private void revoke(String token, String tokenTypeHint) {
        if (token == null || token.isBlank()) return;
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("token", token);
        form.add("token_type_hint", tokenTypeHint);
        form.add("client_id", properties.getClientId());
        form.add("client_secret", properties.getClientSecret());
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        try {
            restTemplate.exchange(properties.getInternalIssuer() + "/protocol/openid-connect/revoke",
                    HttpMethod.POST, new HttpEntity<>(form, headers), Void.class);
        } catch (Exception ignored) {
            // Local deletion is authoritative for the browser. Keycloak revocation is best effort
            // here because logout must not strand an opaque session cookie on an IdP outage.
        }
    }

    private void validateIdToken(Jwt jwt, String nonce) {
        if (jwt.getIssuer() == null || !properties.getPublicIssuer().equals(jwt.getIssuer().toString())) {
            throw new OidcProtocolException("OIDC_ISSUER_MISMATCH");
        }
        if (!jwt.getAudience().contains(properties.getClientId())) {
            throw new OidcProtocolException("OIDC_AUDIENCE_MISMATCH");
        }
        if (!MessageDigest.isEqual(nonce.getBytes(StandardCharsets.UTF_8),
                Optional.ofNullable(jwt.getClaimAsString("nonce")).orElse("").getBytes(StandardCharsets.UTF_8))) {
            throw new OidcProtocolException("OIDC_NONCE_MISMATCH");
        }
    }

    private static WebAuthSessionStore.SessionData sessionFromTokens(
            Jwt jwt, String accessToken, String refreshToken, String idToken,
            Instant expiresAt, String csrf, WebAuthSessionStore.AuthTransaction tx) {
        Map<String, Object> profile = new LinkedHashMap<>();
        profile.put("id", jwt.getSubject());
        profile.put("email", Optional.ofNullable(jwt.getClaimAsString("email")).orElse(""));
        profile.put("displayName", Optional.ofNullable(jwt.getClaimAsString("name"))
                .orElse(Optional.ofNullable(jwt.getClaimAsString("preferred_username")).orElse("")));
        profile.put("roles", realmRoles(jwt));
        // Identity assurance (IAL) is deliberately separate from OIDC `acr` (AAL).
        // Missing legacy claims remain honest instead of being promoted to VERIFIED.
        profile.put("identityAssuranceLevel", identityAssurance(jwt));
        String acr = Optional.ofNullable(jwt.getClaimAsString("acr")).orElse("urn:impilo:aal1");
        Instant authTime = claimInstant(jwt, "auth_time");
        Instant stepUp = tx.previousSessionId() == null ? null : Instant.now();
        return new WebAuthSessionStore.SessionData(jwt.getSubject(), accessToken, refreshToken, idToken,
                expiresAt, csrf, acr, amr(jwt), authTime, stepUp, tx.state(), profile);
    }

    @SuppressWarnings("unchecked")
    private static List<String> realmRoles(Jwt jwt) {
        Object realm = jwt.getClaims().get("realm_access");
        if (!(realm instanceof Map<?, ?> map) || !(map.get("roles") instanceof List<?> roles)) return List.of();
        return roles.stream().map(String::valueOf)
                .filter(role -> !role.startsWith("default-roles-") && !role.equals("offline_access") && !role.equals("uma_authorization"))
                .toList();
    }

    private static List<String> amr(Jwt jwt) {
        Object value = jwt.getClaims().get("amr");
        if (value instanceof List<?> list) return list.stream().map(String::valueOf).toList();
        return value == null ? List.of() : List.of(String.valueOf(value));
    }

    private static String identityAssurance(Jwt jwt) {
        for (String claim : List.of("identity_assurance_level", "ial")) {
            String value = jwt.getClaimAsString(claim);
            if (value != null && !value.isBlank()) return value;
        }
        return "UNVERIFIED";
    }

    private static Instant claimInstant(Jwt jwt, String name) {
        Object value = jwt.getClaims().get(name);
        if (value instanceof Number n) return Instant.ofEpochSecond(n.longValue());
        return jwt.getIssuedAt();
    }

    private static void validateRequestedAcr(String requested, String actual) {
        if (requested == null) return;
        int requestedRank = aalRank(requested);
        int actualRank = aalRank(actual);
        if (actualRank < requestedRank) throw new OidcProtocolException("OIDC_AAL_NOT_SATISFIED");
    }

    private static int aalRank(String acr) {
        if (acr == null) return 0;
        if (acr.endsWith("aal3")) return 3;
        if (acr.endsWith("aal2")) return 2;
        if (acr.endsWith("aal1")) return 1;
        return 0;
    }

    private static String normalizeAcr(String value) {
        if (value == null || value.isBlank()) return null;
        if (!ALLOWED_ACR.contains(value)) throw new OidcProtocolException("INVALID_ACR");
        return value;
    }

    private static String normalizeAction(String value) {
        if (value == null || value.isBlank()) return null;
        if (!ALLOWED_ACTIONS.contains(value)) throw new OidcProtocolException("INVALID_REQUIRED_ACTION");
        return value;
    }

    private static String requiredText(JsonNode node, String field) {
        String value = node.path(field).asText("");
        if (value.isBlank()) throw new OidcProtocolException("OIDC_TOKEN_RESPONSE_INVALID");
        return value;
    }

    private static byte[] sha256(String value) {
        try { return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }

    private static String base64Url(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    private void requireEnabled() {
        if (!properties.isEnabled()) throw new OidcProtocolException("OIDC_WEB_SESSION_DISABLED");
        if (jwtDecoder == null) throw new OidcProtocolException("OIDC_JWT_DECODER_UNAVAILABLE");
    }

    public record EstablishedSession(String sessionId, WebAuthSessionStore.SessionData data, String returnTo) {}
    public static final class OidcProtocolException extends RuntimeException {
        private final String code;
        public OidcProtocolException(String code) { super(code); this.code = code; }
        public String code() { return code; }
    }
}
