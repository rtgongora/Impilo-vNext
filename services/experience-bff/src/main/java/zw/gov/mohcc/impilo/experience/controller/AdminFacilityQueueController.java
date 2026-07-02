package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpStatusCodeException;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.PctServiceClient;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Admin proxy for PCT queue materialisation visibility. Surfaces whether a facility's queues are
 * materialised from TUSO or seed/demo, and lets authorised admins trigger an on-demand reconcile.
 *
 * <p><b>Policy:</b> routes sit under {@code /internal/v1/admin/**}, restricted to admin roles by
 * {@code SecurityConfig}. The BFF reports exactly what PCT returns (no fabricated status) and PCT owns
 * the materialisation logic. Queue definitions are owned by TUSO facility service-point/workspace
 * configuration and materialised into PCT for care operations — this is not a queue editor.</p>
 */
@RestController
@RequestMapping("/internal/v1/admin/facilities")
public class AdminFacilityQueueController {

    private static final Logger log = LoggerFactory.getLogger(AdminFacilityQueueController.class);

    private final PctServiceClient pctClient;

    public AdminFacilityQueueController(PctServiceClient pctClient) {
        this.pctClient = pctClient;
    }

    @GetMapping("/{facilityId}/queues/materialization-status")
    public ResponseEntity<Map<String, Object>> materializationStatus(
            @PathVariable UUID facilityId,
            @RequestHeader(value = CompanionHeaders.REQUEST_ID, required = false) String requestId,
            @RequestHeader(value = CompanionHeaders.CORRELATION_ID, required = false) String correlationId) {
        try {
            JsonNode data = pctClient.getQueueMaterializationStatus(facilityId);
            return ResponseEntity.ok(Map.of("data", data, "meta", meta(requestId, correlationId)));
        } catch (HttpStatusCodeException e) {
            return passthrough(e, requestId, correlationId);
        } catch (Exception e) {
            return badGateway(requestId, correlationId, "QUEUE_STATUS_UNAVAILABLE",
                    "Unable to load queue materialisation status from PCT");
        }
    }

    @GetMapping("/{facilityId}/queues")
    public ResponseEntity<Map<String, Object>> queues(
            @PathVariable UUID facilityId,
            @RequestHeader(value = CompanionHeaders.REQUEST_ID, required = false) String requestId,
            @RequestHeader(value = CompanionHeaders.CORRELATION_ID, required = false) String correlationId) {
        try {
            JsonNode data = pctClient.listQueues(facilityId, null);
            return ResponseEntity.ok(Map.of("data", data, "meta", meta(requestId, correlationId)));
        } catch (HttpStatusCodeException e) {
            return passthrough(e, requestId, correlationId);
        } catch (Exception e) {
            return badGateway(requestId, correlationId, "QUEUES_UNAVAILABLE",
                    "Unable to load queues from PCT");
        }
    }

    @PostMapping("/{facilityId}/queues/reconcile")
    public ResponseEntity<Map<String, Object>> reconcile(
            @PathVariable UUID facilityId,
            @RequestHeader(value = CompanionHeaders.REQUEST_ID, required = false) String requestId,
            @RequestHeader(value = CompanionHeaders.CORRELATION_ID, required = false) String correlationId) {
        try {
            JsonNode data = pctClient.reconcileQueues(facilityId);
            return ResponseEntity.ok(Map.of("data", data, "meta", meta(requestId, correlationId)));
        } catch (HttpStatusCodeException e) {
            return passthrough(e, requestId, correlationId);
        } catch (Exception e) {
            return badGateway(requestId, correlationId, "QUEUE_RECONCILE_FAILED",
                    "Unable to reconcile queues via PCT (TUSO may be unreachable)");
        }
    }

    private static Map<String, Object> meta(String requestId, String correlationId) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("request_id", requestId);
        meta.put("correlation_id", correlationId);
        return meta;
    }

    private ResponseEntity<Map<String, Object>> passthrough(
            HttpStatusCodeException e, String requestId, String correlationId) {
        String message = e.getResponseBodyAsString();
        return ResponseEntity.status(e.getStatusCode()).body(Map.of(
                "error", Map.of("code", "PCT_QUEUE_ERROR",
                        "message", message == null || message.isBlank() ? e.getStatusText() : message),
                "meta", meta(requestId, correlationId)));
    }

    private ResponseEntity<Map<String, Object>> badGateway(
            String requestId, String correlationId, String code, String message) {
        log.warn("PCT queue admin proxy failure [{}]: {}", code, message);
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                "error", Map.of("code", code, "message", message),
                "meta", meta(requestId, correlationId)));
    }
}
