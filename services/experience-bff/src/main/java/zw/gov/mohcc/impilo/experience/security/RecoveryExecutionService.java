package zw.gov.mohcc.impilo.experience.security;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;
import org.springframework.stereotype.Service;
import zw.gov.mohcc.impilo.experience.client.KeycloakAdminClient;
import zw.gov.mohcc.impilo.experience.client.NotificationServiceClient;
import zw.gov.mohcc.impilo.experience.client.TshepoIdentityServiceClient;

@Service
public class RecoveryExecutionService {
    private final KeycloakAdminClient keycloak;
    private final TshepoIdentityServiceClient identity;
    private final NotificationServiceClient notifications;
    public RecoveryExecutionService(KeycloakAdminClient keycloak, TshepoIdentityServiceClient identity,
            NotificationServiceClient notifications){this.keycloak=keycloak;this.identity=identity;this.notifications=notifications;}

    public JsonNode execute(String tenantId, UUID caseId, JsonNode recoveryCase) {
        if (recoveryCase == null || !"APPROVED".equals(recoveryCase.path("status").asText())) return recoveryCase;
        String userId=recoveryCase.path("targetUserId").asText();
        Set<String> existing=new HashSet<>();
        keycloak.credentials(userId).forEach(c->existing.add(c.id()));
        boolean credentialsOk=true;
        for(JsonNode id:recoveryCase.path("selectedCredentialIds")){
            String credentialId=id.asText();
            if(existing.contains(credentialId)) credentialsOk &= keycloak.removeCredential(userId,credentialId);
        }
        boolean sessionsOk=keycloak.terminateAllSessions(userId);
        boolean actionsOk=keycloak.sendExecuteActionsEmail(userId,
                List.of("webauthn-register","CONFIGURE_RECOVERY_AUTHN_CODES"),1800);
        boolean success=credentialsOk&&sessionsOk&&actionsOk;
        String reference="recovery-execution-"+UUID.randomUUID();
        JsonNode recorded=identity.recordRecoveryExecution(tenantId,caseId,Map.of(
                "succeeded",success,"executionReference",reference,
                "error",success ? "" : "One or more Keycloak recovery operations failed"));
        if(success) notifyUser(userId,caseId);
        return recorded;
    }

    private void notifyUser(String userId, UUID caseId){
        KeycloakAdminClient.UserMetadata user=keycloak.user(userId);
        if(user==null||user.email()==null||user.email().isBlank()) return;
        try{notifications.sendNotification(Map.of("templateKey","SECURITY_RECOVERY_COMPLETED","channel","EMAIL",
                "to",user.email(),"variables",Map.of("caseReference",caseId.toString())));}catch(Exception ignored){ }
    }
}
