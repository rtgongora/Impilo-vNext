package zw.gov.mohcc.impilo.credential.core;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.credential.api.dto.*;
import zw.gov.mohcc.impilo.credential.config.CredentialProperties;
import zw.gov.mohcc.impilo.credential.persistence.entity.CredentialEntity;
import zw.gov.mohcc.impilo.credential.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.credential.persistence.entity.VerificationLogEntity;
import zw.gov.mohcc.impilo.credential.persistence.repository.CredentialRepository;
import zw.gov.mohcc.impilo.credential.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.credential.persistence.repository.VerificationLogRepository;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.PagedResponse;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

/**
 * Core credential lifecycle service.
 *
 * Issues digitally signed credentials, manages revocation and supersession,
 * provides verification, and publishes outbox events for Kafka delivery.
 */
@Service
public class CredentialService {

    private static final Logger log = LoggerFactory.getLogger(CredentialService.class);

    private final CredentialRepository credentialRepository;
    private final VerificationLogRepository verificationLogRepository;
    private final EventOutboxRepository eventOutboxRepository;
    private final SigningService signingService;
    private final CredentialProperties properties;
    private final ObjectMapper objectMapper;
    private final SecureRandom secureRandom = new SecureRandom();

    public CredentialService(CredentialRepository credentialRepository,
                             VerificationLogRepository verificationLogRepository,
                             EventOutboxRepository eventOutboxRepository,
                             SigningService signingService,
                             CredentialProperties properties,
                             ObjectMapper objectMapper) {
        this.credentialRepository = credentialRepository;
        this.verificationLogRepository = verificationLogRepository;
        this.eventOutboxRepository = eventOutboxRepository;
        this.signingService = signingService;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    /**
     * Issues a new digitally signed credential.
     */
    @Transactional
    public CredentialResponse issueCredential(IssueCredentialRequest request) {
        var trustContext = TrustContextHolder.require();

        // Generate verification token (32 random bytes -> 64 hex chars)
        String verificationToken = generateVerificationToken();

        // Build signed payload
        UUID credentialId = UUID.randomUUID();
        String signedPayload = buildSignedPayload(credentialId, request, trustContext.tenantId());

        // Sign the payload with Ed25519
        String signatureHex = signingService.sign(signedPayload.getBytes(StandardCharsets.UTF_8));

        // Serialize metadata
        String metadataJson = serializeMetadata(request.metadata());

        // Build entity
        CredentialEntity entity = new CredentialEntity();
        entity.setCredentialId(credentialId);
        entity.setTenantId(trustContext.tenantId());
        entity.setSubjectType(request.subjectType());
        entity.setSubjectId(request.subjectId());
        entity.setSubjectName(request.subjectName());
        entity.setCredentialType(request.credentialType());
        entity.setTitle(request.title());
        entity.setIssuedBy(request.issuedBy());
        entity.setIssuedAt(Instant.now());
        entity.setValidFrom(request.validFrom());
        entity.setValidTo(request.validTo());
        entity.setStatus("ACTIVE");
        entity.setVerificationToken(verificationToken);
        entity.setSignatureHex(signatureHex);
        entity.setSignedPayload(signedPayload);
        entity.setMetadata(metadataJson);
        entity.setCreatedBy(trustContext.actorId());

        CredentialEntity saved = credentialRepository.save(entity);

        // Publish outbox event
        publishOutboxEvent("CREDENTIAL", saved.getCredentialId().toString(),
                "credential.issued", buildEventPayload(saved));

        log.info("Issued credential {} for subject {}:{}", saved.getCredentialId(),
                request.subjectType(), request.subjectId());

        return toResponse(saved);
    }

    /**
     * Returns true when the facility has at least one ACTIVE credential that is within its validity window.
     * Used by MUSHEX and other payment rails before funds are released to a provider site.
     */
    @Transactional(readOnly = true)
    public boolean facilityHasActiveCredential(UUID tenantId, UUID facilityId) {
        LocalDate today = LocalDate.now();
        return credentialRepository.countActiveFacilityCredentials(tenantId, facilityId.toString(), today) > 0;
    }

    /**
     * Retrieves a credential by its UUID.
     */
    @Transactional(readOnly = true)
    public CredentialResponse getCredential(UUID credentialId) {
        CredentialEntity entity = credentialRepository.findByCredentialId(credentialId)
                .orElseThrow(() -> new NoSuchElementException("Credential not found: " + credentialId));
        return toResponse(entity);
    }

    /**
     * Searches credentials with optional filters.
     */
    @Transactional(readOnly = true)
    public PagedResponse<CredentialResponse> searchCredentials(CredentialSearchRequest request) {
        var trustContext = TrustContextHolder.require();
        PageRequest pageable = PageRequest.of(request.resolvedPage(), request.resolvedSize());

        Page<CredentialEntity> page = credentialRepository.searchCredentials(
                trustContext.tenantId(),
                request.subjectType(),
                request.subjectId(),
                request.credentialType(),
                request.status(),
                pageable);

        List<CredentialResponse> items = page.getContent().stream()
                .map(this::toResponse)
                .toList();

        return PagedResponse.of(items, request.resolvedPage(), request.resolvedSize(), page.getTotalElements());
    }

    /**
     * Revokes a credential and publishes a revocation event.
     */
    @Transactional
    public CredentialResponse revokeCredential(UUID credentialId, RevokeCredentialRequest request) {
        var trustContext = TrustContextHolder.require();

        CredentialEntity entity = credentialRepository.findByCredentialId(credentialId)
                .orElseThrow(() -> new NoSuchElementException("Credential not found: " + credentialId));

        if (!entity.isActive()) {
            throw new IllegalStateException("Cannot revoke credential in status: " + entity.getStatus());
        }

        entity.setStatus("REVOKED");
        entity.setRevokedAt(Instant.now());
        entity.setRevokedBy(trustContext.actorId());
        entity.setRevocationReason(request.reason());

        CredentialEntity saved = credentialRepository.save(entity);

        // Publish outbox event
        publishOutboxEvent("CREDENTIAL", saved.getCredentialId().toString(),
                "credential.revoked", buildEventPayload(saved));

        log.info("Revoked credential {} by {}", credentialId, trustContext.actorId());

        return toResponse(saved);
    }

    /**
     * Supersedes a credential with a new one.
     */
    @Transactional
    public CredentialResponse supersedeCredential(UUID credentialId, UUID newCredentialId) {
        CredentialEntity existing = credentialRepository.findByCredentialId(credentialId)
                .orElseThrow(() -> new NoSuchElementException("Credential not found: " + credentialId));

        CredentialEntity newCredential = credentialRepository.findByCredentialId(newCredentialId)
                .orElseThrow(() -> new NoSuchElementException("New credential not found: " + newCredentialId));

        if (!existing.isActive()) {
            throw new IllegalStateException("Cannot supersede credential in status: " + existing.getStatus());
        }

        existing.setStatus("SUPERSEDED");
        existing.setSupersededBy(newCredential.getId());

        CredentialEntity saved = credentialRepository.save(existing);

        log.info("Superseded credential {} with {}", credentialId, newCredentialId);

        return toResponse(saved);
    }

    /**
     * Verifies a credential by its public verification token.
     * Logs the verification attempt and returns a minimal result (no PII leakage).
     */
    @Transactional
    public VerificationResult verifyByToken(String token, String verifierIp, String verifierAgent) {
        Optional<CredentialEntity> optEntity = credentialRepository.findByVerificationToken(token);

        if (optEntity.isEmpty()) {
            logVerification(null, token, "NOT_FOUND", verifierIp, verifierAgent);
            return new VerificationResult("NOT_FOUND", null, null, null, null, null, null, Instant.now());
        }

        CredentialEntity entity = optEntity.get();
        String resultStatus = determineVerificationStatus(entity);

        logVerification(entity.getCredentialId(), token, resultStatus, verifierIp, verifierAgent);

        // Publish verification event
        publishOutboxEvent("CREDENTIAL", entity.getCredentialId().toString(),
                "credential.verified", buildVerificationEventPayload(entity, resultStatus));

        return new VerificationResult(
                resultStatus,
                entity.getCredentialType(),
                entity.getSubjectName(),
                entity.getTitle(),
                entity.getIssuedBy(),
                entity.getValidFrom(),
                entity.getValidTo(),
                Instant.now()
        );
    }

    /**
     * Retrieves the entity for PDF generation.
     */
    @Transactional(readOnly = true)
    public CredentialEntity getCredentialEntity(UUID credentialId) {
        return credentialRepository.findByCredentialId(credentialId)
                .orElseThrow(() -> new NoSuchElementException("Credential not found: " + credentialId));
    }

    // --- Private helpers ---

    private String generateVerificationToken() {
        byte[] tokenBytes = new byte[32];
        secureRandom.nextBytes(tokenBytes);
        return HexFormat.of().formatHex(tokenBytes);
    }

    private String buildSignedPayload(UUID credentialId, IssueCredentialRequest request, UUID tenantId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("credentialId", credentialId.toString());
        payload.put("tenantId", tenantId.toString());
        payload.put("subjectType", request.subjectType());
        payload.put("subjectId", request.subjectId());
        payload.put("subjectName", request.subjectName());
        payload.put("credentialType", request.credentialType());
        payload.put("title", request.title());
        payload.put("issuedBy", request.issuedBy());
        payload.put("validFrom", request.validFrom().toString());
        if (request.validTo() != null) {
            payload.put("validTo", request.validTo().toString());
        }
        payload.put("issuedAt", Instant.now().toString());

        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize signed payload", e);
        }
    }

    private String serializeMetadata(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return "{}";
        }
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (Exception e) {
            log.warn("Failed to serialize metadata, defaulting to empty: {}", e.getMessage());
            return "{}";
        }
    }

    private Map<String, Object> deserializeMetadata(String metadataJson) {
        if (metadataJson == null || metadataJson.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(metadataJson, new TypeReference<>() {});
        } catch (Exception e) {
            log.warn("Failed to deserialize metadata: {}", e.getMessage());
            return Map.of();
        }
    }

    private String determineVerificationStatus(CredentialEntity entity) {
        if (entity.isRevoked()) {
            return "REVOKED";
        }
        if (entity.isExpired()) {
            return "EXPIRED";
        }
        if (entity.isActive()) {
            return "VALID";
        }
        return entity.getStatus();
    }

    private void logVerification(UUID credentialId, String token, String result,
                                 String verifierIp, String verifierAgent) {
        VerificationLogEntity logEntry = new VerificationLogEntity();
        logEntry.setCredentialId(credentialId != null ? credentialId : UUID.fromString("00000000-0000-0000-0000-000000000000"));
        logEntry.setVerificationToken(token);
        logEntry.setResult(result);
        logEntry.setVerifierIp(verifierIp);
        logEntry.setVerifierAgent(verifierAgent != null && verifierAgent.length() > 500
                ? verifierAgent.substring(0, 500) : verifierAgent);
        verificationLogRepository.save(logEntry);
    }

    private void publishOutboxEvent(String aggregateType, String aggregateId,
                                    String eventType, String payload) {
        EventOutboxEntity event = new EventOutboxEntity();
        event.setAggregateType(aggregateType);
        event.setAggregateId(aggregateId);
        event.setEventType(eventType);
        event.setPayload(payload);
        eventOutboxRepository.save(event);
    }

    private String buildEventPayload(CredentialEntity entity) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("credentialId", entity.getCredentialId().toString());
        payload.put("tenantId", entity.getTenantId().toString());
        payload.put("subjectType", entity.getSubjectType());
        payload.put("subjectId", entity.getSubjectId());
        payload.put("credentialType", entity.getCredentialType());
        payload.put("status", entity.getStatus());
        payload.put("timestamp", Instant.now().toString());

        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            return "{}";
        }
    }

    private String buildVerificationEventPayload(CredentialEntity entity, String result) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("credentialId", entity.getCredentialId().toString());
        payload.put("credentialType", entity.getCredentialType());
        payload.put("verificationResult", result);
        payload.put("timestamp", Instant.now().toString());

        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            return "{}";
        }
    }

    private CredentialResponse toResponse(CredentialEntity entity) {
        String verificationUrl = properties.getVerify().getBaseUrl() + "/" + entity.getVerificationToken();

        return new CredentialResponse(
                entity.getCredentialId(),
                entity.getTenantId(),
                entity.getSubjectType(),
                entity.getSubjectId(),
                entity.getSubjectName(),
                entity.getCredentialType(),
                entity.getTitle(),
                entity.getIssuedBy(),
                entity.getIssuedAt(),
                entity.getValidFrom(),
                entity.getValidTo(),
                entity.getStatus(),
                verificationUrl,
                entity.getPdfDocumentId(),
                deserializeMetadata(entity.getMetadata()),
                entity.getCreatedAt()
        );
    }
}
