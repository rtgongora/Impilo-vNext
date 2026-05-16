package zw.gov.mohcc.impilo.docstore.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import zw.gov.mohcc.impilo.docstore.config.DocumentStoreProperties;
import zw.gov.mohcc.impilo.docstore.core.scan.ScanService;
import zw.gov.mohcc.impilo.docstore.core.storage.ObjectStorageProvider;
import zw.gov.mohcc.impilo.docstore.core.storage.ObjectStorageProviderRouter;
import zw.gov.mohcc.impilo.docstore.dto.ObjectResponse;
import zw.gov.mohcc.impilo.docstore.dto.ScanResult;
import zw.gov.mohcc.impilo.docstore.dto.SignedUrlResponse;
import zw.gov.mohcc.impilo.docstore.dto.StoreObjectRequest;
import zw.gov.mohcc.impilo.docstore.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.docstore.persistence.entity.ObjectEntity;
import zw.gov.mohcc.impilo.docstore.persistence.entity.SignedUrlEntity;
import zw.gov.mohcc.impilo.docstore.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.docstore.persistence.repository.ObjectRepository;
import zw.gov.mohcc.impilo.docstore.persistence.repository.SignedUrlRepository;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.*;

/**
 * Core service for object storage operations.
 *
 * Handles:
 *   - Uploading files to MinIO with SHA-256 hash computation
 *   - Tracking metadata in PostgreSQL
 *   - Generating pre-signed URLs for time-limited direct access
 *   - Soft-deleting objects
 *   - Triggering antivirus scans via the configured ScanService
 *   - Publishing domain events via the outbox pattern
 *
 * Object key pattern: {tenantId}/{objectId}/{filename}
 */
@Service
public class ObjectStorageService {

    private static final Logger log = LoggerFactory.getLogger(ObjectStorageService.class);

    private final DocumentStoreProperties properties;
    private final ObjectStorageProviderRouter storageProviderRouter;
    private final ObjectRepository objectRepository;
    private final SignedUrlRepository signedUrlRepository;
    private final EventOutboxRepository outboxRepository;
    private final ScanService scanService;
    private final ObjectMapper objectMapper;

    public ObjectStorageService(DocumentStoreProperties properties,
                                ObjectStorageProviderRouter storageProviderRouter,
                                 ObjectRepository objectRepository,
                                 SignedUrlRepository signedUrlRepository,
                                 EventOutboxRepository outboxRepository,
                                 ScanService scanService,
                                 ObjectMapper objectMapper) {
        this.properties = properties;
        this.storageProviderRouter = storageProviderRouter;
        this.objectRepository = objectRepository;
        this.signedUrlRepository = signedUrlRepository;
        this.outboxRepository = outboxRepository;
        this.scanService = scanService;
        this.objectMapper = objectMapper;
    }

    /**
     * Store a file in MinIO and track its metadata.
     *
     * @param file    the uploaded multipart file
     * @param request metadata for the object (filename, MIME type, custom metadata)
     * @return response containing the stored object's metadata
     */
    @Transactional
    public ObjectResponse store(MultipartFile file, StoreObjectRequest request) {
        TrustContext ctx = TrustContextHolder.require();

        validateFileSize(file);
        ObjectStorageProvider storageProvider = storageProviderRouter.activeProvider();

        try {
            byte[] content = file.getBytes();
            String sha256 = computeSha256(content);
            UUID objectId = UUID.randomUUID();
            String bucket = properties.getMinio().getBucket();

            // Build object key: {tenantId}/{objectId}/{filename}
            String filename = request.originalFilename() != null
                    ? request.originalFilename()
                    : file.getOriginalFilename();
            if (filename == null || filename.isBlank()) {
                filename = objectId.toString();
            }
            String objectKey = ctx.tenantId() + "/" + objectId + "/" + sanitizeFilename(filename);

            // Ensure bucket exists
            storageProvider.ensureBucketExists(bucket);

            // Upload to MinIO
            String providerStorageKey = storageProvider.putObject(bucket, objectKey, content, request.mimeType());

            log.info("Stored object {} in {}: bucket={}, key={}, size={}, sha256={}",
                    objectId, storageProvider.providerType(), bucket, providerStorageKey, content.length, sha256);

            // Persist metadata
            ObjectEntity entity = new ObjectEntity();
            entity.setObjectId(objectId);
            entity.setTenantId(ctx.tenantId());
            entity.setBucket(bucket);
            entity.setObjectKey(providerStorageKey);
            entity.setOriginalFilename(filename);
            entity.setMimeType(request.mimeType());
            entity.setFileSizeBytes(content.length);
            entity.setHashSha256(sha256);
            entity.setCreatedBy(ctx.actorId() != null ? ctx.actorId() : "system");
            entity.setMetadata(serializeMetadata(augmentMetadata(request.metadata(), storageProvider.providerType())));

            // Run antivirus scan
            if (properties.getScan().isEnabled()) {
                ScanResult scanResult = scanService.scan(objectId, content);
                entity.setScanStatus(scanResult.status());
                entity.setScanResult(scanResult.result());
            } else {
                entity.setScanStatus("SKIPPED");
                entity.setScanResult("Scanning disabled");
            }

            entity = objectRepository.save(entity);

            // Publish outbox event (legacy + v1.1 dual emit via DocumentOutboxPublisher)
            publishDocumentStoredEvent(objectId, request, content.length, sha256, ctx);

            return toResponse(entity);

        } catch (Exception e) {
            log.error("Failed to store object: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to store object: " + e.getMessage(), e);
        }
    }

    /**
     * Retrieve metadata for a stored object.
     *
     * @param objectId the UUID of the object
     * @return object metadata response
     * @throws NoSuchElementException if the object does not exist or has been deleted
     */
    public ObjectResponse getObject(UUID objectId) {
        ObjectEntity entity = objectRepository.findByObjectIdAndDeletedAtIsNull(objectId)
                .orElseThrow(() -> new NoSuchElementException("Object not found: " + objectId));
        return toResponse(entity);
    }

    /**
     * Generate a pre-signed URL for direct object access.
     *
     * @param objectId the UUID of the object
     * @return signed URL response with token and expiration
     */
    @Transactional
    public SignedUrlResponse generateSignedUrl(UUID objectId) {
        TrustContext ctx = TrustContextHolder.require();

        ObjectEntity entity = objectRepository.findByObjectIdAndDeletedAtIsNull(objectId)
                .orElseThrow(() -> new NoSuchElementException("Object not found: " + objectId));
        ObjectStorageProvider storageProvider = storageProviderRouter.activeProvider();

        try {
            int ttlSeconds = properties.getSignedUrl().getTtlSeconds();

            String url = storageProvider.generateSignedUrl(entity.getBucket(), entity.getObjectKey(), ttlSeconds);

            String token = UUID.randomUUID().toString().replace("-", "")
                    + UUID.randomUUID().toString().replace("-", "");

            OffsetDateTime expiresAt = OffsetDateTime.now().plusSeconds(ttlSeconds);

            // Record the signed URL for audit
            SignedUrlEntity signedUrl = new SignedUrlEntity();
            signedUrl.setObjectId(objectId);
            signedUrl.setUrlToken(token);
            signedUrl.setExpiresAt(expiresAt);
            signedUrl.setCreatedBy(ctx.actorId() != null ? ctx.actorId() : "system");
            signedUrlRepository.save(signedUrl);

            log.info("Generated signed URL for object {}, expires at {}", objectId, expiresAt);

            return new SignedUrlResponse(url, token, expiresAt);

        } catch (Exception e) {
            log.error("Failed to generate signed URL for object {}: {}", objectId, e.getMessage(), e);
            throw new RuntimeException("Failed to generate signed URL: " + e.getMessage(), e);
        }
    }

    /**
     * Download the raw object content from MinIO.
     *
     * @param objectId the UUID of the object
     * @return input stream of the object content
     */
    public InputStream downloadObject(UUID objectId) {
        ObjectEntity entity = objectRepository.findByObjectIdAndDeletedAtIsNull(objectId)
                .orElseThrow(() -> new NoSuchElementException("Object not found: " + objectId));
        ObjectStorageProvider storageProvider = storageProviderRouter.activeProvider();

        try {
            return storageProvider.getObject(entity.getBucket(), entity.getObjectKey());
        } catch (Exception e) {
            log.error("Failed to download object {}: {}", objectId, e.getMessage(), e);
            throw new RuntimeException("Failed to download object: " + e.getMessage(), e);
        }
    }

    /**
     * Retrieve the entity for a stored object (used by controller for content-type headers).
     *
     * @param objectId the UUID of the object
     * @return the object entity
     */
    public ObjectEntity getObjectEntity(UUID objectId) {
        return objectRepository.findByObjectIdAndDeletedAtIsNull(objectId)
                .orElseThrow(() -> new NoSuchElementException("Object not found: " + objectId));
    }

    /**
     * Soft-delete an object (marks deleted_at, does not remove from MinIO immediately).
     *
     * @param objectId the UUID of the object to delete
     */
    @Transactional
    public void deleteObject(UUID objectId) {
        TrustContext ctx = TrustContextHolder.require();

        ObjectEntity entity = objectRepository.findByObjectIdAndDeletedAtIsNull(objectId)
                .orElseThrow(() -> new NoSuchElementException("Object not found: " + objectId));

        entity.setDeletedAt(OffsetDateTime.now());
        objectRepository.save(entity);

        log.info("Soft-deleted object {}, will be purged from MinIO by background job", objectId);

        publishDocumentDeletedEvent(objectId, entity, ctx);
    }

    /**
     * Find objects by SHA-256 hash (for deduplication checks).
     *
     * @param sha256 the hex-encoded SHA-256 hash
     * @return list of matching object responses
     */
    public List<ObjectResponse> getByHash(String sha256) {
        return objectRepository.findByHashSha256AndDeletedAtIsNull(sha256)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    // --- Private helpers ---

    private void validateFileSize(MultipartFile file) {
        long maxBytes = (long) properties.getMaxFileSizeMb() * 1024 * 1024;
        if (file.getSize() > maxBytes) {
            throw new IllegalArgumentException(
                    "File size " + file.getSize() + " bytes exceeds maximum of " + maxBytes + " bytes");
        }
    }

    private String computeSha256(byte[] content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content);
            return bytesToHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private String sanitizeFilename(String filename) {
        // Remove path separators and null bytes, keep only safe characters
        return filename.replaceAll("[^a-zA-Z0-9._\\-]", "_");
    }

    private String serializeMetadata(Map<String, String> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return "{}";
        }
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize metadata, using empty object: {}", e.getMessage());
            return "{}";
        }
    }

    private Map<String, String> augmentMetadata(Map<String, String> metadata, String providerType) {
        Map<String, String> out = new LinkedHashMap<>();
        if (metadata != null) {
            out.putAll(metadata);
        }
        out.putIfAbsent("storageProvider", providerType);
        return out;
    }

    private Map<String, String> deserializeMetadata(String json) {
        if (json == null || json.isBlank() || "{}".equals(json)) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, String>>() {});
        } catch (JsonProcessingException e) {
            log.warn("Failed to deserialize metadata: {}", e.getMessage());
            return Map.of();
        }
    }

    private ObjectResponse toResponse(ObjectEntity entity) {
        return new ObjectResponse(
                entity.getObjectId(),
                entity.getTenantId(),
                entity.getBucket(),
                entity.getObjectKey(),
                entity.getOriginalFilename(),
                entity.getMimeType(),
                entity.getFileSizeBytes(),
                entity.getHashSha256(),
                entity.getScanStatus(),
                deserializeMetadata(entity.getMetadata()),
                entity.getCreatedBy(),
                entity.getCreatedAt()
        );
    }

    private void publishDocumentStoredEvent(UUID objectId, StoreObjectRequest request,
                                            int fileSizeBytes, String sha256, TrustContext ctx) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("document_id", objectId.toString());
            payload.put("tenant_id", ctx.tenantId().toString());
            payload.put("content_type", request.mimeType());
            payload.put("file_size_bytes", fileSizeBytes);
            payload.put("hash_sha256", sha256);
            String patientCpid = metadataFirst(request.metadata(), "patient_cpid", "patientCpid");
            if (patientCpid != null) {
                payload.put("patient_cpid", patientCpid);
            }
            String encounterId = metadataFirst(request.metadata(), "encounter_id", "encounterId");
            if (encounterId != null) {
                payload.put("encounter_id", encounterId);
            }

            EventOutboxEntity event = new EventOutboxEntity();
            event.setAggregateType("DOCUMENT");
            event.setAggregateId(objectId.toString());
            event.setEventType("document.stored");
            event.setPayload(objectMapper.writeValueAsString(payload));
            event.setTenantId(ctx.tenantId().toString());
            event.setCorrelationId(ctx.correlationId() != null ? ctx.correlationId().toString() : null);
            event.setSubjectType("DOCUMENT");
            event.setSubjectId(objectId.toString());
            event.setPartitionKey(objectId.toString());
            event.setOccurredAt(OffsetDateTime.now());
            outboxRepository.save(event);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize outbox event payload: {}", e.getMessage(), e);
        }
    }

    private void publishDocumentDeletedEvent(UUID objectId, ObjectEntity entity, TrustContext ctx) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("document_id", objectId.toString());
            payload.put("tenant_id", entity.getTenantId().toString());
            payload.put("deleted_by", ctx.actorId() != null ? ctx.actorId() : "system");

            EventOutboxEntity event = new EventOutboxEntity();
            event.setAggregateType("DOCUMENT");
            event.setAggregateId(objectId.toString());
            event.setEventType("document.deleted");
            event.setPayload(objectMapper.writeValueAsString(payload));
            event.setTenantId(entity.getTenantId().toString());
            event.setCorrelationId(ctx.correlationId() != null ? ctx.correlationId().toString() : null);
            event.setSubjectType("DOCUMENT");
            event.setSubjectId(objectId.toString());
            event.setPartitionKey(objectId.toString());
            event.setOccurredAt(OffsetDateTime.now());
            outboxRepository.save(event);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize outbox event payload: {}", e.getMessage(), e);
        }
    }

    private static String metadataFirst(Map<String, String> metadata, String... keys) {
        if (metadata == null) {
            return null;
        }
        for (String k : keys) {
            String v = metadata.get(k);
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return null;
    }
}
