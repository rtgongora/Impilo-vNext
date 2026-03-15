package zw.gov.mohcc.impilo.experience.controller.mobile;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.service.OutboxService;

import java.time.OffsetDateTime;
import java.util.*;

/**
 * Mobile offline and break-glass endpoints.
 * GET  /internal/v1/mobile/provider/entitlement/verify?cpid=  - entitlement check
 * POST /internal/v1/mobile/provider/break-glass/activate      - activate break-glass
 * POST /internal/v1/mobile/provider/break-glass/deactivate    - deactivate break-glass
 */
@RestController
@RequestMapping("/internal/v1/mobile/provider")
public class MobileOfflineController {

    private final JdbcTemplate jdbcTemplate;
    private final OutboxService outboxService;

    public MobileOfflineController(JdbcTemplate jdbcTemplate, OutboxService outboxService) {
        this.jdbcTemplate = jdbcTemplate;
        this.outboxService = outboxService;
    }

    public record ActivateBreakGlassRequest(
            @NotBlank String activated_by,
            @NotBlank String facility_id,
            @NotBlank String reason,
            Integer duration_minutes
    ) {}

    public record DeactivateBreakGlassRequest(
            @NotBlank String deactivated_by,
            @NotBlank String facility_id,
            String notes
    ) {}

    @GetMapping("/entitlement/verify")
    public ResponseEntity<Map<String, Object>> verifyEntitlement(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam(name = "cpid") String cpid) {

        List<Map<String, Object>> patientRows = jdbcTemplate.queryForList("""
            SELECT id, cpid, given_name, family_name, date_of_birth, sex, status,
                   facility_id, created_at
            FROM patients
            WHERE tenant_id = ? AND cpid = ?
            """, tenantId, cpid);

        if (patientRows.isEmpty()) {
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("data", Map.of(
                    "entitled", false,
                    "cpid", cpid,
                    "reason", "Patient not found"
            ));
            response.put("meta", Map.of(
                    "request_id", requestId,
                    "correlation_id", correlationId
            ));
            return ResponseEntity.ok(response);
        }

        Map<String, Object> patient = patientRows.get(0);
        boolean isActive = "ACTIVE".equals(patient.get("status"));

        List<Map<String, Object>> entitlementRows = jdbcTemplate.queryForList("""
            SELECT id, scheme_name, scheme_number, coverage_type, valid_from, valid_to, status
            FROM entitlements
            WHERE tenant_id = ? AND patient_id = ? AND status = 'ACTIVE'
              AND (valid_to IS NULL OR valid_to >= CURRENT_DATE)
            ORDER BY valid_from DESC
            LIMIT 5
            """, tenantId, patient.get("id"));

        boolean hasEntitlement = !entitlementRows.isEmpty();

        Map<String, Object> entitlementData = new LinkedHashMap<>();
        entitlementData.put("entitled", isActive && hasEntitlement);
        entitlementData.put("cpid", cpid);
        entitlementData.put("patient_id", patient.get("id").toString());
        entitlementData.put("patient_status", patient.get("status"));
        entitlementData.put("given_name", patient.get("given_name"));
        entitlementData.put("family_name", patient.get("family_name"));
        entitlementData.put("date_of_birth", patient.get("date_of_birth"));
        entitlementData.put("sex", patient.get("sex"));

        List<Map<String, Object>> coverages = entitlementRows.stream().map(row -> {
            Map<String, Object> coverage = new LinkedHashMap<>();
            coverage.put("id", row.get("id").toString());
            coverage.put("scheme_name", row.get("scheme_name"));
            coverage.put("scheme_number", row.get("scheme_number"));
            coverage.put("coverage_type", row.get("coverage_type"));
            coverage.put("valid_from", row.get("valid_from"));
            coverage.put("valid_to", row.get("valid_to"));
            coverage.put("status", row.get("status"));
            return coverage;
        }).toList();

        entitlementData.put("coverages", coverages);

        if (!isActive) {
            entitlementData.put("reason", "Patient status is " + patient.get("status"));
        } else if (!hasEntitlement) {
            entitlementData.put("reason", "No active entitlement found");
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", entitlementData);
        response.put("meta", Map.of(
                "request_id", requestId,
                "correlation_id", correlationId
        ));

        return ResponseEntity.ok(response);
    }

    @PostMapping("/break-glass/activate")
    @Transactional
    public ResponseEntity<Map<String, Object>> activateBreakGlass(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.POD_ID) String podId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @Valid @RequestBody ActivateBreakGlassRequest request) {

        UUID breakGlassId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        int durationMinutes = request.duration_minutes() != null ? request.duration_minutes() : 60;
        OffsetDateTime expiresAt = now.plusMinutes(durationMinutes);

        jdbcTemplate.update("""
            INSERT INTO break_glass_sessions
                (id, tenant_id, facility_id, activated_by, reason, status,
                 activated_at, expires_at, created_at, updated_at)
            VALUES (?, ?, ?::uuid, ?, ?, 'ACTIVE', ?, ?, ?, ?)
            """,
                breakGlassId, tenantId, request.facility_id(), request.activated_by(),
                request.reason(), now, expiresAt, now, now);

        outboxService.writeOutboxEvent(
                "impilo.experience.break_glass.activated.v1",
                correlationId,
                requestId,
                idempotencyKey != null ? idempotencyKey : requestId,
                tenantId,
                podId,
                "BreakGlassSession",
                breakGlassId.toString(),
                Map.of(
                        "break_glass_id", breakGlassId.toString(),
                        "facility_id", request.facility_id(),
                        "activated_by", request.activated_by(),
                        "reason", request.reason(),
                        "status", "ACTIVE",
                        "duration_minutes", durationMinutes
                ),
                Map.of()
        );

        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("facility_id", request.facility_id());
        attributes.put("activated_by", request.activated_by());
        attributes.put("reason", request.reason());
        attributes.put("status", "ACTIVE");
        attributes.put("activated_at", now);
        attributes.put("expires_at", expiresAt);
        attributes.put("duration_minutes", durationMinutes);
        attributes.put("created_at", now);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", Map.of(
                "id", breakGlassId.toString(),
                "type", "BreakGlassSession",
                "attributes", attributes
        ));
        response.put("meta", Map.of(
                "request_id", requestId,
                "correlation_id", correlationId
        ));

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/break-glass/deactivate")
    @Transactional
    public ResponseEntity<Map<String, Object>> deactivateBreakGlass(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.POD_ID) String podId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @Valid @RequestBody DeactivateBreakGlassRequest request) {

        OffsetDateTime now = OffsetDateTime.now();

        List<Map<String, Object>> activeRows = jdbcTemplate.queryForList("""
            SELECT id FROM break_glass_sessions
            WHERE tenant_id = ? AND facility_id = ?::uuid AND status = 'ACTIVE'
            ORDER BY activated_at DESC
            LIMIT 1
            """, tenantId, request.facility_id());

        if (activeRows.isEmpty()) {
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("data", Map.of("deactivated", false, "reason", "No active break-glass session found"));
            response.put("meta", Map.of(
                    "request_id", requestId,
                    "correlation_id", correlationId
            ));
            return ResponseEntity.ok(response);
        }

        UUID sessionId = (UUID) activeRows.get(0).get("id");

        jdbcTemplate.update("""
            UPDATE break_glass_sessions
            SET status = 'DEACTIVATED', deactivated_by = ?, deactivated_at = ?,
                notes = ?, updated_at = ?
            WHERE id = ? AND tenant_id = ?
            """, request.deactivated_by(), now, request.notes(), now, sessionId, tenantId);

        outboxService.writeOutboxEvent(
                "impilo.experience.break_glass.deactivated.v1",
                correlationId,
                requestId,
                idempotencyKey != null ? idempotencyKey : requestId,
                tenantId,
                podId,
                "BreakGlassSession",
                sessionId.toString(),
                Map.of(
                        "break_glass_id", sessionId.toString(),
                        "facility_id", request.facility_id(),
                        "deactivated_by", request.deactivated_by(),
                        "status", "DEACTIVATED"
                ),
                Map.of()
        );

        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("facility_id", request.facility_id());
        attributes.put("deactivated_by", request.deactivated_by());
        attributes.put("status", "DEACTIVATED");
        attributes.put("deactivated_at", now);
        attributes.put("notes", request.notes());

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", Map.of(
                "id", sessionId.toString(),
                "type", "BreakGlassSession",
                "attributes", attributes
        ));
        response.put("meta", Map.of(
                "request_id", requestId,
                "correlation_id", correlationId
        ));

        return ResponseEntity.ok(response);
    }
}
