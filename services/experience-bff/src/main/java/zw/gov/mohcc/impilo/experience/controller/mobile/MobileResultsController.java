package zw.gov.mohcc.impilo.experience.controller.mobile;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.OrosServiceClient;
import zw.gov.mohcc.impilo.experience.controller.ResourceNotFoundException;

import java.util.*;

/**
 * Mobile provider lab results endpoints.
 * GET  /internal/v1/mobile/provider/labs/results              - list resulted lab orders for facility
 * POST /internal/v1/mobile/provider/labs/results/{id}/acknowledge - acknowledge a result
 *
 * <p>STRANGLER: JdbcTemplate retained for local reads during migration; writes delegated to OrosServiceClient.</p>
 */
@RestController
@RequestMapping("/internal/v1/mobile/provider/labs/results")
public class MobileResultsController {

    private final OrosServiceClient orosClient;

        this.orosClient = orosClient;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> listResultedOrders(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader("X-Actor-ID") String actorId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        throw new UnsupportedOperationException("Endpoint pending migration to sovereign service");
    }

    @PostMapping("/{id}/acknowledge")
    public ResponseEntity<Map<String, Object>> acknowledgeResult(
            @PathVariable UUID id,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.POD_ID) String podId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @RequestHeader("X-Actor-ID") String actorId) {
        throw new UnsupportedOperationException("Endpoint pending migration to sovereign service");
    }

    private Map<String, Object> toResource(Map<String, Object> row) {
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("encounter_id", row.get("encounter_id"));
        attributes.put("patient_id", row.get("patient_id"));
        attributes.put("test_code", row.get("test_code"));
        attributes.put("test_name", row.get("test_name"));
        attributes.put("priority", row.get("priority"));
        attributes.put("clinical_notes", row.get("clinical_notes"));
        attributes.put("specimen_type", row.get("specimen_type"));
        attributes.put("status", row.get("status"));
        attributes.put("result_value", row.get("result_value"));
        attributes.put("result_unit", row.get("result_unit"));
        attributes.put("result_reference_range", row.get("result_reference_range"));
        attributes.put("result_interpretation", row.get("result_interpretation"));
        attributes.put("ordered_at", row.get("ordered_at"));
        attributes.put("collected_at", row.get("collected_at"));
        attributes.put("resulted_at", row.get("resulted_at"));
        attributes.put("created_at", row.get("created_at"));
        attributes.put("updated_at", row.get("updated_at"));

        Map<String, Object> resource = new LinkedHashMap<>();
        resource.put("id", row.get("id").toString());
        resource.put("type", "LabResult");
        resource.put("attributes", attributes);
        return resource;
    }
}
