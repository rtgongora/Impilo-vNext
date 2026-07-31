package zw.gov.mohcc.impilo.experience.admingovernance;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import zw.gov.mohcc.impilo.experience.client.KeycloakAdminClient;
import zw.gov.mohcc.impilo.experience.client.NotificationServiceClient;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GovernanceInvitationServiceTest {

    @Mock
    private KeycloakAdminClient keycloakAdminClient;
    @Mock
    private NotificationServiceClient notificationServiceClient;

    private GovernanceInvitationService service;

    @BeforeEach
    void setUp() {
        service = new GovernanceInvitationService(keycloakAdminClient, notificationServiceClient);
        ReflectionTestUtils.setField(service, "invitationExpiryHours", 168);
        ReflectionTestUtils.setField(service, "activationBaseUrl", "https://impilo.mohcc.gov.zw");
    }

    @Test
    void bootstrapActivationBlockedWhenKeycloakUnavailable() {
        when(keycloakAdminClient.isReady()).thenReturn(false);

        var result = service.deliverBootstrapActivation("admin@mohcc.gov.zw", "Admin User", true);

        assertFalse(result.activated());
        assertEquals("blocked", result.status());
        assertNull(result.keycloakUserId());
        verify(keycloakAdminClient, never()).createUser(any());
    }

    @Test
    void bootstrapActivationDoesNotReportSuccessWhenKeycloakCreateFails() {
        when(keycloakAdminClient.isReady()).thenReturn(true);
        when(keycloakAdminClient.createUser(any())).thenReturn(
                KeycloakAdminClient.KeycloakUserResult.failed("CREATE_FAILED", "Keycloak user creation failed."));

        var result = service.deliverBootstrapActivation("admin@mohcc.gov.zw", "Admin User", true);

        assertFalse(result.activated());
        assertEquals("blocked", result.status());
        verify(notificationServiceClient, never()).sendNotification(any());
    }

    @Test
    void bootstrapActivationReportsSentOnlyAfterKeycloakCreatesUser() {
        when(keycloakAdminClient.isReady()).thenReturn(true);
        when(keycloakAdminClient.createUser(any())).thenAnswer(invocation -> {
            KeycloakAdminClient.CreateUserCommand command = invocation.getArgument(0);
            assertNull(command.password(), "Impilo must never receive or set the bootstrap password");
            assertTrue(command.realmRoles().isEmpty(), "Administrator authority is assigned only after AAL3 verification");
            return KeycloakAdminClient.KeycloakUserResult.created("kc-user-1");
        });
        when(keycloakAdminClient.sendExecuteActionsEmail(eq("kc-user-1"),
                eq(List.of("UPDATE_PASSWORD", "webauthn-register", "CONFIGURE_RECOVERY_AUTHN_CODES")),
                eq(604800))).thenReturn(true);
        when(notificationServiceClient.sendNotification(any())).thenReturn(mock(JsonNode.class));

        var result = service.deliverBootstrapActivation("admin@mohcc.gov.zw", "Admin User", true);

        assertTrue(result.activated());
        assertEquals("invitation_sent", result.status());
        assertEquals("kc-user-1", result.keycloakUserId());
        assertNotNull(result.invitationId());
    }

    @Test
    void bootstrapActivationDisablesAccountWhenNativeActionCannotBeDelivered() {
        when(keycloakAdminClient.isReady()).thenReturn(true);
        when(keycloakAdminClient.createUser(any())).thenReturn(
                KeycloakAdminClient.KeycloakUserResult.created("kc-user-1"));
        when(keycloakAdminClient.sendExecuteActionsEmail(eq("kc-user-1"), anyList(), anyInt()))
                .thenReturn(false);
        when(keycloakAdminClient.setUserEnabled("kc-user-1", false)).thenReturn(true);

        var result = service.deliverBootstrapActivation("admin@mohcc.gov.zw", "Admin User", true);

        assertFalse(result.activated());
        verify(keycloakAdminClient).setUserEnabled("kc-user-1", false);
        verifyNoInteractions(notificationServiceClient);
    }

    @Test
    void organisationInvitationDoesNotAssignElevatedRealmRoles() {
        when(keycloakAdminClient.isReady()).thenReturn(true);
        when(keycloakAdminClient.createUser(any())).thenAnswer(invocation -> {
            KeycloakAdminClient.CreateUserCommand command = invocation.getArgument(0);
            assertTrue(command.realmRoles().isEmpty(), "Organisation invitations must not assign hidden superuser realm roles");
            return KeycloakAdminClient.KeycloakUserResult.created("kc-rep-1");
        });
        when(notificationServiceClient.sendNotification(any())).thenReturn(null);

        var result = service.deliverOrganisationInvitation(
                "org-1",
                "rep@org.example",
                "Org Rep",
                "organisation_authorised_representative",
                "authorised_representative");

        assertTrue(result.activated());
        assertEquals("pending_backend", result.auditStatus());
    }

    @Test
    void organisationInvitationBlockedWithoutEmail() {
        var result = service.deliverOrganisationInvitation("org-1", "  ", "Org Rep", "organisation_authorised_representative", "authorised_representative");

        assertFalse(result.activated());
        verifyNoInteractions(keycloakAdminClient);
    }

    @Test
    void importRowInvitationDelegatesToOrganisationFlow() {
        when(keycloakAdminClient.isReady()).thenReturn(true);
        when(keycloakAdminClient.createUser(any())).thenReturn(KeycloakAdminClient.KeycloakUserResult.created("kc-import-1"));
        when(notificationServiceClient.sendNotification(any())).thenReturn(mock(JsonNode.class));

        var result = service.deliverImportRowInvitation(
                "org-1",
                "batch-1",
                "uploader@org.example",
                "Uploader",
                "organisation_users");

        assertTrue(result.activated());
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(notificationServiceClient).sendNotification(captor.capture());
        Map<String, Object> body = captor.getValue();
        // notification-service NotifyRequest contract: registered template key, uppercase channel,
        // "to" recipient, string variables — the worker rejects unregistered keys.
        assertEquals("GOVERNANCE_ONBOARDING_INVITATION", body.get("templateKey"));
        assertEquals("EMAIL", body.get("channel"));
        assertEquals("uploader@org.example", body.get("to"));
        @SuppressWarnings("unchecked")
        Map<String, String> variables = (Map<String, String>) body.get("variables");
        assertEquals("import_organisation_users:batch-1", variables.get("invitationType"));
        assertEquals("https://impilo.mohcc.gov.zw", variables.get("activationUrl"));
        assertNotNull(variables.get("expiresAt"));
    }
}
