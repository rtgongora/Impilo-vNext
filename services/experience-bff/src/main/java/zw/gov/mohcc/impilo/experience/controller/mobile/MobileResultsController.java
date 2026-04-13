package zw.gov.mohcc.impilo.experience.controller.mobile;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.OrosServiceClient;

import java.util.*;

/**
 * Mobile provider lab results endpoints.
 * GET  /internal/v1/mobile/provider/labs/results              - list resulted lab orders for facility
 * POST /internal/v1/mobile/provider/labs/results/{id}/acknowledge - acknowledge a result
 */
@RestController
@RequestMapping("/internal/v1/mobile/provider/labs/results")
public class MobileResultsController {

    private final OrosServiceClient orosClient;

    public MobileResultsController(OrosServiceClient orosClient) {
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
        try {
            JsonNode data = orosClient.getPatientResults(actorId, page, size);
            if (data != null) {
                return ResponseEntity.ok(Map.of("data", data));
            }
        } catch (Exception ignored) {}
        return ResponseEntity.ok(Map.of("data", List.of()));
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
        try {
            JsonNode data = orosClient.acknowledgeOrder(id.toString(), "CLINICIAN", null);
            if (data != null) {
                return ResponseEntity.ok(Map.of("data", data));
            }
        } catch (Exception ignored) {}
        return ResponseEntity.ok(Map.of("data", List.of()));
    }
}
