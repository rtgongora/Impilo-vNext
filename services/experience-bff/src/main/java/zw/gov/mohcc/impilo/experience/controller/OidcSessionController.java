package zw.gov.mohcc.impilo.experience.controller;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.experience.auth.session.OidcSessionService;
import zw.gov.mohcc.impilo.experience.auth.session.SessionBearerTokenResolver;
import zw.gov.mohcc.impilo.experience.auth.session.WebAuthSessionProperties;
import zw.gov.mohcc.impilo.experience.auth.session.WebAuthSessionStore;

@RestController
@RequestMapping("/internal/v1/auth/oidc")
public class OidcSessionController {
    private final OidcSessionService sessions;
    private final WebAuthSessionProperties properties;

    public OidcSessionController(OidcSessionService sessions, WebAuthSessionProperties properties) {
        this.sessions = sessions;
        this.properties = properties;
    }

    @GetMapping("/authorize")
    public ResponseEntity<Void> authorize(
            @RequestParam(defaultValue = "/") String returnTo,
            @RequestParam(required = false) String acr,
            @RequestParam(required = false) String loginHint) {
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(sessions.begin(returnTo, acr, null, null, loginHint)).build();
    }

    @GetMapping("/callback")
    public ResponseEntity<Void> callback(@RequestParam String state, @RequestParam String code) {
        OidcSessionService.EstablishedSession established = sessions.complete(state, code);
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(established.returnTo()))
                .header(HttpHeaders.SET_COOKIE, sessionCookie(established.sessionId()).toString())
                .header(HttpHeaders.SET_COOKIE, csrfCookie(established.data().csrfToken()).toString())
                .build();
    }

    @GetMapping("/session")
    public ResponseEntity<Map<String, Object>> session(HttpServletRequest request) {
        String sessionId = SessionBearerTokenResolver.cookie(request, WebAuthSessionStore.SESSION_COOKIE);
        return sessions.session(sessionId).<ResponseEntity<Map<String, Object>>>map(session -> {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("authenticated", true);
            data.put("user", session.profile());
            data.put("acr", session.acr());
            data.put("amr", session.amr());
            data.put("authTime", session.authTime());
            data.put("stepUpTime", session.stepUpTime());
            data.put("expiresAt", session.accessTokenExpiresAt());
            return ResponseEntity.ok(Map.of("data", data));
        }).orElseGet(() -> ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("error", Map.of("code", "NO_ACTIVE_SESSION"))));
    }

    @PostMapping("/step-up")
    public ResponseEntity<Map<String, Object>> stepUp(HttpServletRequest request,
            @AuthenticationPrincipal Jwt jwt, @RequestBody Map<String, String> body) {
        String sessionId = SessionBearerTokenResolver.cookie(request, WebAuthSessionStore.SESSION_COOKIE);
        URI location = sessions.begin(body.get("returnTo"), body.get("requiredAcr"), null,
                sessionId, jwt == null ? null : jwt.getClaimAsString("preferred_username"));
        return ResponseEntity.ok(Map.of("data", Map.of("authorizeUrl", location.toString())));
    }

    @PostMapping("/action")
    public ResponseEntity<Map<String, Object>> action(HttpServletRequest request,
            @AuthenticationPrincipal Jwt jwt, @RequestBody Map<String, String> body) {
        String sessionId = SessionBearerTokenResolver.cookie(request, WebAuthSessionStore.SESSION_COOKIE);
        URI location = sessions.begin(body.get("returnTo"), "urn:impilo:aal2", body.get("action"),
                sessionId, jwt == null ? null : jwt.getClaimAsString("preferred_username"));
        return ResponseEntity.ok(Map.of("data", Map.of("authorizeUrl", location.toString())));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        String sessionId = SessionBearerTokenResolver.cookie(request, WebAuthSessionStore.SESSION_COOKIE);
        sessions.logout(sessionId);
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, clearCookie(WebAuthSessionStore.SESSION_COOKIE, true).toString())
                .header(HttpHeaders.SET_COOKIE, clearCookie(WebAuthSessionStore.CSRF_COOKIE, false).toString())
                .build();
    }

    private ResponseCookie sessionCookie(String value) {
        return ResponseCookie.from(WebAuthSessionStore.SESSION_COOKIE, value).httpOnly(true)
                .secure(properties.isCookieSecure()).sameSite("Lax").path("/")
                .maxAge(Duration.ofSeconds(properties.getSessionTtlSeconds())).build();
    }

    private ResponseCookie csrfCookie(String value) {
        return ResponseCookie.from(WebAuthSessionStore.CSRF_COOKIE, value).httpOnly(false)
                .secure(properties.isCookieSecure()).sameSite("Lax").path("/")
                .maxAge(Duration.ofSeconds(properties.getSessionTtlSeconds())).build();
    }

    private ResponseCookie clearCookie(String name, boolean httpOnly) {
        return ResponseCookie.from(name, "").httpOnly(httpOnly).secure(properties.isCookieSecure())
                .sameSite("Lax").path("/").maxAge(Duration.ZERO).build();
    }
}
