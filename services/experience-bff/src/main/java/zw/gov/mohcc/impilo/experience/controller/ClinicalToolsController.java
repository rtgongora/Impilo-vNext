package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.TshepoOfflineServiceClient;
import zw.gov.mohcc.impilo.experience.config.ServiceClientConfig;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Clinical Tools BFF Controller — bridges offline sync, document
 * management (Landela), and clinical productivity tools.
 */
@RestController
@RequestMapping("/internal/v1/clinical-tools")
public class ClinicalToolsController {

    private static final Logger log = LoggerFactory.getLogger(ClinicalToolsController.class);
    private final RestTemplate restTemplate;
    private final String documentStoreUrl;
    private final TshepoOfflineServiceClient tshepoOfflineClient;

    public ClinicalToolsController(
            RestTemplate serviceRestTemplate,
            ServiceClientConfig.ServiceEndpoints endpoints,
            TshepoOfflineServiceClient tshepoOfflineClient) {
        this.restTemplate = serviceRestTemplate;
        this.documentStoreUrl = endpoints.documentStoreBaseUrl();
        this.tshepoOfflineClient = tshepoOfflineClient;
    }

    // ── Document Management (Landela bridge) ─────────────────────

    @GetMapping("/documents")
    public ResponseEntity<Map<String, Object>> listDocuments(
            @RequestParam(required = false, name = "patient_id") String patientId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId) {
        try {
            String url = documentStoreUrl + "/v1/internal/objects";
            JsonNode result = restTemplate.getForEntity(url, JsonNode.class).getBody();
            Object data = result != null && result.has("data") ? result.get("data") : (result != null ? result : new Object[0]);
            return ResponseEntity.ok(Map.of("data", data, "meta", Map.of("request_id", requestId)));
        } catch (Exception e) {
            log.warn("Document list failed: {}", e.getMessage());
            return ResponseEntity.ok(Map.of("data", new Object[0], "meta", Map.of("request_id", requestId)));
        }
    }

    @GetMapping("/documents/{id}/download-url")
    public ResponseEntity<Map<String, Object>> getDocumentDownloadUrl(
            @PathVariable String id,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId) {
        try {
            String url = documentStoreUrl + "/v1/internal/objects/" + id + "/signed-url";
            JsonNode result = restTemplate.getForEntity(url, JsonNode.class).getBody();
            return ResponseEntity.ok(Map.of("data", result != null ? result : Map.of(), "meta", Map.of("request_id", requestId)));
        } catch (Exception e) {
            return ResponseEntity.status(404).body(Map.of("error", Map.of("code", "NOT_FOUND")));
        }
    }

    // ── Offline Sync Status ──────────────────────────────────────

    @GetMapping("/sync/status")
    public ResponseEntity<Map<String, Object>> getSyncStatus(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId) {
        return ResponseEntity.ok(Map.of(
                "data", Map.of(
                        "syncEngine", "available",
                        "autoSyncInterval", 30000,
                        "conflictResolution", "user_prompted",
                        "offlineCapable", true,
                        "lastSyncAt", "check_client_storage"
                ),
                "meta", Map.of("request_id", requestId)));
    }

    @GetMapping("/offline/queue")
    public ResponseEntity<Map<String, Object>> getOfflineQueue(
            @RequestParam(name = "facility_id") String facilityId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("facility_id", facilityId);
        data.put("offlineCapable", true);
        data.put("conflictResolution", "user_prompted");
        try {
            JsonNode pack = tshepoOfflineClient.getLatestOfflinePackForFacility(UUID.fromString(facilityId));
            if (pack != null && pack.isArray()) {
                data.put("queue_depth", pack.size());
                data.put("items", pack);
            } else if (pack != null) {
                data.put("queue_depth", 1);
                data.put("items", List.of(pack));
            } else {
                data.put("queue_depth", 0);
                data.put("items", List.of());
            }
            data.put("source", "tshepo-offline-pack");
        } catch (Exception e) {
            log.warn("Offline queue probe failed for facility {}: {}", facilityId, e.getMessage());
            data.put("queue_depth", 0);
            data.put("items", List.of());
            data.put("source", "unavailable");
            data.put("message", e.getMessage());
        }
        return ResponseEntity.ok(Map.of(
                "data", data,
                "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
    }

    @GetMapping("/offline/reconcile/pending")
    public ResponseEntity<Map<String, Object>> listPendingOfflineReconcile(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            JsonNode pending = tshepoOfflineClient.listPendingOfflineReconcileBatches();
            return ResponseEntity.ok(Map.of(
                    "data", pending != null ? pending : List.of(),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception e) {
            log.warn("Offline reconcile pending list failed: {}", e.getMessage());
            return ResponseEntity.ok(Map.of(
                    "data", List.of(),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        }
    }

    @PostMapping("/offline/reconcile")
    public ResponseEntity<Map<String, Object>> submitOfflineReconcile(
            @RequestBody Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            JsonNode result = tshepoOfflineClient.submitOfflineReconcileBatch(body);
            return ResponseEntity.status(org.springframework.http.HttpStatus.ACCEPTED).body(Map.of(
                    "data", result != null ? result : Map.of(),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception e) {
            log.warn("Offline reconcile submit failed: {}", e.getMessage());
            return ResponseEntity.status(org.springframework.http.HttpStatus.BAD_GATEWAY).body(Map.of(
                    "error", Map.of("code", "OFFLINE_RECONCILE_UNAVAILABLE", "message", e.getMessage()),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        }
    }

    @GetMapping("/offline/reconcile/{batchId}")
    public ResponseEntity<Map<String, Object>> getOfflineReconcileStatus(
            @PathVariable UUID batchId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            JsonNode status = tshepoOfflineClient.getOfflineReconcileBatchStatus(batchId);
            return ResponseEntity.ok(Map.of(
                    "data", status != null ? status : Map.of(),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception e) {
            log.warn("Offline reconcile status failed for {}: {}", batchId, e.getMessage());
            return ResponseEntity.status(org.springframework.http.HttpStatus.BAD_GATEWAY).body(Map.of(
                    "error", Map.of("code", "OFFLINE_RECONCILE_UNAVAILABLE", "message", e.getMessage()),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        }
    }
}
