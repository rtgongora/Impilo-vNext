package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.domain.AdminUser;
import zw.gov.mohcc.impilo.experience.repository.AdminUserRepository;
import zw.gov.mohcc.impilo.experience.service.OutboxService;

import java.time.OffsetDateTime;
import java.util.*;

/**
 * Admin user management endpoints.
 * GET /internal/v1/admin/users — list admin users with role, status, search filters, pagination.
 * GET /internal/v1/admin/users/{id} — get single admin user.
 */
@RestController
@RequestMapping("/internal/v1/admin/users")
public class AdminUserController {

    private static final Logger log = LoggerFactory.getLogger(AdminUserController.class);

    @Value("${KEYCLOAK_URL:http://localhost:8080}")
    private String keycloakUrl;
    @Value("${KEYCLOAK_REALM:impilo}")
    private String realm;
    @Value("${KEYCLOAK_BACKEND_CLIENT_ID:impilo-backend}")
    private String backendClientId;
    @Value("${KEYCLOAK_BACKEND_SECRET:impilo-backend-secret}")
    private String backendSecret;

    private final AdminUserRepository adminUserRepository;
    private final JdbcTemplate jdbcTemplate;
    private final OutboxService outboxService;
    private final RestTemplate restTemplate = new RestTemplate();

    public AdminUserController(AdminUserRepository adminUserRepository,
                               JdbcTemplate jdbcTemplate,
                               OutboxService outboxService) {
        this.adminUserRepository = adminUserRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.outboxService = outboxService;
    }

    public record CreateUserRequest(
            @NotBlank String email,
            @NotBlank String firstName,
            @NotBlank String lastName,
            @NotBlank String password,
            @NotBlank String role,
            String facilityId
    ) {}

    /**
     * Create a new platform user (admin operation).
     * POST /internal/v1/admin/users
     *
     * Creates user in Keycloak + local admin_users table.
     * All realm roles are available (including SYSTEM_ADMIN, FACILITY_ADMIN).
     */
    @PostMapping
    @Transactional
    public ResponseEntity<Map<String, Object>> createUser(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.POD_ID) String podId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @Valid @RequestBody CreateUserRequest request) {

        // Get admin token
        String adminToken = getServiceAccountToken();
        if (adminToken == null) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of(
                    "error", Map.of("code", "AUTH_SERVICE_UNAVAILABLE",
                            "message", "User management service is temporarily unavailable")));
        }

        try {
            // Create user in Keycloak
            String adminUrl = keycloakUrl + "/admin/realms/" + realm + "/users";
            Map<String, Object> userRep = new LinkedHashMap<>();
            userRep.put("username", request.email());
            userRep.put("email", request.email());
            userRep.put("firstName", request.firstName());
            userRep.put("lastName", request.lastName());
            userRep.put("enabled", true);
            userRep.put("emailVerified", true); // Admin-created users are pre-verified
            userRep.put("credentials", List.of(Map.of("type", "password", "value", request.password(), "temporary", false)));

            HttpHeaders adminHeaders = new HttpHeaders();
            adminHeaders.setContentType(MediaType.APPLICATION_JSON);
            adminHeaders.setBearerAuth(adminToken);

            ResponseEntity<String> createResponse = restTemplate.exchange(
                    adminUrl, HttpMethod.POST, new HttpEntity<>(userRep, adminHeaders), String.class);

            if (createResponse.getStatusCode() == HttpStatus.CONFLICT) {
                return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                        "error", Map.of("code", "USER_EXISTS", "message", "User with this email already exists")));
            }

            String kcUserId = null;
            String locationHeader = createResponse.getHeaders().getFirst("Location");
            if (locationHeader != null) {
                kcUserId = locationHeader.substring(locationHeader.lastIndexOf("/") + 1);
            }

            // Assign role
            if (kcUserId != null) {
                try {
                    String rolesUrl = keycloakUrl + "/admin/realms/" + realm + "/roles/" + request.role();
                    ResponseEntity<JsonNode> roleResponse = restTemplate.exchange(
                            rolesUrl, HttpMethod.GET, new HttpEntity<>(adminHeaders), JsonNode.class);
                    if (roleResponse.getStatusCode().is2xxSuccessful() && roleResponse.getBody() != null) {
                        String assignUrl = keycloakUrl + "/admin/realms/" + realm + "/users/" + kcUserId + "/role-mappings/realm";
                        restTemplate.exchange(assignUrl, HttpMethod.POST,
                                new HttpEntity<>(List.of(roleResponse.getBody()), adminHeaders), String.class);
                    }
                } catch (Exception e) {
                    log.warn("Role assignment failed for {}: {}", kcUserId, e.getMessage());
                }
            }

            // Create local admin_users record
            UUID localId = UUID.randomUUID();
            OffsetDateTime now = OffsetDateTime.now();
            jdbcTemplate.update("""
                INSERT INTO admin_users
                    (id, tenant_id, username, email, display_name, role, status, facility_id, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, 'ACTIVE', ?::uuid, ?, ?)
                """,
                    localId, tenantId, request.email(), request.email(),
                    request.firstName() + " " + request.lastName(),
                    request.role(), request.facilityId(), now, now);

            outboxService.writeOutboxEvent(
                    "impilo.experience.admin.user-created.v1",
                    correlationId, requestId, requestId, tenantId, podId,
                    "AdminUser", localId.toString(),
                    Map.of("user_id", localId.toString(), "email", request.email(), "role", request.role()),
                    Map.of());

            log.info("Admin user created: email={}, role={}, kcId={}", request.email(), request.role(), kcUserId);

            Map<String, Object> attributes = new LinkedHashMap<>();
            attributes.put("email", request.email());
            attributes.put("display_name", request.firstName() + " " + request.lastName());
            attributes.put("role", request.role());
            attributes.put("status", "ACTIVE");
            attributes.put("created_at", now);

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("data", Map.of("id", localId.toString(), "type", "AdminUser", "attributes", attributes));
            response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
            return ResponseEntity.status(HttpStatus.CREATED).body(response);

        } catch (org.springframework.web.client.HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.CONFLICT) {
                return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                        "error", Map.of("code", "USER_EXISTS", "message", "User already exists")));
            }
            log.error("Admin user creation failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "error", Map.of("code", "CREATION_FAILED", "message", "Failed to create user")));
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
            log.error("Failed to get service account token: {}", e.getMessage());
        }
        return null;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> listAdminUsers(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String search) {

        PageRequest pageable = PageRequest.of(page, Math.min(size, 100), Sort.by("username").ascending());

        Page<AdminUser> result = adminUserRepository.findByFilters(tenantId, role, status, search, pageable);

        List<Map<String, Object>> data = result.getContent().stream()
                .map(this::toResource)
                .toList();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", data);
        response.put("meta", Map.of(
                "request_id", requestId,
                "correlation_id", correlationId,
                "page", Map.of(
                        "number", result.getNumber(),
                        "size", result.getSize(),
                        "total_elements", result.getTotalElements(),
                        "total_pages", result.getTotalPages()
                )
        ));

        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getAdminUser(
            @PathVariable UUID id,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {

        AdminUser user = adminUserRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Admin user not found: " + id));

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", toResource(user));
        response.put("meta", Map.of(
                "request_id", requestId,
                "correlation_id", correlationId
        ));

        return ResponseEntity.ok(response);
    }

    private Map<String, Object> toResource(AdminUser u) {
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("username", u.getUsername());
        attributes.put("email", u.getEmail());
        attributes.put("display_name", u.getDisplayName());
        attributes.put("role", u.getRole());
        attributes.put("status", u.getStatus());
        attributes.put("facility_id", u.getFacilityId());
        attributes.put("last_login_at", u.getLastLoginAt());
        attributes.put("created_at", u.getCreatedAt());
        attributes.put("updated_at", u.getUpdatedAt());

        Map<String, Object> resource = new LinkedHashMap<>();
        resource.put("id", u.getId().toString());
        resource.put("type", "AdminUser");
        resource.put("attributes", attributes);
        return resource;
    }
}
