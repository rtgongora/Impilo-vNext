package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.DocumentServiceClient;
import zw.gov.mohcc.impilo.experience.client.PctServiceClient;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Clinical documents — list/create via PCT records; optional document-store enrichment for object metadata.
 */
@RestController
@RequestMapping("/internal/v1/clinical-documents")
public class ClinicalDocumentsController {

    private static final Logger log = LoggerFactory.getLogger(ClinicalDocumentsController.class);

    private final PctServiceClient pctClient;
    private final DocumentServiceClient documentServiceClient;

    public ClinicalDocumentsController(PctServiceClient pctClient,
                                       DocumentServiceClient documentServiceClient) {
        this.pctClient = pctClient;
        this.documentServiceClient = documentServiceClient;
    }

    public record CreateDocumentRequest(
            @NotBlank String patient_id,
            String encounter_id,
            @NotBlank String document_type,
            @NotBlank String title,
            String description,
            String mime_type,
            Long file_size,
            @NotBlank String storage_key,
            String uploaded_by,
            String document_object_id
    ) {}

    @GetMapping
    public ResponseEntity<Map<String, Object>> listDocuments(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false, name = "patient_id") String patientId) {
        if (patientId == null || patientId.isBlank()) {
            return ResponseEntity.ok(Map.of(
                    "data", List.of(),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        }
        try {
            JsonNode data = pctClient.listPatientDocuments(patientId, null);
            List<Map<String, Object>> rows = new java.util.ArrayList<>();
            if (data != null && data.isArray()) {
                data.forEach(doc -> rows.add(documentRow(doc)));
            }
            int from = Math.min(Math.max(page, 0) * Math.max(size, 1), rows.size());
            int to = Math.min(from + Math.max(size, 1), rows.size());
            return ResponseEntity.ok(Map.of(
                    "data", rows.subList(from, to),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId,
                            "page", Map.of("number", page, "size", size,
                                    "total_elements", rows.size(),
                                    "total_pages", (rows.size() + Math.max(size, 1) - 1) / Math.max(size, 1)))));
        } catch (Exception e) {
            // An empty 200 here reads as "this patient has no records on file", which is an
            // affirmative finding and would be a fabricated one. Fail loudly instead.
            log.error("PCT listPatientDocuments failed for patient={}: {}", patientId, e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                    "error", "clinical_documents_unavailable",
                    "message", "Clinical documents could not be retrieved. Do not treat this as an "
                               + "absence of documents.",
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        }
    }

    /**
     * pct document → the JSON:API row the shell reads. Both dialects inside {@code attributes}
     * because the read hooks type camelCase while the create contract is snake_case, and neither
     * side has ever seen live data to prove a single spelling.
     */
    static Map<String, Object> documentRow(JsonNode doc) {
        Map<String, Object> attributes = new LinkedHashMap<>();
        putBoth(attributes, "patient_id", "patientId", text(doc, "patient_id"));
        putBoth(attributes, "encounter_id", "encounterId", text(doc, "encounter_id"));
        putBoth(attributes, "document_type", "documentType", text(doc, "document_type"));
        attributes.put("title", text(doc, "title"));
        attributes.put("description", text(doc, "description"));
        putBoth(attributes, "mime_type", "mimeType", text(doc, "mime_type"));
        putBoth(attributes, "file_size", "fileSize",
                doc.hasNonNull("file_size") ? doc.get("file_size").asLong() : null);
        putBoth(attributes, "storage_key", "storageKey", text(doc, "storage_key"));
        putBoth(attributes, "document_object_id", "documentObjectId", text(doc, "document_object_id"));
        putBoth(attributes, "uploaded_by", "uploadedBy", text(doc, "uploaded_by"));
        attributes.put("status", text(doc, "status"));
        putBoth(attributes, "created_at", "createdAt", text(doc, "recorded_at"));
        putBoth(attributes, "recorded_at", "recordedAt", text(doc, "recorded_at"));

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", text(doc, "document_id"));
        row.put("type", "clinical_document");
        row.put("attributes", attributes);
        return row;
    }

    private static void putBoth(Map<String, Object> attributes, String snake, String camel, Object value) {
        attributes.put(snake, value);
        attributes.put(camel, value);
    }

    private static String text(JsonNode node, String key) {
        return node.hasNonNull(key) && !node.get(key).asText().isBlank() ? node.get(key).asText() : null;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> createDocument(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.POD_ID) String podId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @Valid @RequestBody CreateDocumentRequest request) {

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("patient_id", request.patient_id());
        body.put("encounter_id", request.encounter_id());
        body.put("document_type", request.document_type());
        body.put("title", request.title());
        body.put("description", request.description());
        body.put("mime_type", request.mime_type());
        body.put("file_size", request.file_size());
        body.put("storage_key", request.storage_key());
        body.put("uploaded_by", request.uploaded_by());
        // The object id goes into the index too — it is what makes signed-URL enrichment
        // possible on later reads without guessing at storage keys.
        body.put("document_object_id", request.document_object_id());

        JsonNode created = pctClient.createPatientDocument(body);

        Map<String, Object> meta = new LinkedHashMap<>(Map.of(
                "request_id", requestId,
                "correlation_id", correlationId));

        if (request.document_object_id() != null && !request.document_object_id().isBlank()) {
            try {
                UUID oid = UUID.fromString(request.document_object_id().trim());
                JsonNode docMeta = documentServiceClient.getObjectMetadata(oid);
                if (docMeta != null) {
                    meta.put("document_object", docMeta);
                }
                JsonNode signed = documentServiceClient.getSignedUrl(oid);
                if (signed != null) {
                    meta.put("download", signed);
                }
            } catch (Exception e) {
                log.warn("Document store enrichment failed (non-blocking): {}", e.getMessage());
            }
        }

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "data", created != null ? documentRow(created) : Map.of(),
                "meta", meta));
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> uploadAndIndex(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.POD_ID) String podId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestPart("file") MultipartFile file,
            @RequestPart("patient_id") String patientId,
            @RequestPart("document_type") String documentType,
            @RequestPart("title") String title,
            @RequestPart(value = "description", required = false) String description,
            @RequestPart(value = "encounter_id", required = false) String encounterId,
            @RequestPart(value = "uploaded_by", required = false) String uploadedBy) throws java.io.IOException {

        String mimeType = file.getContentType() != null ? file.getContentType() : MediaType.APPLICATION_OCTET_STREAM_VALUE;
        String filename = file.getOriginalFilename() != null ? file.getOriginalFilename() : title;
        JsonNode stored = documentServiceClient.uploadObject(file.getBytes(), filename, mimeType);

        String objectId = stored != null && stored.has("id") ? stored.get("id").asText() : null;
        String storageKey = stored != null && stored.has("storageKey")
                ? stored.get("storageKey").asText()
                : (stored != null && stored.has("storage_key") ? stored.get("storage_key").asText() : filename);

        CreateDocumentRequest request = new CreateDocumentRequest(
                patientId,
                encounterId,
                documentType,
                title,
                description,
                mimeType,
                file.getSize(),
                storageKey,
                uploadedBy != null ? uploadedBy : "clinical-upload",
                objectId);

        return createDocument(tenantId, podId, requestId, correlationId, null, request);
    }

    @PostMapping("/objects/{objectId}/ocr")
    public ResponseEntity<Map<String, Object>> requestDocumentOcr(
            @PathVariable UUID objectId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            JsonNode job = documentServiceClient.requestOcr(objectId);
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of(
                    "data", job != null ? job : Map.of(),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception e) {
            log.warn("Document OCR request failed for {}: {}", objectId, e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                    "error", Map.of("code", "DOCUMENT_OCR_UNAVAILABLE", "message", e.getMessage()),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        }
    }

    @GetMapping("/objects/{objectId}/ocr")
    public ResponseEntity<Map<String, Object>> getDocumentOcrStatus(
            @PathVariable UUID objectId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            JsonNode job = documentServiceClient.getLatestOcr(objectId);
            return ResponseEntity.ok(Map.of(
                    "data", job != null ? job : Map.of(),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception e) {
            log.warn("Document OCR status failed for {}: {}", objectId, e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                    "error", Map.of("code", "DOCUMENT_OCR_UNAVAILABLE", "message", e.getMessage()),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        }
    }
}
