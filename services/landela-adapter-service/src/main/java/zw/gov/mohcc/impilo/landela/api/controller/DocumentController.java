package zw.gov.mohcc.impilo.landela.api.controller;

import jakarta.validation.Valid;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import zw.gov.mohcc.impilo.landela.api.dto.*;
import zw.gov.mohcc.impilo.landela.core.DocumentService;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;
import zw.gov.mohcc.impilo.shared.response.PagedResponse;

import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * Document Gateway REST API.
 *
 * Provides multipart upload, metadata retrieval, signed URL generation,
 * proxy download, search, metadata updates, lifecycle management,
 * and FHIR DocumentReference publication.
 */
@RestController
@RequestMapping("/v1/internal/documents")
public class DocumentController {

    private final DocumentService documentService;

    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    /**
     * Upload a new document (multipart).
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<DocumentResponse>> upload(
            @RequestPart("metadata") @Valid UploadDocumentRequest metadata,
            @RequestPart("file") MultipartFile file) {

        TrustContext ctx = TrustContextHolder.require();

        DocumentResponse response = documentService.upload(metadata, file, ctx);
        return ResponseEntity.status(201).body(
                ApiResponse.ok(response, ctx.correlationId().toString()));
    }

    /**
     * Get document metadata by document ID.
     */
    @GetMapping("/{documentId}")
    public ResponseEntity<ApiResponse<DocumentResponse>> getMetadata(@PathVariable UUID documentId) {
        TrustContext ctx = TrustContextHolder.require();

        try {
            DocumentResponse response = documentService.getMetadata(documentId);
            return ResponseEntity.ok(
                    ApiResponse.ok(response, ctx.correlationId().toString()));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(404).body(
                    ApiResponse.error("NOT_FOUND", "Document not found: " + documentId, 404,
                            ctx.correlationId().toString()));
        }
    }

    /**
     * Generate a pre-signed URL for downloading a document.
     */
    @GetMapping("/{documentId}/signed-url")
    public ResponseEntity<ApiResponse<SignedUrlResponse>> generateSignedUrl(@PathVariable UUID documentId) {
        TrustContext ctx = TrustContextHolder.require();

        try {
            SignedUrlResponse response = documentService.generateSignedUrl(documentId, ctx);
            return ResponseEntity.ok(
                    ApiResponse.ok(response, ctx.correlationId().toString()));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(404).body(
                    ApiResponse.error("NOT_FOUND", "Document not found: " + documentId, 404,
                            ctx.correlationId().toString()));
        }
    }

    /**
     * Proxy download — streams the binary content directly to the caller.
     */
    @GetMapping("/{documentId}/download")
    public ResponseEntity<?> download(@PathVariable UUID documentId) {
        TrustContext ctx = TrustContextHolder.require();

        try {
            DocumentService.DownloadResult result = documentService.download(documentId, ctx);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType(result.mimeType()));
            if (result.fileName() != null) {
                headers.setContentDispositionFormData("attachment", result.fileName());
            }
            if (result.fileSizeBytes() != null) {
                headers.setContentLength(result.fileSizeBytes());
            }

            return ResponseEntity.ok()
                    .headers(headers)
                    .body(new InputStreamResource(result.inputStream()));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(404).body(
                    ApiResponse.error("NOT_FOUND", "Document not found: " + documentId, 404,
                            ctx.correlationId().toString()));
        }
    }

    /**
     * Search documents with query parameters.
     */
    @GetMapping
    public ResponseEntity<ApiResponse<PagedResponse<DocumentResponse>>> search(
            @RequestParam(required = false) String subjectType,
            @RequestParam(required = false) String subjectId,
            @RequestParam(required = false) String documentTypeCode,
            @RequestParam(required = false) String lifecycleState,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        TrustContext ctx = TrustContextHolder.require();

        DocumentSearchRequest request = new DocumentSearchRequest(
                subjectType, subjectId, documentTypeCode, lifecycleState, page, size);

        PagedResponse<DocumentResponse> response = documentService.search(request, ctx);
        return ResponseEntity.ok(
                ApiResponse.ok(response, ctx.correlationId().toString()));
    }

    /**
     * Update document metadata (tags and/or document type code).
     */
    @PatchMapping("/{documentId}/metadata")
    public ResponseEntity<ApiResponse<DocumentResponse>> updateMetadata(
            @PathVariable UUID documentId,
            @RequestBody MetadataUpdatePayload payload) {

        TrustContext ctx = TrustContextHolder.require();

        try {
            DocumentResponse response = documentService.updateMetadata(
                    documentId, payload.tags(), payload.documentTypeCode(), ctx);
            return ResponseEntity.ok(
                    ApiResponse.ok(response, ctx.correlationId().toString()));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(404).body(
                    ApiResponse.error("NOT_FOUND", "Document not found: " + documentId, 404,
                            ctx.correlationId().toString()));
        }
    }

    /**
     * Perform a lifecycle action on a document (ARCHIVE, REVOKE, SUPERSEDE, DELETE).
     */
    @PostMapping("/{documentId}/lifecycle")
    public ResponseEntity<ApiResponse<DocumentResponse>> lifecycleAction(
            @PathVariable UUID documentId,
            @RequestBody @Valid LifecycleActionRequest request) {

        TrustContext ctx = TrustContextHolder.require();

        try {
            DocumentResponse response = documentService.lifecycleAction(documentId, request, ctx);
            return ResponseEntity.ok(
                    ApiResponse.ok(response, ctx.correlationId().toString()));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(404).body(
                    ApiResponse.error("NOT_FOUND", "Document not found: " + documentId, 404,
                            ctx.correlationId().toString()));
        } catch (IllegalStateException | IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(
                    ApiResponse.error("BAD_REQUEST", e.getMessage(), 400,
                            ctx.correlationId().toString()));
        }
    }

    /**
     * Push a FHIR DocumentReference to BUTANO (HAPI FHIR server).
     */
    @PostMapping("/{documentId}/fhir-push")
    public ResponseEntity<ApiResponse<Void>> pushDocumentReference(
            @PathVariable UUID documentId,
            @RequestParam String fhirServerUrl) {

        TrustContext ctx = TrustContextHolder.require();

        try {
            documentService.pushDocumentReference(documentId, fhirServerUrl, ctx);
            return ResponseEntity.ok(
                    ApiResponse.ok(null, ctx.correlationId().toString()));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(404).body(
                    ApiResponse.error("NOT_FOUND", "Document not found: " + documentId, 404,
                            ctx.correlationId().toString()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(502).body(
                    ApiResponse.error("FHIR_PUSH_FAILED", e.getMessage(), 502,
                            ctx.correlationId().toString()));
        }
    }

    /**
     * Inline payload for metadata PATCH requests.
     */
    private record MetadataUpdatePayload(
            Map<String, Object> tags,
            String documentTypeCode
    ) {}
}
