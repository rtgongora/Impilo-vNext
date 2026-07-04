package zw.gov.mohcc.impilo.docstore.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import zw.gov.mohcc.impilo.docstore.core.ocr.OcrService;
import zw.gov.mohcc.impilo.docstore.core.ObjectStorageService;
import zw.gov.mohcc.impilo.docstore.core.signature.SignatureService;
import zw.gov.mohcc.impilo.docstore.dto.ObjectResponse;
import zw.gov.mohcc.impilo.docstore.dto.RegisterExternalObjectRequest;
import zw.gov.mohcc.impilo.docstore.dto.SignedUrlResponse;
import zw.gov.mohcc.impilo.docstore.dto.StoreObjectRequest;
import zw.gov.mohcc.impilo.docstore.persistence.entity.ObjectEntity;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * REST controller for document object storage operations.
 *
 * All endpoints are internal-only (accessed by platform services via Envoy).
 * Authentication is handled by the TrustContext filter and OAuth2 resource server.
 *
 * Endpoints:
 *   POST   /v1/internal/objects               — upload a new object
 *   POST   /v1/internal/objects/register-external — adopt an externally written MinIO object
 *   GET    /v1/internal/objects/{objectId}     — retrieve object metadata
 *   GET    /v1/internal/objects/{objectId}/signed-url — generate pre-signed download URL
 *   GET    /v1/internal/objects/{objectId}/download   — proxy download through this service
 *   DELETE /v1/internal/objects/{objectId}     — soft-delete an object
 *   GET    /v1/internal/objects/by-hash/{sha256}      — find objects by content hash
 */
@RestController
@RequestMapping("/v1/internal/objects")
public class ObjectController {

    private static final Logger log = LoggerFactory.getLogger(ObjectController.class);

    private final ObjectStorageService storageService;
    private final OcrService ocrService;
    private final SignatureService signatureService;

    public ObjectController(ObjectStorageService storageService,
                            OcrService ocrService,
                            SignatureService signatureService) {
        this.storageService = storageService;
        this.ocrService = ocrService;
        this.signatureService = signatureService;
    }

    /**
     * Upload a new document object.
     *
     * Accepts a multipart form with the binary file and metadata fields.
     * Computes SHA-256 hash, stores in MinIO, saves metadata, and
     * optionally triggers antivirus scanning.
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<ObjectResponse>> uploadObject(
            @RequestPart("file") MultipartFile file,
            @RequestPart(value = "originalFilename", required = false) String originalFilename,
            @RequestPart(value = "mimeType") String mimeType,
            @RequestPart(value = "metadata", required = false) Map<String, String> metadata) {

        String correlationId = getCorrelationId();

        try {
            StoreObjectRequest request = new StoreObjectRequest(originalFilename, mimeType, metadata);
            ObjectResponse response = storageService.store(file, request);

            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.ok(response, correlationId));

        } catch (IllegalArgumentException e) {
            log.warn("Validation error during upload: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("VALIDATION_FAILED", e.getMessage(), 400, correlationId));
        } catch (Exception e) {
            log.error("Upload failed: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("UPLOAD_FAILED", "Failed to store object", 500, correlationId));
        }
    }

    /**
     * Adopt an externally written MinIO object into the catalogue.
     *
     * The binary was written directly to object storage by another platform
     * component (e.g. rtc-gateway recording egress). Registration verifies the
     * object exists (statObject), then creates the same metadata row multipart
     * ingest does, so the artifact gets signed-url playback and audit.
     *
     * Idempotent: a bucket+key that is already catalogued returns the existing
     * object with 200 instead of creating a duplicate (201 on first adoption).
     */
    @PostMapping(value = "/register-external", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<ObjectResponse>> registerExternal(
            @RequestBody RegisterExternalObjectRequest request) {
        String correlationId = getCorrelationId();

        try {
            ObjectStorageService.RegisterExternalResult result = storageService.registerExternal(request);
            return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
                    .body(ApiResponse.ok(result.response(), correlationId));

        } catch (IllegalArgumentException e) {
            log.warn("Validation error during external registration: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("VALIDATION_FAILED", e.getMessage(), 400, correlationId));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("NOT_FOUND", e.getMessage(), 404, correlationId));
        } catch (Exception e) {
            log.error("External registration failed: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("REGISTER_EXTERNAL_FAILED",
                            "Failed to register external object", 500, correlationId));
        }
    }

    /**
     * Retrieve metadata for a stored object.
     */
    @GetMapping("/{objectId}")
    public ResponseEntity<ApiResponse<ObjectResponse>> getObject(@PathVariable UUID objectId) {
        String correlationId = getCorrelationId();

        try {
            ObjectResponse response = storageService.getObject(objectId);
            return ResponseEntity.ok(ApiResponse.ok(response, correlationId));

        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("NOT_FOUND", e.getMessage(), 404, correlationId));
        }
    }

    /**
     * Generate a pre-signed URL for direct object download.
     *
     * The returned URL provides time-limited access (configurable TTL)
     * to the object in MinIO without further authentication.
     */
    @GetMapping("/{objectId}/signed-url")
    public ResponseEntity<ApiResponse<SignedUrlResponse>> getSignedUrl(@PathVariable UUID objectId) {
        String correlationId = getCorrelationId();

        try {
            SignedUrlResponse response = storageService.generateSignedUrl(objectId);
            return ResponseEntity.ok(ApiResponse.ok(response, correlationId));

        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("NOT_FOUND", e.getMessage(), 404, correlationId));
        } catch (Exception e) {
            log.error("Signed URL generation failed for object {}: {}", objectId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("SIGNED_URL_FAILED",
                            "Failed to generate signed URL", 500, correlationId));
        }
    }

    /**
     * Generate a preview payload for inline rendering clients.
     *
     * Returns a signed URL with MIME/disposition hints without proxying bytes.
     */
    @GetMapping("/{objectId}/preview")
    public ResponseEntity<ApiResponse<java.util.Map<String, Object>>> getPreview(@PathVariable UUID objectId) {
        String correlationId = getCorrelationId();
        try {
            ObjectEntity entity = storageService.getObjectEntity(objectId);
            SignedUrlResponse signed = storageService.generateSignedUrl(objectId);
            java.util.Map<String, Object> preview = java.util.Map.of(
                    "url", signed.url(),
                    "expiresAt", signed.expiresAt(),
                    "mimeType", entity.getMimeType(),
                    "filename", entity.getOriginalFilename(),
                    "disposition", "inline");
            return ResponseEntity.ok(ApiResponse.ok(preview, correlationId));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("NOT_FOUND", e.getMessage(), 404, correlationId));
        } catch (Exception e) {
            log.error("Preview generation failed for object {}: {}", objectId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("PREVIEW_FAILED",
                            "Failed to generate preview URL", 500, correlationId));
        }
    }

    /**
     * Proxy-download an object's content through this service.
     *
     * Streams the object content from MinIO through the service layer,
     * setting appropriate Content-Type and Content-Disposition headers.
     */
    @GetMapping("/{objectId}/download")
    public ResponseEntity<?> downloadObject(@PathVariable UUID objectId) {
        String correlationId = getCorrelationId();

        try {
            ObjectEntity entity = storageService.getObjectEntity(objectId);
            InputStream stream = storageService.downloadObject(objectId);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType(entity.getMimeType()));
            headers.setContentLength(entity.getFileSizeBytes());
            if (entity.getOriginalFilename() != null) {
                headers.setContentDispositionFormData("attachment", entity.getOriginalFilename());
            }

            return ResponseEntity.ok()
                    .headers(headers)
                    .body(new InputStreamResource(stream));

        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("NOT_FOUND", e.getMessage(), 404, correlationId));
        } catch (Exception e) {
            log.error("Download failed for object {}: {}", objectId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("DOWNLOAD_FAILED",
                            "Failed to download object", 500, correlationId));
        }
    }

    /**
     * Soft-delete an object.
     *
     * Sets the deleted_at timestamp on the metadata record. The actual
     * binary content in MinIO is retained for potential recovery and
     * will be purged by a background cleanup job.
     */
    @DeleteMapping("/{objectId}")
    public ResponseEntity<ApiResponse<Void>> deleteObject(@PathVariable UUID objectId) {
        String correlationId = getCorrelationId();

        try {
            storageService.deleteObject(objectId);
            return ResponseEntity.ok(ApiResponse.ok(null, correlationId));

        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("NOT_FOUND", e.getMessage(), 404, correlationId));
        }
    }

    /**
     * Find objects by SHA-256 content hash.
     *
     * Used for deduplication — checks whether an identical file has
     * already been stored based on its content hash.
     */
    @GetMapping("/by-hash/{sha256}")
    public ResponseEntity<ApiResponse<List<ObjectResponse>>> getByHash(@PathVariable String sha256) {
        String correlationId = getCorrelationId();
        List<ObjectResponse> results = storageService.getByHash(sha256);
        return ResponseEntity.ok(ApiResponse.ok(results, correlationId));
    }

    @PostMapping("/{objectId}/ocr")
    public ResponseEntity<ApiResponse<java.util.Map<String, Object>>> requestOcr(@PathVariable UUID objectId) {
        String correlationId = getCorrelationId();
        try {
            return ResponseEntity.accepted().body(ApiResponse.ok(ocrService.requestOcr(objectId), correlationId));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("NOT_FOUND", e.getMessage(), 404, correlationId));
        } catch (Exception e) {
            log.error("OCR request failed for object {}: {}", objectId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("OCR_FAILED", "Failed to process OCR request", 500, correlationId));
        }
    }

    @GetMapping("/{objectId}/ocr")
    public ResponseEntity<ApiResponse<java.util.Map<String, Object>>> getLatestOcr(@PathVariable UUID objectId) {
        String correlationId = getCorrelationId();
        try {
            return ResponseEntity.ok(ApiResponse.ok(ocrService.getLatestOcr(objectId), correlationId));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("NOT_FOUND", e.getMessage(), 404, correlationId));
        }
    }

    @PostMapping("/{objectId}/signature")
    public ResponseEntity<ApiResponse<java.util.Map<String, Object>>> requestSignature(
            @PathVariable UUID objectId,
            @RequestBody(required = false) java.util.Map<String, Object> body) {
        String correlationId = getCorrelationId();
        try {
            String signerId = body == null || body.get("signerId") == null ? null : body.get("signerId").toString();
            return ResponseEntity.accepted().body(ApiResponse.ok(signatureService.requestSignature(objectId, signerId), correlationId));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("NOT_FOUND", e.getMessage(), 404, correlationId));
        } catch (Exception e) {
            log.error("Signature request failed for object {}: {}", objectId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("SIGNATURE_FAILED", "Failed to process signature request", 500, correlationId));
        }
    }

    @GetMapping("/{objectId}/signature")
    public ResponseEntity<ApiResponse<java.util.Map<String, Object>>> getLatestSignature(@PathVariable UUID objectId) {
        String correlationId = getCorrelationId();
        try {
            return ResponseEntity.ok(ApiResponse.ok(signatureService.getLatestSignature(objectId), correlationId));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("NOT_FOUND", e.getMessage(), 404, correlationId));
        }
    }

    @GetMapping("/signatures/{jobId}/verify")
    public ResponseEntity<ApiResponse<java.util.Map<String, Object>>> verifySignature(@PathVariable UUID jobId) {
        String correlationId = getCorrelationId();
        try {
            return ResponseEntity.ok(ApiResponse.ok(signatureService.verifySignature(jobId), correlationId));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("NOT_FOUND", e.getMessage(), 404, correlationId));
        }
    }

    // --- Private helpers ---

    private String getCorrelationId() {
        var ctx = TrustContextHolder.get();
        return ctx != null && ctx.correlationId() != null
                ? ctx.correlationId().toString()
                : UUID.randomUUID().toString();
    }
}
