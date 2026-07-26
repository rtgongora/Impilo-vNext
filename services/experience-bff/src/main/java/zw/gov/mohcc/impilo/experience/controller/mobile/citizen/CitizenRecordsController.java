package zw.gov.mohcc.impilo.experience.controller.mobile.citizen;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.server.ResponseStatusException;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.DocumentServiceClient;
import zw.gov.mohcc.impilo.experience.client.PctServiceClient;

import java.util.*;

/**
 * Citizen clinical records — a person reading their own document index.
 *
 * <p>The list and detail reads pass the caller's own actor id to pct as the subject binding, so
 * this surface can never serve another person's record: pct answers 404 on a subject mismatch
 * without disclosing that the id exists. Rows are the flat shape the citizen app's
 * {@code MedicalRecord} type reads, and the list carries real {@code meta.page} numbers — the
 * app reads {@code meta.page.total_elements} unguarded.</p>
 */
@RestController
@RequestMapping("/internal/v1/mobile/citizen/records")
public class CitizenRecordsController {

    private static final Logger log = LoggerFactory.getLogger(CitizenRecordsController.class);

    private final PctServiceClient pctClient;
    private final DocumentServiceClient documentServiceClient;

    public CitizenRecordsController(PctServiceClient pctClient,
                                    DocumentServiceClient documentServiceClient) {
        this.pctClient = pctClient;
        this.documentServiceClient = documentServiceClient;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> list(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader("X-Actor-ID") String actorId,
            @RequestParam(required = false) String documentType,
            @RequestParam(required = false) String type,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        int limit = Math.max(1, Math.min(size, 100));
        String typeFilter = documentType != null && !documentType.isBlank() ? documentType : type;
        try {
            JsonNode documents = pctClient.getPatientRecords(actorId, typeFilter);
            List<Map<String, Object>> rows = new ArrayList<>();
            if (documents != null && documents.isArray()) {
                documents.forEach(doc -> rows.add(recordRow(doc, null)));
            }
            int from = Math.min(Math.max(page, 0) * limit, rows.size());
            int to = Math.min(from + limit, rows.size());

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("data", rows.subList(from, to));
            response.put("meta", Map.of(
                    "request_id", requestId,
                    "correlation_id", correlationId,
                    "page", Map.of(
                            "number", page,
                            "size", limit,
                            "total_elements", rows.size(),
                            "total_pages", (rows.size() + limit - 1) / limit)));
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            // "No records found" to a person about their own medical history is an affirmative
            // claim. When the truth is unreachable, say so; the app renders a retryable error.
            log.error("PCT getPatientRecords failed for citizen actor={}: {}", actorId, e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                    "error", "records_unavailable",
                    "message", "Your records could not be retrieved. Do not treat this as an "
                               + "absence of records.",
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> get(
            @PathVariable UUID id,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader("X-Actor-ID") String actorId) {

        JsonNode document;
        try {
            document = pctClient.getPatientRecord(id.toString(), actorId);
        } catch (HttpClientErrorException.NotFound e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "record not found");
        }

        // Detail read gets a short-lived download link when the binary is registered. Advisory
        // enrichment: its failure hides the link, never the record.
        String documentUrl = null;
        String objectId = document != null && document.hasNonNull("document_object_id")
                ? document.get("document_object_id").asText() : null;
        if (objectId != null && !objectId.isBlank()) {
            try {
                JsonNode signed = documentServiceClient.getSignedUrl(UUID.fromString(objectId));
                if (signed != null && signed.hasNonNull("url")) {
                    documentUrl = signed.get("url").asText();
                }
            } catch (Exception e) {
                log.warn("Signed-URL enrichment failed for object {}: {}", objectId, e.getMessage());
            }
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", document != null ? recordRow(document, documentUrl) : Map.of());
        response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
        return ResponseEntity.ok(response);
    }

    /** pct document → the flat {@code MedicalRecord} row the citizen app reads. */
    private static Map<String, Object> recordRow(JsonNode doc, String documentUrl) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", text(doc, "document_id"));
        row.put("title", text(doc, "title"));
        row.put("type", text(doc, "document_type"));
        row.put("date", text(doc, "recorded_at"));
        row.put("provider", text(doc, "uploaded_by"));
        // No facility-name resolution exists on this path; null renders blank, a UUID would lie.
        row.put("facilityName", null);
        row.put("summary", text(doc, "description"));
        row.put("documentUrl", documentUrl);
        row.put("createdAt", text(doc, "recorded_at"));
        return row;
    }

    private static String text(JsonNode node, String key) {
        return node.hasNonNull(key) && !node.get(key).asText().isBlank() ? node.get(key).asText() : null;
    }
}
