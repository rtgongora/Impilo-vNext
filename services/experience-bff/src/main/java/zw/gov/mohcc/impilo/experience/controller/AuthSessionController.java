package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.Set;

/**
 * Auth session controller with real Keycloak credential exchange.
 *
 * <p>Uses the Resource Owner Password Credentials (ROPC) grant against
 * Keycloak's token endpoint. The experience-ui client is configured
 * as a public client with direct access grants enabled in the impilo
 * realm.</p>
 *
 * <p>When Keycloak is not reachable, falls back to a local session
 * token with a warning log. This ensures the platform remains usable
 * during development without a running Keycloak instance.</p>
 */
@RestController
@RequestMapping("/internal/v1/auth")
public class AuthSessionController {

    private static final Logger log = LoggerFactory.getLogger(AuthSessionController.class);

    @Value("${KEYCLOAK_URL:http://localhost:8080}")
    private String keycloakUrl;

    @Value("${KEYCLOAK_REALM:impilo}")
    private String realm;

    @Value("${KEYCLOAK_CLIENT_ID:experience-ui}")
    private String clientId;

    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * Login via email/password → Keycloak ROPC grant.
     *
     * <p>Exchanges credentials with Keycloak's token endpoint. On success,
     * decodes the JWT access token to extract user claims (sub, email,
     * name, realm_access.roles) and returns them in the AuthTokenResource
     * shape expected by the UI's useLogin hook.</p>
     */
    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.POD_ID) String podId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @RequestBody Map<String, Object> body) {

        String email = body.getOrDefault("email", body.getOrDefault("identifier", "")).toString();
        String password = body.getOrDefault("password", "").toString();

        if (email.isBlank() || password.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", Map.of("code", "VALIDATION", "message", "Email and password are required")));
        }

        // Attempt Keycloak ROPC grant
        try {
            String tokenUrl = keycloakUrl + "/realms/" + realm + "/protocol/openid-connect/token";

            MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
            formData.add("grant_type", "password");
            formData.add("client_id", clientId);
            formData.add("username", email);
            formData.add("password", password);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            ResponseEntity<JsonNode> kcResponse = restTemplate.exchange(
                    tokenUrl, HttpMethod.POST,
                    new HttpEntity<>(formData, headers),
                    JsonNode.class);

            if (kcResponse.getStatusCode().is2xxSuccessful() && kcResponse.getBody() != null) {
                JsonNode tokenData = kcResponse.getBody();
                String accessToken = tokenData.get("access_token").asText();
                String refreshToken = tokenData.has("refresh_token") ? tokenData.get("refresh_token").asText() : null;
                int expiresIn = tokenData.has("expires_in") ? tokenData.get("expires_in").asInt() : 28800;

                // Decode JWT payload (base64 middle segment)
                String[] parts = accessToken.split("\\.");
                String payloadJson = new String(Base64.getUrlDecoder().decode(parts[1]));
                JsonNode claims = new com.fasterxml.jackson.databind.ObjectMapper().readTree(payloadJson);

                String userId = claims.has("sub") ? claims.get("sub").asText() : UUID.randomUUID().toString();
                String userEmail = claims.has("email") ? claims.get("email").asText() : email;
                String displayName = claims.has("name") ? claims.get("name").asText()
                        : claims.has("preferred_username") ? claims.get("preferred_username").asText() : email;

                List<String> roles = new ArrayList<>();
                if (claims.has("realm_access") && claims.get("realm_access").has("roles")) {
                    for (JsonNode role : claims.get("realm_access").get("roles")) {
                        String r = role.asText();
                        if (!r.startsWith("default-roles-") && !r.equals("offline_access") && !r.equals("uma_authorization")) {
                            roles.add(r);
                        }
                    }
                }

                String actorType = determineActorType(roles);

                log.info("Keycloak login successful: user={}, email={}, roles={}", userId, userEmail, roles);

                return buildLoginResponse(accessToken, refreshToken, expiresIn, userId, userEmail, displayName, roles, actorType, requestId, correlationId);
            }
        } catch (org.springframework.web.client.HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.UNAUTHORIZED) {
                log.info("Keycloak login failed: invalid credentials for {}", email);
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                        "error", Map.of("code", "INVALID_CREDENTIALS", "message", "Invalid email or password")));
            }
            log.warn("Keycloak login error: {}", e.getMessage());
        } catch (Exception e) {
            log.warn("Keycloak unreachable, falling back to local session: {}", e.getMessage());
        }

        // Fallback: local session when Keycloak is not available
        log.warn("Using local session fallback for: {}", email);
        String fallbackToken = UUID.randomUUID().toString();
        String fallbackUserId = UUID.nameUUIDFromBytes(email.getBytes()).toString();
        return buildLoginResponse(fallbackToken, null, 28800, fallbackUserId, email, email,
                List.of("CLINICIAN"), "PROVIDER", requestId, correlationId);
    }

    @PostMapping("/logout")
    public ResponseEntity<Map<String, Object>> logout(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.IDEMPOTENCY_KEY, required = false) String idempotencyKey) {

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", Map.of(
                "id", "logout",
                "type", "logout",
                "attributes", Map.of("status", "logged_out")
        ));
        response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
        return ResponseEntity.ok(response);
    }

    /**
     * Refresh session token using Keycloak refresh_token grant.
     */
    @PostMapping("/refresh")
    public ResponseEntity<Map<String, Object>> refresh(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody Map<String, Object> body) {

        String refreshToken = body.getOrDefault("refreshToken", "").toString();
        if (refreshToken.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", Map.of("code", "VALIDATION", "message", "refreshToken is required")));
        }

        try {
            String tokenUrl = keycloakUrl + "/realms/" + realm + "/protocol/openid-connect/token";

            MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
            formData.add("grant_type", "refresh_token");
            formData.add("client_id", clientId);
            formData.add("refresh_token", refreshToken);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            ResponseEntity<JsonNode> kcResponse = restTemplate.exchange(
                    tokenUrl, HttpMethod.POST,
                    new HttpEntity<>(formData, headers),
                    JsonNode.class);

            if (kcResponse.getStatusCode().is2xxSuccessful() && kcResponse.getBody() != null) {
                JsonNode tokenData = kcResponse.getBody();
                String newAccessToken = tokenData.get("access_token").asText();
                String newRefreshToken = tokenData.has("refresh_token") ? tokenData.get("refresh_token").asText() : null;
                int expiresIn = tokenData.has("expires_in") ? tokenData.get("expires_in").asInt() : 28800;

                String[] parts = newAccessToken.split("\\.");
                String payloadJson = new String(Base64.getUrlDecoder().decode(parts[1]));
                JsonNode claims = new com.fasterxml.jackson.databind.ObjectMapper().readTree(payloadJson);

                String userId = claims.has("sub") ? claims.get("sub").asText() : "";
                String userEmail = claims.has("email") ? claims.get("email").asText() : "";
                String displayName = claims.has("name") ? claims.get("name").asText()
                        : claims.has("preferred_username") ? claims.get("preferred_username").asText() : userEmail;

                List<String> roles = new ArrayList<>();
                if (claims.has("realm_access") && claims.get("realm_access").has("roles")) {
                    for (JsonNode role : claims.get("realm_access").get("roles")) {
                        String r = role.asText();
                        if (!r.startsWith("default-roles-") && !r.equals("offline_access") && !r.equals("uma_authorization")) {
                            roles.add(r);
                        }
                    }
                }

                String actorType = determineActorType(roles);
                log.info("Token refreshed for user={}", userId);

                return buildLoginResponse(newAccessToken, newRefreshToken, expiresIn, userId, userEmail,
                        displayName, roles, actorType, requestId, correlationId);
            }
        } catch (org.springframework.web.client.HttpClientErrorException e) {
            log.info("Token refresh failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                    "error", Map.of("code", "REFRESH_FAILED", "message", "Session expired. Please log in again.")));
        } catch (Exception e) {
            log.warn("Keycloak refresh failed: {}", e.getMessage());
        }

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                "error", Map.of("code", "REFRESH_FAILED", "message", "Unable to refresh session")));
    }

    @GetMapping("/session")
    public ResponseEntity<Map<String, Object>> getSession(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = "Authorization", required = false) String authorization) {

        Map<String, Object> session = new LinkedHashMap<>();
        session.put("authenticated", authorization != null && !authorization.isBlank());
        session.put("tenant_id", tenantId);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", session);
        response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
        return ResponseEntity.ok(response);
    }

    // ── Helpers ──────────────────────────────────────────────────

    private ResponseEntity<Map<String, Object>> buildLoginResponse(
            String token, String refreshToken, int expiresIn, String userId, String email,
            String displayName, List<String> roles, String actorType,
            String requestId, String correlationId) {

        OffsetDateTime expiresAt = OffsetDateTime.now().plusSeconds(expiresIn);

        Map<String, Object> user = new LinkedHashMap<>();
        user.put("id", userId);
        user.put("email", email);
        user.put("displayName", displayName);
        user.put("roles", roles);
        user.put("actorType", actorType);

        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("token", token);
        if (refreshToken != null) attributes.put("refreshToken", refreshToken);
        attributes.put("expiresAt", expiresAt.toString());
        attributes.put("expiresIn", expiresIn);
        attributes.put("user", user);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", Map.of(
                "id", userId,
                "type", "auth_token",
                "attributes", attributes
        ));
        response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
        return ResponseEntity.ok(response);
    }

    @Value("${KEYCLOAK_BACKEND_CLIENT_ID:impilo-backend}")
    private String backendClientId;

    @Value("${KEYCLOAK_BACKEND_SECRET:impilo-backend-secret}")
    private String backendSecret;

    /**
     * Register a new user via Keycloak Admin REST API.
     *
     * <p>Uses the impilo-backend service account to obtain an admin token,
     * then creates the user and assigns the requested realm role.</p>
     */
    @PostMapping("/register")
    public ResponseEntity<Map<String, Object>> register(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.POD_ID) String podId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody Map<String, Object> body) {

        String email = body.getOrDefault("email", "").toString().trim();
        String password = body.getOrDefault("password", "").toString();
        String firstName = body.getOrDefault("firstName", "").toString().trim();
        String lastName = body.getOrDefault("lastName", "").toString().trim();
        String role = body.getOrDefault("role", "CITIZEN").toString().toUpperCase();

        // Validation
        if (email.isBlank() || password.isBlank() || firstName.isBlank() || lastName.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", Map.of("code", "VALIDATION", "message",
                            "email, password, firstName, and lastName are required")));
        }
        if (password.length() < 8) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", Map.of("code", "VALIDATION", "message",
                            "Password must be at least 8 characters")));
        }

        Set<String> allowedRoles = Set.of("CITIZEN", "CLINICIAN", "NURSE", "PHARMACIST");
        if (!allowedRoles.contains(role)) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", Map.of("code", "VALIDATION", "message",
                            "Self-registration is only available for: " + allowedRoles)));
        }

        try {
            // 1. Get admin token via service account
            String adminToken = getServiceAccountToken();
            if (adminToken == null) {
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of(
                        "error", Map.of("code", "AUTH_SERVICE_UNAVAILABLE",
                                "message", "Registration service is temporarily unavailable")));
            }

            // 2. Create user in Keycloak
            String adminUrl = keycloakUrl + "/admin/realms/" + realm + "/users";

            Map<String, Object> userRep = new LinkedHashMap<>();
            userRep.put("username", email);
            userRep.put("email", email);
            userRep.put("firstName", firstName);
            userRep.put("lastName", lastName);
            userRep.put("enabled", true);
            userRep.put("emailVerified", false);
            userRep.put("credentials", List.of(Map.of(
                    "type", "password",
                    "value", password,
                    "temporary", false
            )));

            HttpHeaders adminHeaders = new HttpHeaders();
            adminHeaders.setContentType(MediaType.APPLICATION_JSON);
            adminHeaders.setBearerAuth(adminToken);

            ResponseEntity<String> createResponse = restTemplate.exchange(
                    adminUrl, HttpMethod.POST,
                    new HttpEntity<>(userRep, adminHeaders),
                    String.class);

            if (createResponse.getStatusCode() == HttpStatus.CONFLICT) {
                return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                        "error", Map.of("code", "USER_EXISTS",
                                "message", "An account with this email already exists")));
            }

            if (!createResponse.getStatusCode().is2xxSuccessful()) {
                log.error("Keycloak user creation failed: {} {}", createResponse.getStatusCode(), createResponse.getBody());
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                        "error", Map.of("code", "REGISTRATION_FAILED",
                                "message", "Failed to create account")));
            }

            // 3. Get the created user's ID from the Location header
            String locationHeader = createResponse.getHeaders().getFirst("Location");
            String userId = locationHeader != null
                    ? locationHeader.substring(locationHeader.lastIndexOf("/") + 1)
                    : null;

            // 4. Assign realm role if we have the user ID
            if (userId != null && !role.equals("default")) {
                try {
                    // Get the role representation
                    String rolesUrl = keycloakUrl + "/admin/realms/" + realm + "/roles/" + role;
                    ResponseEntity<JsonNode> roleResponse = restTemplate.exchange(
                            rolesUrl, HttpMethod.GET,
                            new HttpEntity<>(adminHeaders),
                            JsonNode.class);

                    if (roleResponse.getStatusCode().is2xxSuccessful() && roleResponse.getBody() != null) {
                        // Assign role to user
                        String assignUrl = keycloakUrl + "/admin/realms/" + realm + "/users/" + userId + "/role-mappings/realm";
                        restTemplate.exchange(
                                assignUrl, HttpMethod.POST,
                                new HttpEntity<>(List.of(roleResponse.getBody()), adminHeaders),
                                String.class);
                        log.info("Role {} assigned to user {}", role, userId);
                    }
                } catch (Exception e) {
                    log.warn("Failed to assign role {} to user {}: {}", role, userId, e.getMessage());
                }
            }

            log.info("User registered: email={}, role={}, keycloakId={}", email, role, userId);

            // 5. Auto-login the new user
            try {
                String tokenUrl = keycloakUrl + "/realms/" + realm + "/protocol/openid-connect/token";
                MultiValueMap<String, String> loginForm = new LinkedMultiValueMap<>();
                loginForm.add("grant_type", "password");
                loginForm.add("client_id", clientId);
                loginForm.add("username", email);
                loginForm.add("password", password);

                HttpHeaders loginHeaders = new HttpHeaders();
                loginHeaders.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

                ResponseEntity<JsonNode> loginResponse = restTemplate.exchange(
                        tokenUrl, HttpMethod.POST,
                        new HttpEntity<>(loginForm, loginHeaders),
                        JsonNode.class);

                if (loginResponse.getStatusCode().is2xxSuccessful() && loginResponse.getBody() != null) {
                    JsonNode tokenData = loginResponse.getBody();
                    String accessToken = tokenData.get("access_token").asText();
                    String refreshToken = tokenData.has("refresh_token") ? tokenData.get("refresh_token").asText() : null;
                    int expiresIn = tokenData.has("expires_in") ? tokenData.get("expires_in").asInt() : 28800;

                    return buildLoginResponse(accessToken, refreshToken, expiresIn,
                            userId != null ? userId : UUID.randomUUID().toString(),
                            email, firstName + " " + lastName,
                            List.of(role), determineActorType(List.of(role)),
                            requestId, correlationId);
                }
            } catch (Exception e) {
                log.warn("Auto-login after registration failed: {}", e.getMessage());
            }

            // Registration succeeded but auto-login failed — return success without token
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("data", Map.of(
                    "id", userId != null ? userId : "registered",
                    "type", "registration",
                    "attributes", Map.of(
                            "status", "REGISTERED",
                            "email", email,
                            "role", role,
                            "message", "Account created. Please sign in."
                    )
            ));
            response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
            return ResponseEntity.status(HttpStatus.CREATED).body(response);

        } catch (org.springframework.web.client.HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.CONFLICT) {
                return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                        "error", Map.of("code", "USER_EXISTS",
                                "message", "An account with this email already exists")));
            }
            log.error("Registration error: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "error", Map.of("code", "REGISTRATION_FAILED", "message", "Registration failed")));
        } catch (Exception e) {
            log.error("Registration error: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "error", Map.of("code", "REGISTRATION_FAILED", "message", "Registration failed")));
        }
    }

    /**
     * Get a service account access token for Keycloak admin operations.
     */
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
                    tokenUrl, HttpMethod.POST,
                    new HttpEntity<>(formData, headers),
                    JsonNode.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return response.getBody().get("access_token").asText();
            }
        } catch (Exception e) {
            log.error("Failed to get service account token: {}", e.getMessage());
        }
        return null;
    }

    private String determineActorType(List<String> roles) {
        if (roles.contains("SYSTEM_ADMIN") || roles.contains("SUPPORT_AGENT")) return "OPERATOR";
        if (roles.contains("CITIZEN")) return "CITIZEN";
        if (roles.contains("CLINICIAN") || roles.contains("NURSE") || roles.contains("FACILITY_ADMIN")) return "PROVIDER";
        return "PROVIDER";
    }
}
