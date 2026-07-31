package zw.gov.mohcc.impilo.experience.bootstrap;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.experience.auth.session.WebAuthSessionStore;
import zw.gov.mohcc.impilo.experience.client.KeycloakAdminClient;

@ExtendWith(MockitoExtension.class)
class BootstrapActivationVerifierTest {
    @Mock KeycloakAdminClient keycloak;

    @Test
    void rejectsClientClaimWithoutServerSession() {
        assertFalse(new BootstrapActivationVerifier(keycloak, 300)
                .verify("user-1", null).allowed());
    }

    @Test
    void rejectsAal2EvenWhenCredentialsExist() {
        assertFalse(new BootstrapActivationVerifier(keycloak, 300)
                .verify("user-1", session("urn:impilo:aal2", List.of("pwd", "otp"))).allowed());
    }

    @Test
    void rejectsAal3WithoutRecoveryCodes() {
        when(keycloak.credentials("user-1")).thenReturn(List.of(
                new KeycloakAdminClient.CredentialMetadata("key-1", "webauthn", "hardware", 1L)));
        assertFalse(new BootstrapActivationVerifier(keycloak, 300)
                .verify("user-1", session("urn:impilo:aal3", List.of("webauthn"))).allowed());
    }

    @Test
    void acceptsFreshMatchingAal3WithHardwareAndRecoveryCodes() {
        when(keycloak.credentials("user-1")).thenReturn(List.of(
                new KeycloakAdminClient.CredentialMetadata("key-1", "webauthn", "hardware", 1L),
                new KeycloakAdminClient.CredentialMetadata("recovery-1", "recovery-authn-codes", "", 2L)));
        assertTrue(new BootstrapActivationVerifier(keycloak, 300)
                .verify("user-1", session("urn:impilo:aal3", List.of("webauthn"))).allowed());
    }

    private static WebAuthSessionStore.SessionData session(String acr, List<String> amr) {
        Instant now = Instant.now();
        return new WebAuthSessionStore.SessionData("user-1", "access", "refresh", "id",
                now.plusSeconds(300), "csrf", acr, amr, now, now, "flow-1", Map.of());
    }
}
