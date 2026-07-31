package zw.gov.mohcc.impilo.experience.controller;

import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.experience.security.SecuritySettingsService;

@RestController
@RequestMapping("/internal/v1/settings/security")
public class SecuritySettingsController {
    private final SecuritySettingsService security;

    public SecuritySettingsController(SecuritySettingsService security) { this.security = security; }

    @GetMapping
    public Map<String, Object> get(@AuthenticationPrincipal Jwt jwt) {
        return Map.of("data", security.snapshot(jwt));
    }

    @DeleteMapping("/credentials/{credentialId}")
    public ResponseEntity<Void> removeCredential(@AuthenticationPrincipal Jwt jwt,
                                                  @PathVariable String credentialId) {
        security.removeCredential(jwt, credentialId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/sessions/{sessionId}")
    public ResponseEntity<Void> terminateSession(@AuthenticationPrincipal Jwt jwt,
                                                  @PathVariable String sessionId) {
        security.terminateSession(jwt, sessionId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/sessions/logout-others")
    public Map<String, Object> terminateOtherSessions(@AuthenticationPrincipal Jwt jwt) {
        return Map.of("data", Map.of("revoked", security.terminateOtherSessions(jwt)));
    }
}
