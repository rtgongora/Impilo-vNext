package zw.gov.mohcc.impilo.experience.controller.mobile;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.TshepoOfflineServiceClient;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.OffsetDateTime;
import java.util.*;

/**
 * Mobile offline-support endpoints.
 * POST /internal/v1/mobile/provider/offline/entitlement/verify      - verify patient entitlement
 * POST /internal/v1/mobile/provider/offline/break-glass/activate    - activate break-glass
 * POST /internal/v1/mobile/provider/offline/break-glass/deactivate  - deactivate break-glass
 * GET  /internal/v1/mobile/provider/offline/sync/snapshot           - get sync snapshot
 * POST /internal/v1/mobile/provider/offline/sync/reconcile          - reconcile sync data
 *
 * <p>STRANGLER: JdbcTemplate retained for local reads during migration; writes delegated to TshepoOfflineServiceClient.</p>
 */
@RestController
@RequestMapping("/internal/v1/mobile/provider/offline")
public class MobileOfflineController {

    private final ObjectMapper objectMapper;
    private final TshepoOfflineServiceClient tshepoOfflineClient;

        this.objectMapper = objectMapper;
        this.tshepoOfflineClient = tshepoOfflineClient;
    }

    public record VerifyEntitlementRequest(
            @NotBlank String cpid,
            String facility_id,
            String service_type
    ) {}

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

    public record ReconcileRequest(
            @NotNull List<Map<String, Object>> records,
            @NotBlank String facility_id,
            String sync_token
    ) {}

    @PostMapping("/entitlement/verify")
    public ResponseEntity<Map<String, Object>> verifyEntitlement(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @Valid @RequestBody VerifyEntitlementRequest request) {

        if (patientRows.isEmpty()) {
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("data", Map.of(
                    "entitled", false,
                    "cpid", request.cpid(),
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

        boolean hasEntitlement = !entitlementRows.isEmpty();

        Map<String, Object> entitlementData = new LinkedHashMap<>();
        entitlementData.put("entitled", isActive && hasEntitlement);
        entitlementData.put("cpid", request.cpid());
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
    public ResponseEntity<Map<String, Object>> deactivateBreakGlass(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.POD_ID) String podId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @Valid @RequestBody DeactivateBreakGlassRequest request) {

        OffsetDateTime now = OffsetDateTime.now();

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

    @GetMapping("/sync/snapshot")
    public ResponseEntity<Map<String, Object>> getSyncSnapshot(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam(name = "facility_id") String facilityId,
            @RequestParam(required = false, name = "since") String since) {
        throw new UnsupportedOperationException("Endpoint pending migration to sovereign service");
    }

    @PostMapping("/sync/reconcile")
    public ResponseEntity<Map<String, Object>> reconcileSync(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.POD_ID) String podId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @Valid @RequestBody ReconcileRequest request) {

        OffsetDateTime now = OffsetDateTime.now();
        UUID reconciliationId = UUID.randomUUID();
        int accepted = 0;
        int rejected = 0;
        List<Map<String, Object>> results = new ArrayList<>();

        for (Map<String, Object> record : request.records()) {
            try {
                UUID recordId = UUID.randomUUID();
                String recordJson = objectMapper.writeValueAsString(record);
                accepted++;
                results.add(Map.of(
                        "entity_id", record.getOrDefault("entity_id", ""),
                        "status", "ACCEPTED"
                ));
            } catch (Exception e) {
                rejected++;
                results.add(Map.of(
                        "entity_id", record.getOrDefault("entity_id", ""),
                        "status", "REJECTED",
                        "reason", e.getMessage() != null ? e.getMessage() : "Unknown error"
                ));
            }
        }

        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("facility_id", request.facility_id());
        attributes.put("accepted", accepted);
        attributes.put("rejected", rejected);
        attributes.put("total", request.records().size());
        attributes.put("results", results);
        attributes.put("reconciled_at", now);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", Map.of(
                "id", reconciliationId.toString(),
                "type", "SyncReconciliation",
                "attributes", attributes
        ));
        response.put("meta", Map.of(
                "request_id", requestId,
                "correlation_id", correlationId
        ));

        return ResponseEntity.ok(response);
    }
}
