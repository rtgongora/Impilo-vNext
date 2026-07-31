package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.OrosServiceClient;

import java.util.List;
import java.util.Map;

/**
 * Ambulatory order sets — BFF places real OROS orders (not CKP pathway-apply).
 */
@RestController
@RequestMapping("/internal/v1/order-sets")
public class AmbulatoryOrderSetController {

    private static final Logger log = LoggerFactory.getLogger(AmbulatoryOrderSetController.class);

    private final OrosServiceClient orosClient;

    public AmbulatoryOrderSetController(OrosServiceClient orosClient) {
        this.orosClient = orosClient;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> list(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            JsonNode data = orosClient.listOrderSets();
            return ResponseEntity.ok(Map.of(
                    "data", data != null ? data : List.of(),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception e) {
            log.error("OROS listOrderSets failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                    "error", "order_sets_unavailable",
                    "message", "Ambulatory order sets could not be retrieved.",
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        }
    }

    @PostMapping("/{setId}/place")
    public ResponseEntity<Map<String, Object>> place(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @PathVariable String setId,
            @RequestBody Map<String, Object> body) {
        try {
            JsonNode data = orosClient.placeOrderSet(setId, body);
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                    "data", data != null ? data : Map.of(),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception e) {
            log.error("OROS placeOrderSet failed for set={}: {}", setId, e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                    "error", "order_set_place_failed",
                    "message", "Order set was not placed. No pathway session was started.",
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        }
    }
}
