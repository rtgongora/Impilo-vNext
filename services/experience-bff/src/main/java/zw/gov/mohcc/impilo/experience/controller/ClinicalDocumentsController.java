package zw.gov.mohcc.impilo.experience.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import zw.gov.mohcc.impilo.experience.client.DocumentServiceClient;
import zw.gov.mohcc.impilo.experience.domain.ClinicalDocument;
import zw.gov.mohcc.impilo.experience.repository.ClinicalDocumentRepository;
import zw.gov.mohcc.impilo.experience.service.OutboxService;

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

    private final ClinicalDocumentRepository clinicalDocumentRepository;
    private final OutboxService outboxService;
    private final JdbcTemplate jdbcTemplate;
    private final DocumentServiceClient documentServiceClient;

    public ClinicalDocumentsController(ClinicalDocumentRepository clinicalDocumentRepository,
                                       OutboxService outboxService,
                                       JdbcTemplate jdbcTemplate,
                                       DocumentServiceClient documentServiceClient) {
        this.clinicalDocumentRepository = clinicalDocumentRepository;
        this.outboxService = outboxService;
        this.jdbcTemplate = jdbcTemplate;
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

        PageRequest pageable = PageRequest.of(page, Math.min(size, 100), Sort.by("createdAt").descending());

        Page<ClinicalDocument> result;
        if (patientId != null) {
            result = clinicalDocumentRepository.findByTenantIdAndPatientId(tenantId, UUID.fromString(patientId), pageable);
        } else {
            result = clinicalDocumentRepository.findAll(pageable);
        }

        List<Map<String, Object>> data = result.getContent().stream()
                .map(this::toResource)
                .toList();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", data);
        response.put("meta", Map.of(
                "request_id", requestId,
                "correlation_id", correlationId,
                "page", Map.of(
                        "number", result.getNumber(),
                        "size", result.getSize(),
                        "total_elements", result.getTotalElements(),
                        "total_pages", result.getTotalPages()
                )
        ));

        return ResponseEntity.ok(response);
    }

    @PostMapping
    @Transactional
    public ResponseEntity<Map<String, Object>> createDocument(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.POD_ID) String podId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @Valid @RequestBody CreateDocumentRequest request) {

        UUID documentId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();

        jdbcTemplate.update("""
            INSERT INTO clinical_documents
                (id, tenant_id, patient_id, encounter_id, document_type,
                 title, description, mime_type, file_size, storage_key,
                 uploaded_by, status, created_at, updated_at)
            VALUES (?, ?, ?::uuid, ?::uuid, ?, ?, ?, ?, ?, ?, ?, 'ACTIVE', ?, ?)
            """,
                documentId, tenantId, request.patient_id(), request.encounter_id(),
                request.document_type(),
                request.title(), request.description(),
                request.mime_type(), request.file_size(), request.storage_key(),
                request.uploaded_by(),
                now, now);

        outboxService.writeOutboxEvent(
                "impilo.experience.document.uploaded.v1",
                correlationId,
                requestId,
                idempotencyKey != null ? idempotencyKey : requestId,
                tenantId,
                podId,
                "ClinicalDocument",
                documentId.toString(),
                Map.of(
                        "document_id", documentId.toString(),
                        "patient_id", request.patient_id(),
                        "document_type", request.document_type(),
                        "title", request.title(),
                        "storage_key", request.storage_key(),
                        "status", "ACTIVE"
                ),
                Map.of()
        );

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

    private Map<String, Object> toResource(ClinicalDocument d) {
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("patient_id", d.getPatientId());
        attributes.put("encounter_id", d.getEncounterId());
        attributes.put("document_type", d.getDocumentType());
        attributes.put("title", d.getTitle());
        attributes.put("description", d.getDescription());
        attributes.put("mime_type", d.getMimeType());
        attributes.put("file_size", d.getFileSize());
        attributes.put("storage_key", d.getStorageKey());
        attributes.put("uploaded_by", d.getUploadedBy());
        attributes.put("status", d.getStatus());
        attributes.put("created_at", d.getCreatedAt());
        attributes.put("updated_at", d.getUpdatedAt());

        // V15 bridge: include document_object_id and download URL if available
        try {
            List<Map<String, Object>> objRows = jdbcTemplate.queryForList(
                    "SELECT document_object_id FROM clinical_documents WHERE id = ?", d.getId());
            if (!objRows.isEmpty() && objRows.get(0).get("document_object_id") != null) {
                UUID objectId = (UUID) objRows.get(0).get("document_object_id");
                attributes.put("document_object_id", objectId.toString());
                try {
                    JsonNode signedUrl = documentServiceClient.getSignedUrl(objectId);
                    if (signedUrl != null && signedUrl.has("url")) {
                        attributes.put("download_url", signedUrl.get("url").asText());
                    }
                } catch (Exception e) {
                    log.debug("Could not generate download URL for object {}: {}", objectId, e.getMessage());
                }
            }
        } catch (Exception e) {
            // V15 column may not exist yet
        }

        Map<String, Object> resource = new LinkedHashMap<>();
        resource.put("id", d.getId().toString());
        resource.put("type", "ClinicalDocument");
        resource.put("attributes", attributes);
        return resource;
    }
}
