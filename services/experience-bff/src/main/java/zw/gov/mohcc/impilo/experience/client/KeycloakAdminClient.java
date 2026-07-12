package zw.gov.mohcc.impilo.experience.client;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.*;

/**
 * Keycloak Admin API client for governed onboarding invitations and bootstrap activation.
 * Does not bypass policy — callers must precheck before invoking.
 */
@Component
public class KeycloakAdminClient {

    private static final Logger log = LoggerFactory.getLogger(KeycloakAdminClient.class);

    @Value("${KEYCLOAK_URL:http://localhost:8080}")
    private String keycloakUrl;
    @Value("${KEYCLOAK_REALM:impilo}")
    private String realm;
    @Value("${KEYCLOAK_BACKEND_CLIENT_ID:impilo-backend}")
    private String backendClientId;
    @Value("${KEYCLOAK_BACKEND_SECRET:impilo-backend-secret}")
    private String backendSecret;

    private final RestTemplate restTemplate;

    public KeycloakAdminClient(RestTemplate serviceRestTemplate) {
        this.restTemplate = serviceRestTemplate;
    }

    public boolean isReady() {
        return getServiceAccountToken() != null;
    }

    public KeycloakUserResult createUser(CreateUserCommand command) {
        String adminToken = getServiceAccountToken();
        if (adminToken == null) {
            return KeycloakUserResult.unavailable("Keycloak admin service account unavailable.");
        }
        try {
            String[] names = splitName(command.displayName(), command.email());
            Map<String, Object> userRep = new LinkedHashMap<>();
            userRep.put("username", command.email());
            userRep.put("email", command.email());
            userRep.put("firstName", names[0]);
            userRep.put("lastName", names[1]);
            userRep.put("enabled", true);
            userRep.put("emailVerified", command.emailVerified());
            if (command.password() != null && !command.password().isBlank()) {
                userRep.put("credentials", List.of(Map.of(
                        "type", "password",
                        "value", command.password(),
                        "temporary", command.temporaryPassword()
                )));
            }

            HttpHeaders adminHeaders = adminHeaders(adminToken);
            ResponseEntity<String> createResponse = restTemplate.exchange(
                    usersUrl(), HttpMethod.POST, new HttpEntity<>(userRep, adminHeaders), String.class);

            if (createResponse.getStatusCode() == HttpStatus.CONFLICT) {
                return KeycloakUserResult.failed("USER_EXISTS", "An account with this email already exists.");
            }
            if (!createResponse.getStatusCode().is2xxSuccessful()) {
                return KeycloakUserResult.failed("CREATE_FAILED", "Keycloak user creation failed.");
            }

            String userId = extractUserId(createResponse.getHeaders().getFirst(HttpHeaders.LOCATION));
            if (userId == null) {
                return KeycloakUserResult.failed("CREATE_FAILED", "Keycloak did not return a user id.");
            }

            for (String role : command.realmRoles()) {
                assignRealmRole(userId, role, adminHeaders);
            }

            if (command.sendUpdatePasswordAction()) {
                sendExecuteActionsEmail(userId, List.of("UPDATE_PASSWORD"), adminHeaders);
            }
            if (command.sendConfigureTotpAction()) {
                sendExecuteActionsEmail(userId, List.of("CONFIGURE_TOTP"), adminHeaders);
            }

            return KeycloakUserResult.created(userId);
        } catch (HttpClientErrorException.Conflict e) {
            return KeycloakUserResult.failed("USER_EXISTS", "An account with this email already exists.");
        } catch (Exception e) {
            log.warn("Keycloak user creation failed: {}", e.getMessage());
            return KeycloakUserResult.failed("CREATE_FAILED", e.getMessage());
        }
    }

    /**
     * Create a contact-anchored CITIZEN user for the R1 "Reachable" registration flow
     * (phone-OTP / email-OTP registration): the username is the verified contact value
     * (E.164 phone or email), contact provenance is stored as user attributes, and the
     * CITIZEN realm role is mandatory — the account is unusable without it, so on role
     * assignment failure the half-created user is deleted and the result is a failure
     * (mirrors AuthSessionController#register semantics).
     */
    public KeycloakUserResult createContactUser(ContactUserCommand command) {
        String adminToken = getServiceAccountToken();
        if (adminToken == null) {
            return KeycloakUserResult.unavailable("Keycloak admin service account unavailable.");
        }
        try {
            Map<String, Object> userRep = new LinkedHashMap<>();
            userRep.put("username", command.username());
            if (command.email() != null && !command.email().isBlank()) {
                userRep.put("email", command.email());
                userRep.put("emailVerified", command.emailVerified());
            }
            // Names are optional at R1 (the account anchor is the verified contact).
            if (command.firstName() != null && !command.firstName().isBlank()) {
                userRep.put("firstName", command.firstName());
            }
            if (command.lastName() != null && !command.lastName().isBlank()) {
                userRep.put("lastName", command.lastName());
            }
            userRep.put("enabled", true);
            if (command.attributes() != null && !command.attributes().isEmpty()) {
                userRep.put("attributes", command.attributes());
            }
            userRep.put("credentials", List.of(Map.of(
                    "type", "password",
                    "value", command.password(),
                    "temporary", false
            )));

            HttpHeaders adminHeaders = adminHeaders(adminToken);
            ResponseEntity<String> createResponse = restTemplate.exchange(
                    usersUrl(), HttpMethod.POST, new HttpEntity<>(userRep, adminHeaders), String.class);

            if (createResponse.getStatusCode() == HttpStatus.CONFLICT) {
                return KeycloakUserResult.failed("USER_EXISTS", "An account with this contact already exists.");
            }
            if (!createResponse.getStatusCode().is2xxSuccessful()) {
                return KeycloakUserResult.failed("CREATE_FAILED", "Keycloak user creation failed.");
            }
            String userId = extractUserId(createResponse.getHeaders().getFirst(HttpHeaders.LOCATION));
            if (userId == null) {
                return KeycloakUserResult.failed("CREATE_FAILED", "Keycloak did not return a user id.");
            }

            for (String role : command.realmRoles()) {
                if (!assignRealmRoleStrict(userId, role, adminHeaders)) {
                    log.error("Role {} assignment failed for contact user {} — rolling back", role, userId);
                    deleteUser(userId, adminHeaders);
                    return KeycloakUserResult.failed("ROLE_ASSIGNMENT_FAILED",
                            "Account could not be activated.");
                }
            }
            return KeycloakUserResult.created(userId);
        } catch (HttpClientErrorException.Conflict e) {
            return KeycloakUserResult.failed("USER_EXISTS", "An account with this contact already exists.");
        } catch (Exception e) {
            log.warn("Keycloak contact-user creation failed: {}", e.getMessage());
            return KeycloakUserResult.failed("CREATE_FAILED", e.getMessage());
        }
    }

    /** Delete a user (registration rollback). */
    public boolean deleteUser(String userId) {
        String adminToken = getServiceAccountToken();
        if (adminToken == null || userId == null || userId.isBlank()) {
            return false;
        }
        return deleteUser(userId, adminHeaders(adminToken));
    }

    /**
     * A realm-scoped service-account bearer token (impilo-backend), for authenticated
     * service-to-service calls made on behalf of a not-yet-authenticated registration
     * (e.g. recording the CONTACT_VERIFIED attestation in identity-assurance).
     *
     * @return the raw access token, or null when Keycloak is unreachable
     */
    public String serviceAccountBearer() {
        return getServiceAccountToken();
    }

    public boolean sendExecuteActionsEmail(String userId, List<String> actions) {
        String adminToken = getServiceAccountToken();
        if (adminToken == null || userId == null || userId.isBlank()) {
            return false;
        }
        return sendExecuteActionsEmail(userId, actions, adminHeaders(adminToken));
    }

    public boolean setUserEnabled(String userId, boolean enabled) {
        String adminToken = getServiceAccountToken();
        if (adminToken == null || userId == null || userId.isBlank()) {
            return false;
        }
        try {
            String url = usersUrl() + "/" + userId;
            Map<String, Object> body = Map.of("enabled", enabled);
            restTemplate.exchange(url, HttpMethod.PUT, new HttpEntity<>(body, adminHeaders(adminToken)), Void.class);
            return true;
        } catch (Exception e) {
            log.warn("Keycloak user enable/disable failed for {}: {}", userId, e.getMessage());
            return false;
        }
    }

    private boolean sendExecuteActionsEmail(String userId, List<String> actions, HttpHeaders adminHeaders) {
        try {
            String url = usersUrl() + "/" + userId + "/execute-actions-email";
            restTemplate.exchange(url, HttpMethod.PUT, new HttpEntity<>(actions, adminHeaders), Void.class);
            return true;
        } catch (Exception e) {
            log.warn("Keycloak execute-actions-email failed for {}: {}", userId, e.getMessage());
            return false;
        }
    }

    private void assignRealmRole(String userId, String roleName, HttpHeaders adminHeaders) {
        if (!assignRealmRoleStrict(userId, roleName, adminHeaders)) {
            log.warn("Keycloak role assignment failed for {} role {}", userId, roleName);
        }
    }

    private boolean assignRealmRoleStrict(String userId, String roleName, HttpHeaders adminHeaders) {
        try {
            String rolesUrl = keycloakUrl + "/admin/realms/" + realm + "/roles/" + roleName;
            ResponseEntity<JsonNode> roleResponse = restTemplate.exchange(
                    rolesUrl, HttpMethod.GET, new HttpEntity<>(adminHeaders), JsonNode.class);
            if (roleResponse.getStatusCode().is2xxSuccessful() && roleResponse.getBody() != null) {
                String assignUrl = usersUrl() + "/" + userId + "/role-mappings/realm";
                ResponseEntity<String> assignResponse = restTemplate.exchange(assignUrl, HttpMethod.POST,
                        new HttpEntity<>(List.of(roleResponse.getBody()), adminHeaders), String.class);
                return assignResponse.getStatusCode().is2xxSuccessful();
            }
            return false;
        } catch (Exception e) {
            log.warn("Keycloak role assignment failed for {} role {}: {}", userId, roleName, e.getMessage());
            return false;
        }
    }

    private boolean deleteUser(String userId, HttpHeaders adminHeaders) {
        try {
            restTemplate.exchange(usersUrl() + "/" + userId, HttpMethod.DELETE,
                    new HttpEntity<>(adminHeaders), Void.class);
            return true;
        } catch (Exception e) {
            log.warn("Keycloak user deletion failed for {}: {}", userId, e.getMessage());
            return false;
        }
    }

    private String getServiceAccountToken() {
        try {
            String tokenUrl = keycloakUrl + "/realms/" + realm + "/protocol/openid-connect/token";
            MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
            formData.add("grant_type", "client_credentials");
            formData.add("client_id", backendClientId);
            formData.add("client_secret", backendSecret);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            ResponseEntity<JsonNode> response = restTemplate.exchange(
                    tokenUrl, HttpMethod.POST, new HttpEntity<>(formData, headers), JsonNode.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return response.getBody().get("access_token").asText();
            }
        } catch (Exception e) {
            log.error("Failed to get Keycloak service account token: {}", e.getMessage());
        }
        return null;
    }

    private HttpHeaders adminHeaders(String adminToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(adminToken);
        return headers;
    }

    private String usersUrl() {
        return keycloakUrl + "/admin/realms/" + realm + "/users";
    }

    private static String extractUserId(String locationHeader) {
        if (locationHeader == null || locationHeader.isBlank()) return null;
        return locationHeader.substring(locationHeader.lastIndexOf('/') + 1);
    }

    private static String[] splitName(String displayName, String email) {
        if (displayName != null && !displayName.isBlank()) {
            String[] parts = displayName.trim().split("\\s+", 2);
            return new String[]{parts[0], parts.length > 1 ? parts[1] : parts[0]};
        }
        String local = email != null && email.contains("@") ? email.substring(0, email.indexOf('@')) : "User";
        return new String[]{local, local};
    }

    public record CreateUserCommand(
            String email,
            String displayName,
            String password,
            boolean temporaryPassword,
            boolean emailVerified,
            List<String> realmRoles,
            boolean sendUpdatePasswordAction,
            boolean sendConfigureTotpAction
    ) {}

    /**
     * @param username      the verified contact value (E.164 phone or lowercase email)
     * @param email         set only for EMAIL-channel registrations
     * @param attributes    contact provenance (e.g. phoneNumber, contactChannel, contactVerified)
     * @param emailVerified true when the contact channel IS the email and OTP-verified
     */
    public record ContactUserCommand(
            String username,
            String email,
            String firstName,
            String lastName,
            String password,
            Map<String, List<String>> attributes,
            List<String> realmRoles,
            boolean emailVerified
    ) {}

    public record KeycloakUserResult(
            boolean available,
            boolean created,
            String userId,
            String code,
            String message
    ) {
        public static KeycloakUserResult unavailable(String message) {
            return new KeycloakUserResult(false, false, null, "AUTH_SERVICE_UNAVAILABLE", message);
        }

        public static KeycloakUserResult failed(String code, String message) {
            return new KeycloakUserResult(true, false, null, code, message);
        }

        public static KeycloakUserResult created(String userId) {
            return new KeycloakUserResult(true, true, userId, "CREATED", "Keycloak user created.");
        }
    }
}
