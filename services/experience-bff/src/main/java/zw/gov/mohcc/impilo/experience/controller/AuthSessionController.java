package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.http.ResponseCookie;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.util.WebUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.RulesServiceClient;
import zw.gov.mohcc.impilo.experience.client.VarapiServiceClient;
import zw.gov.mohcc.impilo.experience.client.VitoServiceClient;

import java.time.OffsetDateTime;
import java.time.Duration;
import java.util.*;
import java.util.Set;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Auth session controller with real Keycloak credential exchange.
 *
 * <p>Uses the Resource Owner Password Credentials (ROPC) grant against
 * Keycloak's token endpoint. The experience-ui OIDC client is configured
 * as a public client with direct access grants enabled in the impilo
 * realm.</p>
 *
 * <p>When Keycloak is not reachable, falls back to a local session
 * token with a warning log. This ensures the platform remains usable
 * during development without a running Keycloak instance.</p>
 *
 * <p>Health OS Identity Doctrine: everyone starts as CITIZEN. Professional
 * capacity is discovered post-login via the /linked-ids endpoint.</p>
 */
@RestController
@RequestMapping("/internal/v1")
public class AuthSessionController {

    private static final Logger log = LoggerFactory.getLogger(AuthSessionController.class);
    private static final String REFRESH_COOKIE_NAME = "exp_refresh_token";

    @Value("${KEYCLOAK_URL:http://localhost:8080}")
    private String keycloakUrl;

    @Value("${KEYCLOAK_REALM:impilo}")
    private String realm;

    @Value("${KEYCLOAK_CLIENT_ID:experience-ui}")
    private String clientId;

    /**
     * Controls whether local fallback tokens are issued when Keycloak is unreachable.
     * Default: false (fail-closed). Set to true ONLY in development/test environments.
     * Fallback tokens are unsigned UUIDs and will NOT pass Spring Security JWT validation.
     */
    @Value("${impilo.auth.fallback-enabled:false}")
    private boolean fallbackEnabled;

    @Value("${impilo.auth.refresh-cookie-secure:false}")
    private boolean refreshCookieSecure;

    private final RestTemplate restTemplate;
    private final VarapiServiceClient varapiClient;
    private final VitoServiceClient vitoClient;
    private final RulesServiceClient rulesClient;

    public AuthSessionController(RestTemplate serviceRestTemplate,
                                 VarapiServiceClient varapiClient,
                                 VitoServiceClient vitoClient,
                                 RulesServiceClient rulesClient) {
        this.restTemplate = serviceRestTemplate;
        this.varapiClient = varapiClient;
        this.vitoClient = vitoClient;
        this.rulesClient = rulesClient;
    }

    /**
     * Login via email/password -> Keycloak ROPC grant.
     *
     * <p>Exchanges credentials with Keycloak's token endpoint. On success,
     * decodes the JWT access token to extract user claims (sub, email,
     * name, realm_access.roles) and returns them in the AuthTokenResource
     * shape expected by the UI's useLogin hook.</p>
     */
    @PostMapping("/auth/login")
    public ResponseEntity<Map<String, Object>> login(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.POD_ID) String podId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @RequestBody Map<String, Object> body) {

        String email = body.getOrDefault("email", body.getOrDefault("identifier", "")).toString();
        String password = body.getOrDefault("password", "").toString();
        String loginMethod = body.get("method") == null ? null : body.get("method").toString().trim();
        if (loginMethod != null && loginMethod.isEmpty()) {
            loginMethod = null;
        }

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
                int refreshExpiresIn = tokenData.has("refresh_expires_in") ? tokenData.get("refresh_expires_in").asInt() : expiresIn * 6;

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

                return buildLoginResponse(accessToken, refreshToken, expiresIn, refreshExpiresIn, userId, userEmail, displayName, roles, actorType,
                        email, loginMethod, requestId, correlationId);
            }
        } catch (org.springframework.web.client.HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.UNAUTHORIZED) {
                log.info("Keycloak login failed: invalid credentials for {}", email);
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                        "error", Map.of("code", "INVALID_CREDENTIALS", "message", "Invalid email or password")));
            }
            log.warn("Keycloak login error (status {}): {}", e.getStatusCode(), e.getMessage());
        } catch (Exception e) {
            log.warn("Keycloak unreachable for login: {}", e.getMessage());
        }

        // Fallback: local session when Keycloak is not available
        if (!fallbackEnabled) {
            log.error("Keycloak unreachable and fallback is disabled (impilo.auth.fallback-enabled=false). "
                    + "Login failed for: {}", email);
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of(
                    "error", Map.of(
                            "code", "AUTH_UNAVAILABLE",
                            "message", "Authentication service is temporarily unavailable. Please try again later.")));
        }

        log.warn("SECURITY: Using local session fallback for: {}. "
                + "Fallback tokens are unsigned and will NOT pass JWT validation on protected endpoints. "
                + "This mode is intended for development only.", email);
        String fallbackToken = UUID.randomUUID().toString();
        String fallbackUserId = UUID.nameUUIDFromBytes(email.getBytes()).toString();

        // Health OS Identity Doctrine: everyone starts as CITIZEN.
        // Professional capacity is discovered post-login via /linked-ids.
        return buildLoginResponse(fallbackToken, null, 28800, 0, fallbackUserId, email, email,
                List.of("CITIZEN"), "CITIZEN", email, loginMethod, requestId, correlationId);
    }

    @PostMapping("/auth/logout")
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
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, clearRefreshCookie().toString())
                .body(response);
    }

    /**
     * Refresh session token using Keycloak refresh_token grant.
     */
    @PostMapping("/auth/refresh")
    public ResponseEntity<Map<String, Object>> refresh(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody(required = false) Map<String, Object> body,
            HttpServletRequest request) {

        String refreshToken = "";
        if (body != null) {
            refreshToken = body.getOrDefault("refreshToken", "").toString();
        }
        if (refreshToken.isBlank()) {
            var cookie = WebUtils.getCookie(request, REFRESH_COOKIE_NAME);
            if (cookie != null) {
                refreshToken = cookie.getValue();
            }
        }
        if (refreshToken.isBlank()) {
            return ResponseEntity.badRequest()
                    .header(HttpHeaders.SET_COOKIE, clearRefreshCookie().toString())
                    .body(Map.of(
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
                int refreshExpiresIn = tokenData.has("refresh_expires_in") ? tokenData.get("refresh_expires_in").asInt() : expiresIn * 6;

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

                return buildLoginResponse(newAccessToken, newRefreshToken, expiresIn, refreshExpiresIn, userId, userEmail,
                        displayName, roles, actorType, userEmail, null, requestId, correlationId);
            }
        } catch (org.springframework.web.client.HttpClientErrorException e) {
            log.info("Token refresh failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .header(HttpHeaders.SET_COOKIE, clearRefreshCookie().toString())
                    .body(Map.of(
                            "error", Map.of("code", "REFRESH_FAILED", "message", "Session expired. Please log in again.")));
        } catch (Exception e) {
            log.warn("Keycloak refresh failed: {}", e.getMessage());
        }

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .header(HttpHeaders.SET_COOKIE, clearRefreshCookie().toString())
                .body(Map.of(
                        "error", Map.of("code", "REFRESH_FAILED", "message", "Unable to refresh session")));
    }

    @GetMapping("/auth/session")
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

    // ── Linked IDs — Post-login Identity Resolution ─────────────────

    /**
     * Discover linked IDs (Provider ID, Staff ID) for the authenticated person.
     *
     * <p>Health OS Identity Doctrine §5–§6: after login the UI queries this
     * endpoint to discover what professional/staff identifiers are linked
     * to the person's Health ID. This drives the provider activation banner
     * and shell adaptation.</p>
     */
    @GetMapping("/identity/linked-ids")
    public ResponseEntity<Map<String, Object>> getLinkedIds(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(CompanionHeaders.ACTOR_ID) String actorId) {
        try {
            // Query VARAPI for provider registration
            JsonNode providerData = varapiClient.getProviderByHealthId(actorId);
            // Query VITO for staff assignments
            JsonNode staffData = vitoClient.getStaffAssignments(actorId);

            Map<String, Object> linkedIds = new LinkedHashMap<>();
            if (providerData != null && !providerData.isNull()) {
                linkedIds.put("providerId", providerData.has("providerId") ? providerData.get("providerId").asText() : null);
                linkedIds.put("providerStatus", providerData.has("status") ? providerData.get("status").asText() : null);
                linkedIds.put("licenceValid", providerData.has("licenceValid") ? providerData.get("licenceValid").asBoolean() : false);
            }
            if (staffData != null && !staffData.isNull()) {
                linkedIds.put("staffId", staffData.has("staffId") ? staffData.get("staffId").asText() : null);
                linkedIds.put("facilityId", staffData.has("facilityId") ? staffData.get("facilityId").asText() : null);
            }

            return ResponseEntity.ok(Map.of(
                    "data", Map.of("id", actorId, "type", "linked-ids", "attributes", linkedIds),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)
            ));
        } catch (Exception e) {
            log.warn("Failed to resolve linked IDs for actor={}: {}", actorId, e.getMessage());
            return ResponseEntity.ok(Map.of(
                    "data", Map.of("id", actorId, "type", "linked-ids", "attributes", Map.of()),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)
            ));
        }
    }

    // ── Registration ────────────────────────────────────────────────

    @Value("${KEYCLOAK_BACKEND_CLIENT_ID:impilo-backend}")
    private String backendClientId;

    @Value("${KEYCLOAK_BACKEND_SECRET:impilo-backend-secret}")
    private String backendSecret;

    /**
     * Register a new user via Keycloak Admin REST API.
     *
     * <p>Uses the impilo-backend service account to obtain an admin token,
     * then creates the user and assigns the CITIZEN realm role.</p>
     *
     * <p>Health OS Identity Doctrine: everyone registers as a person (CITIZEN).
     * Professional capacity is discovered post-login, not during registration.</p>
     */
    @PostMapping("/auth/register")
    public ResponseEntity<Map<String, Object>> register(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.POD_ID) String podId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody Map<String, Object> body) {

        String email = body.getOrDefault("email", body.getOrDefault("identifier", "")).toString().trim();
        String password = body.getOrDefault("password", "").toString();
        String firstName = body.getOrDefault("firstName", "").toString().trim();
        String lastName = body.getOrDefault("lastName", "").toString().trim();
        String fullName = body.getOrDefault("fullName", "").toString().trim();
        String healthId = body.getOrDefault("healthId", "").toString().trim();

        // Support fullName field: split into first/last if firstName/lastName not provided
        if (firstName.isEmpty() && !fullName.isEmpty()) {
            String[] parts = fullName.split("\\s+", 2);
            firstName = parts[0];
            lastName = parts.length > 1 ? parts[1] : parts[0];
        }

        // Health OS Identity Doctrine: everyone registers as CITIZEN
        String role = "CITIZEN";

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
            // Store Health ID as a user attribute if provided
            if (!healthId.isEmpty()) {
                userRep.put("attributes", Map.of("healthId", List.of(healthId)));
            }
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

            // 4. Assign CITIZEN realm role (required — account is unusable without it)
            if (userId != null && !assignRealmRole(userId, role, adminHeaders)) {
                log.error("Failed to assign role {} to user {} — registration cannot complete", role, userId);
                deleteKeycloakUser(userId, adminHeaders);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                        "error", Map.of("code", "ROLE_ASSIGNMENT_FAILED",
                                "message", "Account could not be activated. Please try again or contact support.")));
            }

            log.info("User registered: email={}, role={}, keycloakId={}, healthId={}", email, role, userId, healthId.isEmpty() ? "none" : healthId);

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
                    int refreshExpiresIn = tokenData.has("refresh_expires_in") ? tokenData.get("refresh_expires_in").asInt() : expiresIn * 6;

                    return buildLoginResponse(accessToken, refreshToken, expiresIn, refreshExpiresIn,
                            userId != null ? userId : UUID.randomUUID().toString(),
                            email, firstName + " " + lastName,
                            List.of(role), "CITIZEN",
                            email, "email", requestId, correlationId);
                }
            } catch (Exception e) {
                log.warn("Auto-login after registration failed: {}", e.getMessage());
            }

            // Registration succeeded but auto-login failed -- return success without token
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("data", Map.of(
                    "id", userId != null ? userId : "registered",
                    "type", "registration",
                    "attributes", Map.of(
                            "status", "REGISTERED",
                            "email", email,
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

    // ── Helpers ──────────────────────────────────────────────────

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

    private ResponseEntity<Map<String, Object>> buildLoginResponse(
            String token, String refreshToken, int expiresIn, int refreshExpiresIn, String userId, String userEmail,
            String displayName, List<String> roles, String actorType,
            String loginPrincipal, String loginMethod,
            String requestId, String correlationId) {

        OffsetDateTime expiresAt = OffsetDateTime.now().plusSeconds(expiresIn);

        Map<String, Object> user = new LinkedHashMap<>();
        user.put("id", userId);
        user.put("email", userEmail);
        user.put("identifier", loginPrincipal != null && !loginPrincipal.isBlank() ? loginPrincipal : userEmail);
        user.put("displayName", displayName);
        user.put("roles", roles);
        user.put("actorType", actorType);
        if (loginMethod != null && !loginMethod.isBlank()) {
            user.put("method", loginMethod);
        }

        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("token", token);
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
        ResponseEntity.BodyBuilder builder = ResponseEntity.ok();
        if (refreshToken != null && !refreshToken.isBlank()) {
            builder.header(HttpHeaders.SET_COOKIE, buildRefreshCookie(refreshToken, refreshExpiresIn).toString());
        } else {
            builder.header(HttpHeaders.SET_COOKIE, clearRefreshCookie().toString());
        }
        return builder.body(response);
    }

    private ResponseCookie buildRefreshCookie(String refreshToken, int expiresIn) {
        return ResponseCookie.from(REFRESH_COOKIE_NAME, refreshToken)
                .httpOnly(true)
                .secure(refreshCookieSecure)
                .sameSite("Lax")
                .path("/")
                .maxAge(Duration.ofSeconds(Math.max(expiresIn, 0)))
                .build();
    }

    private ResponseCookie clearRefreshCookie() {
        return ResponseCookie.from(REFRESH_COOKIE_NAME, "")
                .httpOnly(true)
                .secure(refreshCookieSecure)
                .sameSite("Lax")
                .path("/")
                .maxAge(Duration.ZERO)
                .build();
    }

    // ── Facility Affiliations ──────────────────────────────────────────

    /**
     * List facility affiliations for the authenticated person.
     * Queries VARAPI for provider-facility linkages.
     */
    @GetMapping("/identity/affiliations")
    public ResponseEntity<Map<String, Object>> getAffiliations(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(CompanionHeaders.ACTOR_ID) String actorId) {
        try {
            JsonNode affiliations = varapiClient.getProviderAffiliations(actorId);
            return ResponseEntity.ok(Map.of(
                    "data", affiliations != null ? affiliations : new Object[0],
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception e) {
            log.debug("No affiliations found for actor {}: {}", actorId, e.getMessage());
            return ResponseEntity.ok(Map.of(
                    "data", new Object[0],
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        }
    }

    // ── Professional Notices ─────────────────────────────────────────

    /**
     * List professional notices (licence renewals, CPD, compliance) for the person.
     */
    @GetMapping("/identity/notices")
    public ResponseEntity<Map<String, Object>> getNotices(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(CompanionHeaders.ACTOR_ID) String actorId) {
        try {
            JsonNode notices = varapiClient.getProviderNotices(actorId);
            return ResponseEntity.ok(Map.of(
                    "data", notices != null ? notices : new Object[0],
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception e) {
            log.debug("No notices for actor {}: {}", actorId, e.getMessage());
            return ResponseEntity.ok(Map.of(
                    "data", new Object[0],
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        }
    }

    // ── CDS Alerts Count ─────────────────────────────────────────────

    /**
     * Return the count of active Clinical Decision Support alerts.
     * Proxies to the rules-service or clinical-knowledge-platform.
     */
    @GetMapping("/cds/alerts/count")
    public ResponseEntity<Map<String, Object>> getCdsAlertCount(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            JsonNode alerts = rulesClient.getActiveAlerts();
            int count = (alerts != null && alerts.isArray()) ? alerts.size() : 0;
            return ResponseEntity.ok(Map.of(
                    "data", Map.of("count", count),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception e) {
            log.warn("CDS alerts fetch failed: {}", e.getMessage());
            return ResponseEntity.ok(Map.of(
                    "data", Map.of("count", 0),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        }
    }

    private String determineActorType(List<String> roles) {
        if (roles.contains("SYSTEM_ADMIN") || roles.contains("SUPER_ADMIN") || roles.contains("DEVELOPER")
                || roles.contains("SUPPORT_AGENT") || roles.contains("FINANCE")) {
            return "OPERATOR";
        }
        if (roles.contains("CLINICIAN") || roles.contains("NURSE") || roles.contains("FACILITY_ADMIN")) {
            return "PROVIDER";
        }
        return "CITIZEN";
    }

    private boolean assignRealmRole(String userId, String roleName, HttpHeaders adminHeaders) {
        try {
            String rolesUrl = keycloakUrl + "/admin/realms/" + realm + "/roles/" + roleName;
            ResponseEntity<JsonNode> roleResponse = restTemplate.exchange(
                    rolesUrl, HttpMethod.GET,
                    new HttpEntity<>(adminHeaders),
                    JsonNode.class);

            if (!roleResponse.getStatusCode().is2xxSuccessful() || roleResponse.getBody() == null) {
                return false;
            }

            String assignUrl = keycloakUrl + "/admin/realms/" + realm + "/users/" + userId + "/role-mappings/realm";
            ResponseEntity<String> assignResponse = restTemplate.exchange(
                    assignUrl, HttpMethod.POST,
                    new HttpEntity<>(List.of(roleResponse.getBody()), adminHeaders),
                    String.class);
            if (!assignResponse.getStatusCode().is2xxSuccessful()) {
                return false;
            }
            log.info("Role {} assigned to user {}", roleName, userId);
            return true;
        } catch (Exception e) {
            log.warn("Failed to assign role {} to user {}: {}", roleName, userId, e.getMessage());
            return false;
        }
    }

    private void deleteKeycloakUser(String userId, HttpHeaders adminHeaders) {
        try {
            String userUrl = keycloakUrl + "/admin/realms/" + realm + "/users/" + userId;
            restTemplate.exchange(userUrl, HttpMethod.DELETE, new HttpEntity<>(adminHeaders), String.class);
            log.info("Rolled back Keycloak user {}", userId);
        } catch (Exception e) {
            log.warn("Failed to roll back Keycloak user {}: {}", userId, e.getMessage());
        }
    }
}
