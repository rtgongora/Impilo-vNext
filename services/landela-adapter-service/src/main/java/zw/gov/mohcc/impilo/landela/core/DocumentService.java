package zw.gov.mohcc.impilo.landela.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;
import zw.gov.mohcc.impilo.landela.api.dto.*;
import zw.gov.mohcc.impilo.landela.config.LandelaProperties;
import zw.gov.mohcc.impilo.landela.persistence.entity.DocumentEntity;
import zw.gov.mohcc.impilo.landela.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.landela.persistence.repository.DocumentRepository;
import zw.gov.mohcc.impilo.landela.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.response.PagedResponse;

import java.io.InputStream;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;

/**
 * Main document service — orchestrates upload, retrieval, search,
 * lifecycle management, and FHIR DocumentReference publication.
 */
@Service
public class DocumentService {

    private static final Logger log = LoggerFactory.getLogger(DocumentService.class);

    private final DocumentRepository documentRepository;
    private final EventOutboxRepository outboxRepository;
    private final StorageRouter storageRouter;
    private final AuditService auditService;
    private final LandelaProperties properties;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;

    public DocumentService(DocumentRepository documentRepository,
                           EventOutboxRepository outboxRepository,
                           StorageRouter storageRouter,
                           AuditService auditService,
                           LandelaProperties properties,
                           ObjectMapper objectMapper) {
        this.documentRepository = documentRepository;
        this.outboxRepository = outboxRepository;
        this.storageRouter = storageRouter;
        this.auditService = auditService;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.restTemplate = new RestTemplate();
    }

    /**
     * Upload a new document: store binary, create metadata record, publish outbox event.
     */
    @Transactional
    public DocumentResponse upload(UploadDocumentRequest request, MultipartFile file, TrustContext ctx) {
        // Compute SHA-256 hash of file content
        String hash = computeSha256(file);

        // Determine MIME type
        String mimeType = file.getContentType();
        if (mimeType == null || mimeType.isBlank()) {
            mimeType = "application/octet-stream";
        }

        // Build storage object key: tenantId/subjectType/subjectId/UUID
        UUID documentId = UUID.randomUUID();
        String objectKey = ctx.tenantId() + "/"
                + request.subjectType() + "/"
                + request.subjectId() + "/"
                + documentId;

        // Store binary in storage provider
        String storageRef = storageRouter.store(objectKey, file, mimeType);

        // Create metadata entity
        DocumentEntity entity = new DocumentEntity();
        entity.setDocumentId(documentId);
        entity.setTenantId(ctx.tenantId());
        entity.setSubjectType(request.subjectType());
        entity.setSubjectId(request.subjectId());
        entity.setDocumentTypeCode(request.documentTypeCode());
        entity.setSource(request.source() != null ? request.source() : "uploaded");
        entity.setIssuer(request.issuer());
        entity.setConfidentiality(request.confidentiality() != null ? request.confidentiality() : "NORMAL");
        entity.setHashSha256(hash);
        entity.setMimeType(mimeType);
        entity.setFileName(request.fileName() != null ? request.fileName() : file.getOriginalFilename());
        entity.setFileSizeBytes(file.getSize());
        entity.setStorageProvider(storageRouter.getProviderName());
        entity.setStorageRef(storageRef);
        entity.setRetentionPolicyId(request.retentionPolicyId());
        entity.setCreatedBy(ctx.actorId());

        if (request.tags() != null && !request.tags().isEmpty()) {
            try {
                entity.setTags(objectMapper.writeValueAsString(request.tags()));
            } catch (JsonProcessingException e) {
                log.warn("Failed to serialize tags, using empty: {}", e.getMessage());
                entity.setTags("{}");
            }
        }

        entity = documentRepository.save(entity);

        // Publish outbox event
        publishOutboxEvent("DOCUMENT", entity.getDocumentId().toString(), "DOCUMENT_CREATED", entity);

        // Record audit
        auditService.recordAudit(entity.getDocumentId(), "UPLOAD", ctx,
                Map.of("fileName", entity.getFileName() != null ? entity.getFileName() : "",
                       "mimeType", mimeType,
                       "fileSizeBytes", file.getSize()));

        log.info("Uploaded document: id={}, type={}, subject={}/{}", entity.getDocumentId(),
                request.documentTypeCode(), request.subjectType(), request.subjectId());

        return toResponse(entity);
    }

    /**
     * Get document metadata by document ID.
     */
    public DocumentResponse getMetadata(UUID documentId) {
        DocumentEntity entity = findEntityOrThrow(documentId);
        return toResponse(entity);
    }

    /**
     * Generate a pre-signed GET URL for downloading a document.
     */
    @Transactional
    public SignedUrlResponse generateSignedUrl(UUID documentId, TrustContext ctx) {
        DocumentEntity entity = findEntityOrThrow(documentId);

        String url = storageRouter.generateSignedUrl(entity.getStorageRef());
        int ttl = properties.getSignedUrl().getTtlSeconds();
        Instant expiresAt = Instant.now().plusSeconds(ttl);

        // Audit the access
        auditService.recordAudit(documentId, "SIGNED_URL_GENERATED", ctx,
                Map.of("ttlSeconds", ttl));

        return new SignedUrlResponse(url, expiresAt);
    }

    /**
     * Get the raw binary input stream for proxy download.
     */
    @Transactional
    public DownloadResult download(UUID documentId, TrustContext ctx) {
        DocumentEntity entity = findEntityOrThrow(documentId);
        InputStream stream = storageRouter.getObject(entity.getStorageRef());

        // Audit the download
        auditService.recordAudit(documentId, "DOWNLOAD", ctx, Map.of());

        return new DownloadResult(stream, entity.getMimeType(), entity.getFileName(), entity.getFileSizeBytes());
    }

    /**
     * Search documents with filters.
     */
    public PagedResponse<DocumentResponse> search(DocumentSearchRequest request, TrustContext ctx) {
        PageRequest pageRequest = PageRequest.of(request.page(), request.size(),
                Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<DocumentEntity> page;

        if (request.subjectType() != null && request.subjectId() != null) {
            page = documentRepository.findByTenantIdAndSubjectTypeAndSubjectId(
                    ctx.tenantId(), request.subjectType(), request.subjectId(), pageRequest);
        } else if (request.lifecycleState() != null) {
            page = documentRepository.findByTenantIdAndLifecycleState(
                    ctx.tenantId(), request.lifecycleState(), pageRequest);
        } else {
            page = documentRepository.findByTenantIdAndLifecycleState(
                    ctx.tenantId(), "ACTIVE", pageRequest);
        }

        List<DocumentResponse> items = page.getContent().stream()
                .map(this::toResponse)
                .toList();

        return PagedResponse.of(items, request.page(), request.size(), page.getTotalElements());
    }

    /**
     * Update document metadata (tags and/or document type code).
     */
    @Transactional
    public DocumentResponse updateMetadata(UUID documentId, Map<String, Object> tags,
                                           String documentTypeCode, TrustContext ctx) {
        DocumentEntity entity = findEntityOrThrow(documentId);

        if (tags != null) {
            try {
                entity.setTags(objectMapper.writeValueAsString(tags));
            } catch (JsonProcessingException e) {
                log.warn("Failed to serialize updated tags: {}", e.getMessage());
            }
        }

        if (documentTypeCode != null) {
            entity.setDocumentTypeCode(documentTypeCode);
        }

        entity.setVersion(entity.getVersion() + 1);
        entity = documentRepository.save(entity);

        // Publish outbox event
        publishOutboxEvent("DOCUMENT", entity.getDocumentId().toString(), "DOCUMENT_UPDATED", entity);

        // Record audit
        auditService.recordAudit(documentId, "METADATA_UPDATED", ctx,
                Map.of("documentTypeCode", documentTypeCode != null ? documentTypeCode : "",
                       "tagsUpdated", tags != null));

        return toResponse(entity);
    }

    /**
     * Perform a lifecycle action on a document (ARCHIVE, REVOKE, SUPERSEDE, DELETE).
     */
    @Transactional
    public DocumentResponse lifecycleAction(UUID documentId, LifecycleActionRequest request, TrustContext ctx) {
        DocumentEntity entity = findEntityOrThrow(documentId);
        String action = request.action().toUpperCase();

        String previousState = entity.getLifecycleState();
        String eventType;

        switch (action) {
            case "ARCHIVE" -> {
                validateTransition(previousState, "ARCHIVED");
                entity.setLifecycleState("ARCHIVED");
                eventType = "DOCUMENT_ARCHIVED";
            }
            case "REVOKE" -> {
                validateTransition(previousState, "REVOKED");
                entity.setLifecycleState("REVOKED");
                eventType = "DOCUMENT_REVOKED";
            }
            case "SUPERSEDE" -> {
                validateTransition(previousState, "SUPERSEDED");
                entity.setLifecycleState("SUPERSEDED");
                if (request.supersededByDocumentId() != null) {
                    DocumentEntity successor = documentRepository.findByDocumentId(request.supersededByDocumentId())
                            .orElseThrow(() -> new IllegalArgumentException(
                                    "Successor document not found: " + request.supersededByDocumentId()));
                    entity.setSupersededBy(successor.getId());
                }
                eventType = "DOCUMENT_SUPERSEDED";
            }
            case "DELETE" -> {
                validateTransition(previousState, "DELETED");
                entity.setLifecycleState("DELETED");
                // Remove binary from storage
                try {
                    storageRouter.remove(entity.getStorageRef());
                } catch (StorageException e) {
                    log.warn("Failed to remove binary for document {}: {}", documentId, e.getMessage());
                }
                eventType = "DOCUMENT_DELETED";
            }
            default -> throw new IllegalArgumentException("Unsupported lifecycle action: " + action);
        }

        entity.setVersion(entity.getVersion() + 1);
        entity = documentRepository.save(entity);

        // Publish outbox event
        publishOutboxEvent("DOCUMENT", entity.getDocumentId().toString(), eventType, entity);

        // Record audit
        Map<String, Object> auditDetails = new HashMap<>();
        auditDetails.put("previousState", previousState);
        auditDetails.put("newState", entity.getLifecycleState());
        if (request.reason() != null) {
            auditDetails.put("reason", request.reason());
        }
        auditService.recordAudit(documentId, action, ctx, auditDetails);

        log.info("Lifecycle action: documentId={}, action={}, {} -> {}",
                documentId, action, previousState, entity.getLifecycleState());

        return toResponse(entity);
    }

    /**
     * Push a FHIR DocumentReference to BUTANO (HAPI FHIR server).
     *
     * Creates a minimal FHIR R4 DocumentReference resource and POSTs it
     * to the specified FHIR server URL.
     */
    @Transactional
    public void pushDocumentReference(UUID documentId, String fhirServerUrl, TrustContext ctx) {
        DocumentEntity entity = findEntityOrThrow(documentId);

        // Build a minimal FHIR DocumentReference JSON payload
        Map<String, Object> documentReference = buildFhirDocumentReference(entity);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Tenant-Id", ctx.tenantId().toString());
        headers.set("X-Actor-Id", ctx.actorId());
        headers.set("X-Correlation-Id", ctx.correlationId().toString());

        String payload;
        try {
            payload = objectMapper.writeValueAsString(documentReference);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize DocumentReference: " + e.getMessage(), e);
        }

        HttpEntity<String> request = new HttpEntity<>(payload, headers);
        String url = fhirServerUrl.endsWith("/") ? fhirServerUrl + "DocumentReference"
                : fhirServerUrl + "/DocumentReference";

        try {
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, request, String.class);
            log.info("Pushed DocumentReference to FHIR: documentId={}, status={}",
                    documentId, response.getStatusCode());
        } catch (Exception e) {
            throw new IllegalStateException("Failed to push DocumentReference to FHIR server: " + e.getMessage(), e);
        }

        // Audit the FHIR push
        auditService.recordAudit(documentId, "FHIR_PUSH", ctx,
                Map.of("fhirServerUrl", fhirServerUrl));
    }

    // ---- Internal helpers ----

    private DocumentEntity findEntityOrThrow(UUID documentId) {
        return documentRepository.findByDocumentId(documentId)
                .orElseThrow(() -> new NoSuchElementException("Document not found: " + documentId));
    }

    private void validateTransition(String currentState, String targetState) {
        // Only ACTIVE documents can transition to other states (except DELETE which works on any)
        if ("DELETED".equals(currentState)) {
            throw new IllegalStateException("Cannot transition a DELETED document");
        }
        if (!"ACTIVE".equals(currentState) && !"DELETED".equals(targetState)) {
            throw new IllegalStateException(
                    "Cannot transition from " + currentState + " to " + targetState
                            + ". Only ACTIVE documents can be archived, revoked, or superseded.");
        }
    }

    private String computeSha256(MultipartFile file) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(file.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to compute SHA-256 hash: " + e.getMessage(), e);
        }
    }

    private void publishOutboxEvent(String aggregateType, String aggregateId,
                                     String eventType, DocumentEntity entity) {
        EventOutboxEntity outbox = new EventOutboxEntity();
        outbox.setAggregateType(aggregateType);
        outbox.setAggregateId(aggregateId);
        outbox.setEventType(eventType);

        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("documentId", entity.getDocumentId().toString());
            payload.put("tenantId", entity.getTenantId().toString());
            payload.put("subjectType", entity.getSubjectType());
            payload.put("subjectId", entity.getSubjectId());
            payload.put("documentTypeCode", entity.getDocumentTypeCode());
            payload.put("lifecycleState", entity.getLifecycleState());
            payload.put("storageProvider", entity.getStorageProvider());
            payload.put("mimeType", entity.getMimeType());
            payload.put("eventType", eventType);
            outbox.setPayload(objectMapper.writeValueAsString(payload));
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize outbox event payload: {}", e.getMessage());
            outbox.setPayload("{}");
        }

        outboxRepository.save(outbox);
    }

    private Map<String, Object> buildFhirDocumentReference(DocumentEntity entity) {
        Map<String, Object> resource = new LinkedHashMap<>();
        resource.put("resourceType", "DocumentReference");
        resource.put("status", mapLifecycleToFhirStatus(entity.getLifecycleState()));

        // Type coding
        if (entity.getDocumentTypeCode() != null) {
            Map<String, Object> type = new LinkedHashMap<>();
            type.put("coding", List.of(Map.of(
                    "system", "http://impilo.gov.zw/fhir/document-type",
                    "code", entity.getDocumentTypeCode())));
            resource.put("type", type);
        }

        // Subject reference using CPID (no PII in SHR)
        Map<String, Object> subject = new LinkedHashMap<>();
        subject.put("reference", entity.getSubjectType() + "/" + entity.getSubjectId());
        resource.put("subject", subject);

        // Content
        Map<String, Object> attachment = new LinkedHashMap<>();
        attachment.put("contentType", entity.getMimeType());
        attachment.put("title", entity.getFileName());
        attachment.put("hash", entity.getHashSha256());
        if (entity.getFileSizeBytes() != null) {
            attachment.put("size", entity.getFileSizeBytes());
        }

        Map<String, Object> content = new LinkedHashMap<>();
        content.put("attachment", attachment);
        resource.put("content", List.of(content));

        // Security label (confidentiality)
        resource.put("securityLabel", List.of(Map.of(
                "coding", List.of(Map.of(
                        "system", "http://terminology.hl7.org/CodeSystem/v3-Confidentiality",
                        "code", mapConfidentiality(entity.getConfidentiality()))))));

        // Identifier with document UUID
        resource.put("identifier", List.of(Map.of(
                "system", "http://impilo.gov.zw/fhir/document-id",
                "value", entity.getDocumentId().toString())));

        return resource;
    }

    private String mapLifecycleToFhirStatus(String lifecycleState) {
        return switch (lifecycleState) {
            case "ACTIVE" -> "current";
            case "ARCHIVED" -> "current";
            case "REVOKED" -> "entered-in-error";
            case "SUPERSEDED" -> "superseded";
            case "DELETED" -> "entered-in-error";
            default -> "current";
        };
    }

    private String mapConfidentiality(String confidentiality) {
        return switch (confidentiality) {
            case "RESTRICTED" -> "R";
            case "VERY_RESTRICTED" -> "V";
            default -> "N";
        };
    }

    private DocumentResponse toResponse(DocumentEntity entity) {
        Map<String, Object> tagsMap;
        try {
            tagsMap = objectMapper.readValue(
                    entity.getTags() != null ? entity.getTags() : "{}",
                    new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            tagsMap = Map.of();
        }

        return new DocumentResponse(
                entity.getDocumentId(),
                entity.getTenantId(),
                entity.getSubjectType(),
                entity.getSubjectId(),
                entity.getDocumentTypeCode(),
                entity.getSource(),
                entity.getIssuer(),
                entity.getConfidentiality(),
                entity.getHashSha256(),
                entity.getMimeType(),
                entity.getFileName(),
                entity.getFileSizeBytes(),
                entity.getStorageProvider(),
                entity.getLifecycleState(),
                tagsMap,
                entity.getCreatedBy(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getVersion());
    }

    /**
     * Result wrapper for proxy download operations.
     */
    public record DownloadResult(
            InputStream inputStream,
            String mimeType,
            String fileName,
            Long fileSizeBytes
    ) {}
}
