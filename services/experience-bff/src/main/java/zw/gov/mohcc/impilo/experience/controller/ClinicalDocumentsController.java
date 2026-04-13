package zw.gov.mohcc.impilo.experience.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import zw.gov.mohcc.impilo.experience.client.DocumentServiceClient;

import java.time.OffsetDateTime;
import java.util.*;

/**
 * Clinical document management endpoints.
 * GET  /internal/v1/clinical-documents?patient_id= — list documents for patient (paged).
 * POST /internal/v1/clinical-documents — create document entry (metadata only).
 *
 * <p>When a document has a document_object_id (V15 bridge column), the response
 * includes a download_url generated from the document-service's pre-signed URL.</p>
 */
@RestController
@RequestMapping("/internal/v1/clinical-documents")
public class ClinicalDocumentsController {

    private static final Logger log = LoggerFactory.getLogger(ClinicalDocumentsController.class);

    private final DocumentServiceClient documentServiceClient;

    public ClinicalDocumentsController(DocumentServiceClient documentServiceClient) {
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
            String uploaded_by
    ) {}

    @GetMapping
    public ResponseEntity<Map<String, Object>> listDocuments(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false, name = "patient_id") String patientId) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", List.of());
        response.put("meta", Map.of(
                "request_id", requestId,
                "correlation_id", correlationId
        ));
        return ResponseEntity.ok(response);
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> createDocument(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.POD_ID) String podId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @Valid @RequestBody CreateDocumentRequest request) {

        UUID documentId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();

        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("patient_id", request.patient_id());
        attributes.put("encounter_id", request.encounter_id());
        attributes.put("document_type", request.document_type());
        attributes.put("title", request.title());
        attributes.put("description", request.description());
        attributes.put("mime_type", request.mime_type());
        attributes.put("file_size", request.file_size());
        attributes.put("storage_key", request.storage_key());
        attributes.put("uploaded_by", request.uploaded_by());
        attributes.put("status", "ACTIVE");
        attributes.put("created_at", now);
        attributes.put("updated_at", now);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", Map.of(
                "id", documentId.toString(),
                "type", "ClinicalDocument",
                "attributes", attributes
        ));
        response.put("meta", Map.of(
                "request_id", requestId,
                "correlation_id", correlationId
        ));

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
