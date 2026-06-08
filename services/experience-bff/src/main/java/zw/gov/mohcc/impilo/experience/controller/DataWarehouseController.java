package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.DataWarehouseServiceClient;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * BFF proxy for data-warehouse-service gold datasets — complements {@link NdrController}.
 */
@RestController
@RequestMapping("/internal/v1/warehouse")
public class DataWarehouseController {

    private static final Logger log = LoggerFactory.getLogger(DataWarehouseController.class);

    private final DataWarehouseServiceClient warehouseClient;

    public DataWarehouseController(DataWarehouseServiceClient warehouseClient) {
        this.warehouseClient = warehouseClient;
    }

    @GetMapping("/gold/stats")
    public ResponseEntity<Map<String, Object>> goldStats(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return proxy(() -> warehouseClient.goldStats(), requestId, correlationId);
    }

    @GetMapping("/gold/datasets")
    public ResponseEntity<Map<String, Object>> listGoldDatasets(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return proxy(() -> warehouseClient.listGoldDatasets(), requestId, correlationId);
    }

    @GetMapping("/gold/query")
    public ResponseEntity<Map<String, Object>> queryGold(
            @RequestParam String dataset,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int limit,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return proxy(() -> warehouseClient.queryGold(dataset, page, limit), requestId, correlationId);
    }

    @PostMapping("/gold/materialize")
    public ResponseEntity<Map<String, Object>> materializeGold(
            @RequestBody Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            JsonNode data = warehouseClient.materialize(body);
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("data", data != null ? data : Map.of());
            response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
        } catch (Exception e) {
            log.warn("Warehouse materialize failed: {}", e.getMessage());
            return upstreamFailure(e.getMessage(), requestId, correlationId);
        }
    }

    private ResponseEntity<Map<String, Object>> proxy(
            java.util.function.Supplier<JsonNode> supplier,
            String requestId,
            String correlationId) {
        try {
            JsonNode data = supplier.get();
            return ResponseEntity.ok(Map.of(
                    "data", data != null ? data : Map.of(),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception e) {
            log.warn("Warehouse proxy failed: {}", e.getMessage());
            return upstreamFailure(e.getMessage(), requestId, correlationId);
        }
    }

    private ResponseEntity<Map<String, Object>> upstreamFailure(
            String message, String requestId, String correlationId) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                "error", Map.of("code", "WAREHOUSE_UNAVAILABLE", "message", message),
                "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
    }
}
