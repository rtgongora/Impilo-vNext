package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.config.ServiceClientConfig;

import java.util.Map;

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

    public ClinicalToolsController(RestTemplate serviceRestTemplate, ServiceClientConfig.ServiceEndpoints endpoints) {
        this.restTemplate = serviceRestTemplate;
        this.documentStoreUrl = endpoints.documentStoreBaseUrl();
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
        // Return client-side sync status info
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
}
