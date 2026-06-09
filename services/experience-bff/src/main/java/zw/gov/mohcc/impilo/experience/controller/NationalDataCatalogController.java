package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.NdrServiceClient;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * BFF proxy for national-data-repository-service dataset catalog and governed query API.
 * Complements {@link NdrController} which targets ndr-service bronze/gold query surfaces.
 */
@RestController
@RequestMapping("/internal/v1/ndr-catalog")
public class NationalDataCatalogController {

    private static final Logger log = LoggerFactory.getLogger(NationalDataCatalogController.class);

    private final NdrServiceClient ndrCatalogClient;

    public NationalDataCatalogController(NdrServiceClient ndrCatalogClient) {
        this.ndrCatalogClient = ndrCatalogClient;
    }

    @GetMapping("/datasets")
    public ResponseEntity<Map<String, Object>> listDatasets(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return proxy(ndrCatalogClient::listDatasets, requestId, correlationId);
    }

    @PostMapping("/datasets")
    public ResponseEntity<Map<String, Object>> createDataset(
            @RequestBody Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            JsonNode data = ndrCatalogClient.createDataset(body);
            return ResponseEntity.status(HttpStatus.CREATED).body(wrap(data, requestId, correlationId));
        } catch (Exception e) {
            log.warn("NDR catalog create dataset failed: {}", e.getMessage());
            return upstreamFailure(e.getMessage(), requestId, correlationId);
        }
    }

    @PostMapping("/datasets/{datasetKey}/versions")
    public ResponseEntity<Map<String, Object>> createDatasetVersion(
            @PathVariable String datasetKey,
            @RequestBody Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            JsonNode data = ndrCatalogClient.createDatasetVersion(datasetKey, body);
            return ResponseEntity.status(HttpStatus.CREATED).body(wrap(data, requestId, correlationId));
        } catch (Exception e) {
            log.warn("NDR catalog create version failed: {}", e.getMessage());
            return upstreamFailure(e.getMessage(), requestId, correlationId);
        }
    }

    @PostMapping("/query")
    public ResponseEntity<Map<String, Object>> executeQuery(
            @RequestBody Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return proxy(() -> ndrCatalogClient.executeQuery(body), requestId, correlationId);
    }

    private ResponseEntity<Map<String, Object>> proxy(
            java.util.function.Supplier<JsonNode> supplier,
            String requestId,
            String correlationId) {
        try {
            return ResponseEntity.ok(wrap(supplier.get(), requestId, correlationId));
        } catch (Exception e) {
            log.warn("NDR catalog proxy failed: {}", e.getMessage());
            return upstreamFailure(e.getMessage(), requestId, correlationId);
        }
    }

    private static Map<String, Object> wrap(JsonNode data, String requestId, String correlationId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("data", data != null ? data : Map.of());
        body.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
        return body;
    }

    private static ResponseEntity<Map<String, Object>> upstreamFailure(
            String message, String requestId, String correlationId) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                "error", Map.of("code", "NDR_CATALOG_UNAVAILABLE", "message", message),
                "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
    }
}
