package zw.gov.mohcc.impilo.experience.controller;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.domain.AdminUser;
import zw.gov.mohcc.impilo.experience.repository.AdminUserRepository;

import java.util.*;

/**
 * Admin user management endpoints.
 * GET /internal/v1/admin/users — list admin users with role, status, search filters, pagination.
 * GET /internal/v1/admin/users/{id} — get single admin user.
 */
@RestController
@RequestMapping("/internal/v1/admin/users")
public class AdminUserController {

    private final AdminUserRepository adminUserRepository;

    public AdminUserController(AdminUserRepository adminUserRepository) {
        this.adminUserRepository = adminUserRepository;
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
