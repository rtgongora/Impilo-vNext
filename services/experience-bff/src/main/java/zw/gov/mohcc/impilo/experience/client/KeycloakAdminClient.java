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
    @Value("${KEYCLOAK_USER_ADMIN_CLIENT_ID:impilo-user-admin}")
    private String userAdminClientId;
    @Value("${KEYCLOAK_USER_ADMIN_SECRET:}")
    private String userAdminSecret;

    private final RestTemplate restTemplate;

    /**
     * Uses the NON-intercepted {@code idpRestTemplate}. Keycloak is not a sovereign Impilo
     * service: the ~30 trust headers the forwarding interceptor attaches mean nothing to it, and
     * this client already authenticates every call with an admin bearer it fetched itself. Riding
     * the intercepted template also put the caller's user token on the token endpoint used to mint
     * that very bearer.
     */
    public KeycloakAdminClient(
            @org.springframework.beans.factory.annotation.Qualifier("idpRestTemplate")
            RestTemplate idpRestTemplate) {
        this.restTemplate = idpRestTemplate;
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

    public boolean sendExecuteActionsEmail(String userId, List<String> actions, int lifespanSeconds) {
        String adminToken = getServiceAccountToken();
        if (adminToken == null || userId == null || userId.isBlank() || lifespanSeconds <= 0) {
            return false;
        }
        return sendExecuteActionsEmail(userId, actions, lifespanSeconds, adminHeaders(adminToken));
    }

    /** Assign governed realm roles only after the caller has completed its activation checks. */
    public boolean assignRealmRoles(String userId, List<String> roles) {
        String adminToken = getServiceAccountToken();
        if (adminToken == null || !safeId(userId) || roles == null || roles.isEmpty()) return false;
        HttpHeaders headers = adminHeaders(adminToken);
        return roles.stream().allMatch(role -> assignRealmRoleStrict(userId, role, headers));
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

    /** Sanitized credential metadata only. Keycloak never returns factor secrets here. */
    public List<CredentialMetadata> credentials(String userId) {
        String token = getServiceAccountToken();
        if (token == null || userId == null || userId.isBlank()) return List.of();
        try {
            ResponseEntity<JsonNode> response = restTemplate.exchange(
                    usersUrl() + "/" + userId + "/credentials", HttpMethod.GET,
                    new HttpEntity<>(adminHeaders(token)), JsonNode.class);
            if (response.getBody() == null || !response.getBody().isArray()) return List.of();
            List<CredentialMetadata> result = new ArrayList<>();
            for (JsonNode item : response.getBody()) {
                result.add(new CredentialMetadata(
                        item.path("id").asText(), item.path("type").asText(),
                        item.path("userLabel").asText(null),
                        item.hasNonNull("createdDate") ? item.path("createdDate").asLong() : null));
            }
            return List.copyOf(result);
        } catch (Exception e) {
            log.warn("Keycloak credential metadata read failed for {}: {}", userId, e.getMessage());
            return List.of();
        }
    }

    public UserMetadata user(String userId) {
        if (!safeId(userId)) return null;
        String token = getServiceAccountToken();
        if (token == null) return null;
        try {
            ResponseEntity<JsonNode> response = restTemplate.exchange(usersUrl() + "/" + userId,
                    HttpMethod.GET, new HttpEntity<>(adminHeaders(token)), JsonNode.class);
            JsonNode body = response.getBody();
            return body == null ? null : new UserMetadata(body.path("id").asText(),
                    body.path("email").asText(""), body.path("username").asText(""),
                    body.path("enabled").asBoolean(false));
        } catch (Exception e) {
            log.warn("Keycloak user metadata lookup failed for {}: {}", userId, e.getMessage());
            return null;
        }
    }

    public List<SessionMetadata> sessions(String userId) {
        String token = getServiceAccountToken();
        if (token == null || userId == null || userId.isBlank()) return List.of();
        try {
            ResponseEntity<JsonNode> response = restTemplate.exchange(
                    usersUrl() + "/" + userId + "/sessions", HttpMethod.GET,
                    new HttpEntity<>(adminHeaders(token)), JsonNode.class);
            if (response.getBody() == null || !response.getBody().isArray()) return List.of();
            List<SessionMetadata> result = new ArrayList<>();
            for (JsonNode item : response.getBody()) {
                result.add(new SessionMetadata(item.path("id").asText(),
                        item.path("ipAddress").asText(""), item.path("start").asLong(0),
                        item.path("lastAccess").asLong(0), item.path("clients")));
            }
            return List.copyOf(result);
        } catch (Exception e) {
            log.warn("Keycloak session metadata read failed for {}: {}", userId, e.getMessage());
            return List.of();
        }
    }

    public boolean removeCredential(String userId, String credentialId) {
        if (!safeId(userId) || !safeId(credentialId)) return false;
        String token = getServiceAccountToken();
        if (token == null) return false;
        try {
            restTemplate.exchange(usersUrl() + "/" + userId + "/credentials/" + credentialId,
                    HttpMethod.DELETE, new HttpEntity<>(adminHeaders(token)), Void.class);
            return true;
        } catch (Exception e) {
            log.warn("Keycloak credential removal failed for {}: {}", userId, e.getMessage());
            return false;
        }
    }

    public boolean terminateSession(String sessionId) {
        if (!safeId(sessionId)) return false;
        String token = getServiceAccountToken();
        if (token == null) return false;
        try {
            restTemplate.exchange(keycloakUrl + "/admin/realms/" + realm + "/sessions/" + sessionId,
                    HttpMethod.DELETE, new HttpEntity<>(adminHeaders(token)), Void.class);
            return true;
        } catch (Exception e) {
            log.warn("Keycloak session termination failed for {}: {}", sessionId, e.getMessage());
            return false;
        }
    }

    public boolean terminateAllSessions(String userId) {
        if (!safeId(userId)) return false;
        String token = getServiceAccountToken();
        if (token == null) return false;
        try {
            restTemplate.exchange(usersUrl() + "/" + userId + "/logout", HttpMethod.POST,
                    new HttpEntity<>(adminHeaders(token)), Void.class);
            return true;
        } catch (Exception e) {
            log.warn("Keycloak all-session termination failed for {}: {}", userId, e.getMessage());
            return false;
        }
    }

    private boolean sendExecuteActionsEmail(String userId, List<String> actions, HttpHeaders adminHeaders) {
        return sendExecuteActionsEmail(userId, actions, null, adminHeaders);
    }

    private boolean sendExecuteActionsEmail(String userId, List<String> actions,
                                            Integer lifespanSeconds, HttpHeaders adminHeaders) {
        try {
            String url = usersUrl() + "/" + userId + "/execute-actions-email";
            if (lifespanSeconds != null) url += "?lifespan=" + lifespanSeconds;
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
        if (userAdminSecret == null || userAdminSecret.isBlank()) return null;
        try {
            String tokenUrl = keycloakUrl + "/realms/" + realm + "/protocol/openid-connect/token";
            MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
            formData.add("grant_type", "client_credentials");
            formData.add("client_id", userAdminClientId);
            formData.add("client_secret", userAdminSecret);
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

    private static boolean safeId(String value) {
        return value != null && value.matches("[A-Za-z0-9._:-]{1,160}");
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

    public record CredentialMetadata(String id, String type, String userLabel, Long createdDate) {}
    public record UserMetadata(String id, String email, String username, boolean enabled) {}

    public record SessionMetadata(String id, String ipAddress, long startedAt,
                                  long lastAccessAt, JsonNode clients) {}

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
