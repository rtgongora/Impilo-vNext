package zw.gov.mohcc.impilo.experience.controller.mobile;

import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.controller.ResourceNotFoundException;
import zw.gov.mohcc.impilo.experience.service.OutboxService;

import java.time.OffsetDateTime;
import java.util.*;

/**
 * Mobile provider profile endpoints.
 * GET   /internal/v1/mobile/provider/profile  - get provider profile
 * PATCH /internal/v1/mobile/provider/profile  - update provider contact/profile details
 */
@RestController
@RequestMapping("/internal/v1/mobile/provider/profile")
public class MobileProfileController {

    private final JdbcTemplate jdbcTemplate;
    private final OutboxService outboxService;

    public MobileProfileController(JdbcTemplate jdbcTemplate, OutboxService outboxService) {
        this.jdbcTemplate = jdbcTemplate;
        this.outboxService = outboxService;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> getProfile(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader("X-Actor-ID") String actorId) {

        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
            SELECT id, user_id, given_name, family_name, practitioner_number, cadre,
                   phone, email, facility_id, status, created_at, updated_at
            FROM registry_providers
            WHERE tenant_id = ? AND user_id = ?
            """, tenantId, actorId);

        if (rows.isEmpty()) {
            throw new ResourceNotFoundException("Provider profile not found for: " + actorId);
        }

        Map<String, Object> row = rows.get(0);
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("user_id", row.get("user_id"));
        attributes.put("given_name", row.get("given_name"));
        attributes.put("family_name", row.get("family_name"));
        attributes.put("practitioner_number", row.get("practitioner_number"));
        attributes.put("cadre", row.get("cadre"));
        attributes.put("phone", row.get("phone"));
        attributes.put("email", row.get("email"));
        attributes.put("facility_id", row.get("facility_id") != null ? row.get("facility_id").toString() : null);
        attributes.put("status", row.get("status"));
        attributes.put("created_at", row.get("created_at"));
        attributes.put("updated_at", row.get("updated_at"));

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", Map.of(
                "id", row.get("id").toString(),
                "type", "ProviderProfile",
                "attributes", attributes
        ));
        response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
        return ResponseEntity.ok(response);
    }

    @PatchMapping
    @Transactional
    public ResponseEntity<Map<String, Object>> updateProfile(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.POD_ID) String podId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader("X-Actor-ID") String actorId,
            @RequestBody Map<String, Object> updates) {

        OffsetDateTime now = OffsetDateTime.now();

        if (updates.containsKey("phone")) {
            jdbcTemplate.update(
                    "UPDATE registry_providers SET phone = ?, updated_at = ? WHERE tenant_id = ? AND user_id = ?",
                    updates.get("phone"), now, tenantId, actorId);
        }
        if (updates.containsKey("email")) {
            jdbcTemplate.update(
                    "UPDATE registry_providers SET email = ?, updated_at = ? WHERE tenant_id = ? AND user_id = ?",
                    updates.get("email"), now, tenantId, actorId);
        }

        outboxService.writeOutboxEvent(
                "impilo.experience.provider.profile-updated.v1",
                correlationId, requestId, requestId, tenantId, podId,
                "ProviderProfile", actorId,
                Map.of("user_id", actorId, "updated_fields", updates.keySet().toString()),
                Map.of());

        return getProfile(tenantId, requestId, correlationId, actorId);
    }
}
