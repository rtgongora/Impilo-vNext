package zw.gov.mohcc.impilo.experience.admingovernance;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import zw.gov.mohcc.impilo.experience.client.KeycloakAdminClient;
import zw.gov.mohcc.impilo.experience.client.NotificationServiceClient;

import java.time.OffsetDateTime;
import java.util.*;

/**
 * Staged onboarding invitations — Keycloak account creation plus optional notification delivery.
 * Never reports success when downstream systems did not acknowledge delivery.
 */
@Service
public class GovernanceInvitationService {

    private final KeycloakAdminClient keycloakAdminClient;
    private final NotificationServiceClient notificationServiceClient;

    @Value("${impilo.governance.invitation-expiry-hours:168}")
    private int invitationExpiryHours;

    @Value("${impilo.governance.activation-base-url:https://impilo.mohcc.gov.zw}")
    private String activationBaseUrl;

    public GovernanceInvitationService(KeycloakAdminClient keycloakAdminClient,
                                       NotificationServiceClient notificationServiceClient) {
        this.keycloakAdminClient = keycloakAdminClient;
        this.notificationServiceClient = notificationServiceClient;
    }

    public InvitationDeliveryResult deliverBootstrapActivation(
            String email,
            String displayName,
            boolean mfaRequired) {
        if (!keycloakAdminClient.isReady()) {
            return InvitationDeliveryResult.blocked(
                    null,
                    "pending_backend",
                    "Keycloak is unavailable. Bootstrap activation was not completed.");
        }

        KeycloakAdminClient.KeycloakUserResult keycloak = keycloakAdminClient.createUser(
                new KeycloakAdminClient.CreateUserCommand(
                        email,
                        displayName,
                        null,
                        true,
                        true,
                        List.of(),
                        false,
                        false
                ));

        if (!keycloak.created()) {
            return InvitationDeliveryResult.blocked(
                    null,
                    "failed_non_blocking",
                    keycloak.message() != null ? keycloak.message() : "Bootstrap Keycloak activation failed.");
        }

        String invitationId = UUID.randomUUID().toString();
        String expiresAt = OffsetDateTime.now().plusHours(invitationExpiryHours).toString();
        List<String> requiredActions = mfaRequired
                ? List.of("UPDATE_PASSWORD", "webauthn-register", "CONFIGURE_RECOVERY_AUTHN_CODES")
                : List.of("UPDATE_PASSWORD");
        if (!keycloakAdminClient.sendExecuteActionsEmail(keycloak.userId(), requiredActions,
                Math.toIntExact(Math.multiplyExact(invitationExpiryHours, 3600L)))) {
            keycloakAdminClient.setUserEnabled(keycloak.userId(), false);
            return InvitationDeliveryResult.blocked(
                    invitationId, "failed_non_blocking",
                    "The secure Keycloak setup action could not be delivered; the staged account was disabled.");
        }
        NotificationAttempt notification = sendOnboardingNotification(
                invitationId,
                email,
                displayName,
                "bootstrap_first_admin",
                Map.of(
                        "invitationType", "bootstrap_first_admin",
                        "expiresAt", expiresAt,
                        "mfaRequired", mfaRequired
                ));

        return InvitationDeliveryResult.sent(
                invitationId,
                keycloak.userId(),
                expiresAt,
                notification.auditStatus(),
                notification.friendlyMessage());
    }

    public boolean activateBootstrapRoles(String keycloakUserId) {
        return keycloakAdminClient.assignRealmRoles(keycloakUserId, List.of("SYSTEM_ADMIN"));
    }

    public InvitationDeliveryResult deliverOrganisationInvitation(
            String organisationId,
            String email,
            String displayName,
            String roleTemplateId,
            String invitationType) {
        if (email == null || email.isBlank()) {
            return InvitationDeliveryResult.blocked(null, "failed_non_blocking", "Official email is required for invitations.");
        }
        if (!keycloakAdminClient.isReady()) {
            return InvitationDeliveryResult.queued(
                    UUID.randomUUID().toString(),
                    null,
                    "pending_backend",
                    "Representative record staged. Keycloak invitation pending — reconciliation required.");
        }

        KeycloakAdminClient.KeycloakUserResult keycloak = keycloakAdminClient.createUser(
                new KeycloakAdminClient.CreateUserCommand(
                        email,
                        displayName,
                        null,
                        true,
                        false,
                        List.of(),
                        true,
                        false
                ));

        if (!keycloak.created() && !"USER_EXISTS".equals(keycloak.code())) {
            return InvitationDeliveryResult.blocked(
                    null,
                    "failed_non_blocking",
                    keycloak.message() != null ? keycloak.message() : "Invitation could not be created in Keycloak.");
        }

        String userId = keycloak.userId();
        String invitationId = UUID.randomUUID().toString();
        String expiresAt = OffsetDateTime.now().plusHours(invitationExpiryHours).toString();
        NotificationAttempt notification = sendOnboardingNotification(
                invitationId,
                email,
                displayName,
                invitationType,
                Map.of(
                        "organisationId", organisationId,
                        "roleTemplateId", roleTemplateId,
                        "invitationType", invitationType,
                        "expiresAt", expiresAt
                ));

        return InvitationDeliveryResult.sent(invitationId, userId, expiresAt, notification.auditStatus(), notification.friendlyMessage());
    }

    public InvitationRevocationResult revokeInvitation(String keycloakUserId) {
        if (keycloakUserId == null || keycloakUserId.isBlank()) {
            return new InvitationRevocationResult(false, "pending_backend", "No Keycloak user is linked to this invitation.");
        }
        if (!keycloakAdminClient.isReady()) {
            return new InvitationRevocationResult(false, "pending_backend", "Keycloak unavailable — invitation revoke not confirmed.");
        }
        boolean disabled = keycloakAdminClient.setUserEnabled(keycloakUserId, false);
        if (!disabled) {
            return new InvitationRevocationResult(false, "failed_non_blocking", "Keycloak did not confirm invitation revocation.");
        }
        return new InvitationRevocationResult(true, "ingested", "Invitation revoked in Keycloak. Account remains organisation-scoped and inactive.");
    }

    public int invitationExpiryHours() {
        return invitationExpiryHours;
    }

    public InvitationDeliveryResult deliverImportRowInvitation(
            String organisationId,
            String importBatchId,
            String email,
            String displayName,
            String importType) {
        return deliverOrganisationInvitation(
                organisationId,
                email,
                displayName,
                "organisation_data_uploader",
                "import_" + importType + ":" + importBatchId);
    }

    private NotificationAttempt sendOnboardingNotification(
            String invitationId,
            String email,
            String displayName,
            String invitationType,
            Map<String, Object> metadata) {
        // notification-service NotifyRequest contract: templateKey + channel + "to" + string variables.
        // Template GOVERNANCE_ONBOARDING_INVITATION is seeded by notification-service V012; the
        // worker refuses unregistered keys, so the key must stay stable — the invitation type is a variable.
        Map<String, String> variables = new LinkedHashMap<>();
        variables.put("displayName", displayName != null ? displayName : "");
        variables.put("invitationType", invitationType != null ? invitationType : "");
        variables.put("invitationId", invitationId);
        variables.put("activationUrl", activationBaseUrl);
        metadata.forEach((key, value) -> variables.putIfAbsent(key, value != null ? String.valueOf(value) : ""));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("templateKey", "GOVERNANCE_ONBOARDING_INVITATION");
        body.put("channel", "EMAIL");
        body.put("to", email);
        body.put("variables", variables);

        try {
            JsonNode response = notificationServiceClient.sendNotification(body);
            if (response == null || response.isNull()) {
                return new NotificationAttempt(
                        "pending_backend",
                        "Account staged in Keycloak. Email notification queued — reconciliation required.");
            }
            return new NotificationAttempt("ingested", "Invitation notification accepted by notification-service.");
        } catch (Exception e) {
            return new NotificationAttempt(
                    "pending_backend",
                    "Account staged in Keycloak. Notification-service unavailable — reconciliation required.");
        }
    }

    public record InvitationDeliveryResult(
            String invitationId,
            String keycloakUserId,
            String expiresAt,
            String status,
            String auditStatus,
            String friendlyMessage
    ) {
        public boolean activated() {
            return "invitation_sent".equals(status) || "completed".equals(status);
        }

        public static InvitationDeliveryResult sent(
                String invitationId,
                String keycloakUserId,
                String expiresAt,
                String notificationAuditStatus,
                String message) {
            String audit = "ingested".equals(notificationAuditStatus) ? "ingested" : "pending_backend";
            return new InvitationDeliveryResult(
                    invitationId,
                    keycloakUserId,
                    expiresAt,
                    "invitation_sent",
                    audit,
                    message);
        }

        public static InvitationDeliveryResult queued(
                String invitationId,
                String keycloakUserId,
                String auditStatus,
                String message) {
            return new InvitationDeliveryResult(invitationId, keycloakUserId, null, "invitations_pending", auditStatus, message);
        }

        public static InvitationDeliveryResult blocked(String invitationId, String auditStatus, String message) {
            return new InvitationDeliveryResult(invitationId, null, null, "blocked", auditStatus, message);
        }
    }

    public record InvitationRevocationResult(
            boolean revoked,
            String auditStatus,
            String friendlyMessage
    ) {}

    private record NotificationAttempt(String auditStatus, String friendlyMessage) {}
}
