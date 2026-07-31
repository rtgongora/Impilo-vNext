package zw.gov.mohcc.impilo.experience.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.server.ResponseStatusException;
import zw.gov.mohcc.impilo.experience.client.KeycloakAdminClient;

class SecuritySettingsServiceTest {
    private final KeycloakAdminClient keycloak = mock(KeycloakAdminClient.class);
    private final SecuritySettingsService service = new SecuritySettingsService(keycloak);

    @Test
    void workforceCannotRemoveItsLastMfaCredential() {
        Jwt jwt = jwt("urn:impilo:aal2", List.of("CLINICIAN"));
        when(keycloak.credentials("user-1")).thenReturn(List.of(
                new KeycloakAdminClient.CredentialMetadata("otp-1", "otp", "Authenticator", 1L)));

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.removeCredential(jwt, "otp-1"));

        assertEquals("WORKFORCE_MFA_REQUIRED", error.getReason());
    }

    @Test
    void aalOneCannotPerformDestructiveSecurityAction() {
        Jwt jwt = jwt("urn:impilo:aal1", List.of("CITIZEN"));

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.terminateOtherSessions(jwt));

        assertEquals("STEP_UP_AAL2_REQUIRED", error.getReason());
        verifyNoInteractions(keycloak);
    }

    @Test
    void snapshotClassifiesPrivilegedPolicyWithoutExposingSecrets() {
        Jwt jwt = jwt("urn:impilo:aal3", List.of("SYSTEM_ADMIN"));
        when(keycloak.credentials("user-1")).thenReturn(List.of(
                new KeycloakAdminClient.CredentialMetadata("password-id", "password", null, 1L),
                new KeycloakAdminClient.CredentialMetadata("key-1", "webauthn", "Security key", 2L)));
        when(keycloak.sessions("user-1")).thenReturn(List.of());

        SecuritySettingsService.SecuritySnapshot snapshot = service.snapshot(jwt);

        assertEquals("urn:impilo:aal3", snapshot.requiredAal());
        assertEquals(1, snapshot.methods().size());
        assertEquals("passkey", snapshot.methods().getFirst().method());
    }

    private static Jwt jwt(String acr, List<String> roles) {
        Instant now = Instant.now();
        return Jwt.withTokenValue("token").header("alg", "none").subject("user-1")
                .issuedAt(now).expiresAt(now.plusSeconds(600))
                .claim("acr", acr).claim("amr", List.of("pwd", "otp"))
                .claim("auth_time", now.getEpochSecond()).claim("sid", "session-current")
                .claim("realm_access", Map.of("roles", roles)).build();
    }
}
