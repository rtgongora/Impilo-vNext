package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpStatusCodeException;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.ClinicalKnowledgePlatformClient;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * BFF proxy for clinical-knowledge-platform PDF → {@code source_sections} ingestion.
 * Restricted to admin-equivalent roles in {@link zw.gov.mohcc.impilo.experience.config.SecurityConfig}.
 */
@RestController
@RequestMapping("/internal/v1/clinical/source")
public class ClinicalSourceIngestionController {

    private static final Logger log = LoggerFactory.getLogger(ClinicalSourceIngestionController.class);

    private final ClinicalKnowledgePlatformClient clinicalClient;

    public ClinicalSourceIngestionController(ClinicalKnowledgePlatformClient clinicalClient) {
        this.clinicalClient = clinicalClient;
    }

    @PostMapping("/documents/{documentId}/ingest-pdf")
    public ResponseEntity<Map<String, Object>> ingestPdf(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @PathVariable UUID documentId,
            @RequestBody(required = false) Map<String, Object> body) {
        try {
            JsonNode data = clinicalClient.ingestPdfSections(documentId, body);
            return ResponseEntity.ok(Map.of("data", data != null ? data : Map.of()));
        } catch (HttpStatusCodeException e) {
            log.warn("Clinical PDF ingestion upstream {}: {}", e.getStatusCode(), e.getResponseBodyAsString());
            return ResponseEntity.status(e.getStatusCode()).body(errorBody(e));
        } catch (Exception e) {
            log.error("Clinical PDF ingestion failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(Map.of("error", "clinical_platform_unreachable", "detail", String.valueOf(e.getMessage())));
        }
    }

    @GetMapping("/documents/{documentId}/ingestion-summary")
    public ResponseEntity<Map<String, Object>> summary(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @PathVariable UUID documentId) {
        try {
            JsonNode data = clinicalClient.sourceIngestionSummary(documentId);
            return ResponseEntity.ok(Map.of("data", data != null ? data : Map.of()));
        } catch (HttpStatusCodeException e) {
            return ResponseEntity.status(e.getStatusCode()).body(errorBody(e));
        } catch (Exception e) {
            log.error("Clinical ingestion summary failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(Map.of("error", "clinical_platform_unreachable", "detail", String.valueOf(e.getMessage())));
        }
    }

    @GetMapping("/edliz-default-document-id")
    public ResponseEntity<Map<String, Object>> defaultEdlizDocumentId(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId) {
        try {
            JsonNode data = clinicalClient.defaultEdlizSourceDocumentId();
            return ResponseEntity.ok(Map.of("data", data != null ? data : Map.of()));
        } catch (HttpStatusCodeException e) {
            return ResponseEntity.status(e.getStatusCode()).body(errorBody(e));
        } catch (Exception e) {
            log.error("Clinical default EDLIZ document id failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(Map.of("error", "clinical_platform_unreachable", "detail", String.valueOf(e.getMessage())));
        }
    }

    private static Map<String, Object> errorBody(HttpStatusCodeException e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("status", e.getStatusCode().value());
        m.put("detail", e.getResponseBodyAsString());
        return m;
    }
}
