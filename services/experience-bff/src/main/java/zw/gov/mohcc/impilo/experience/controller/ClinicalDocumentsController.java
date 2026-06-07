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
            JsonNode data = pctClient.getPatientRecords(patientId, null, page, size);
            return ResponseEntity.ok(Map.of(
                    "data", data != null ? data : List.of(),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception e) {
            log.warn("PCT getPatientRecords failed: {}", e.getMessage());
            return ResponseEntity.ok(Map.of(
                    "data", List.of(),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        }
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

        JsonNode created = pctClient.createPatientRecord(body);

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
                "data", created != null ? created : Map.of(),
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
}
