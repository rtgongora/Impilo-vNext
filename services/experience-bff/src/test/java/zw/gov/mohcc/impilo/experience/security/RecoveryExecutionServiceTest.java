package zw.gov.mohcc.impilo.experience.security;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import zw.gov.mohcc.impilo.experience.client.KeycloakAdminClient;
import zw.gov.mohcc.impilo.experience.client.NotificationServiceClient;
import zw.gov.mohcc.impilo.experience.client.TshepoIdentityServiceClient;

class RecoveryExecutionServiceTest {
    private final KeycloakAdminClient keycloak = mock(KeycloakAdminClient.class);
    private final TshepoIdentityServiceClient identity = mock(TshepoIdentityServiceClient.class);
    private final NotificationServiceClient notifications = mock(NotificationServiceClient.class);
    private final RecoveryExecutionService service = new RecoveryExecutionService(keycloak, identity, notifications);
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void approvedCaseRevokesSelectedCredentialTerminatesSessionsAndRequiresReenrolment() throws Exception {
        UUID caseId = UUID.randomUUID();
        JsonNode recoveryCase = json.readTree("""
                {"status":"APPROVED","targetUserId":"user-1","selectedCredentialIds":["otp-1","already-gone"]}
                """);
        JsonNode recorded = json.createObjectNode().put("status", "EXECUTED");
        when(keycloak.credentials("user-1")).thenReturn(List.of(
                new KeycloakAdminClient.CredentialMetadata("otp-1", "otp", "Authenticator", 1L)));
        when(keycloak.removeCredential("user-1", "otp-1")).thenReturn(true);
        when(keycloak.terminateAllSessions("user-1")).thenReturn(true);
        when(keycloak.sendExecuteActionsEmail(eq("user-1"), any(), eq(1800))).thenReturn(true);
        when(keycloak.user("user-1")).thenReturn(
                new KeycloakAdminClient.UserMetadata("user-1", "person@example.test", "person", true));
        when(identity.recordRecoveryExecution(eq("tenant-1"), eq(caseId), any())).thenReturn(recorded);

        assertSame(recorded, service.execute("tenant-1", caseId, recoveryCase));

        verify(keycloak).removeCredential("user-1", "otp-1");
        verify(keycloak, never()).removeCredential("user-1", "already-gone");
        verify(keycloak).terminateAllSessions("user-1");
        verify(keycloak).sendExecuteActionsEmail("user-1",
                List.of("webauthn-register", "CONFIGURE_RECOVERY_AUTHN_CODES"), 1800);
        verify(identity).recordRecoveryExecution(eq("tenant-1"), eq(caseId),
                argThat(body -> Boolean.TRUE.equals(body.get("succeeded"))));
        verify(notifications).sendNotification(argThat(body ->
                "SECURITY_RECOVERY_COMPLETED".equals(body.get("templateKey"))));
    }

    @Test
    void unapprovedCaseCannotExecute() throws Exception {
        JsonNode recoveryCase = json.readTree("{\"status\":\"REQUESTED\"}");

        assertSame(recoveryCase, service.execute("tenant-1", UUID.randomUUID(), recoveryCase));

        verifyNoInteractions(keycloak, identity, notifications);
    }
}
