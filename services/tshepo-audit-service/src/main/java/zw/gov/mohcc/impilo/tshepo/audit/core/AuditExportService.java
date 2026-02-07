package zw.gov.mohcc.impilo.tshepo.audit.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.tshepo.audit.api.dto.AuditExportRequest;
import zw.gov.mohcc.impilo.tshepo.audit.api.dto.AuditExportResponse;
import zw.gov.mohcc.impilo.tshepo.audit.api.dto.ExportVerificationResponse;
import zw.gov.mohcc.impilo.tshepo.audit.api.dto.SignRequest;
import zw.gov.mohcc.impilo.tshepo.audit.api.dto.SignResponse;
import zw.gov.mohcc.impilo.tshepo.audit.persistence.entity.AuditEventEntity;
import zw.gov.mohcc.impilo.tshepo.audit.persistence.entity.AuditExportEntity;
import zw.gov.mohcc.impilo.tshepo.audit.persistence.repository.AuditEventRepository;
import zw.gov.mohcc.impilo.tshepo.audit.persistence.repository.AuditExportRepository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for creating and verifying signed audit export bundles.
 *
 * An export captures a range of audit events, computes an aggregate SHA-256 hash
 * over all entry hashes, and obtains an Ed25519 signature from tshepo-keys-service.
 * This provides legal defensibility: the signed bundle proves the events existed
 * at export time and have not been tampered with.
 */
@Service
public class AuditExportService {

    private static final Logger log = LoggerFactory.getLogger(AuditExportService.class);

    private final AuditEventRepository eventRepository;
    private final AuditExportRepository exportRepository;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${tshepo-audit.keys-service.base-url}")
    private String keysServiceBaseUrl;

    @Value("${tshepo-audit.keys-service.sign-endpoint}")
    private String signEndpoint;

    public AuditExportService(AuditEventRepository eventRepository,
                               AuditExportRepository exportRepository,
                               RestTemplate restTemplate,
                               ObjectMapper objectMapper) {
        this.eventRepository = eventRepository;
        this.exportRepository = exportRepository;
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Create a signed audit export bundle for the specified sequence range.
     *
     * @param request export request with tenant, range, and requester
     * @return the export response with bundle hash and signature
     * @throws IllegalArgumentException if fromSequence > toSequence
     * @throws IllegalStateException if no events found in range or signing fails
     */
    @Transactional
    public AuditExportResponse createExport(AuditExportRequest request) {
        if (request.fromSequence() > request.toSequence()) {
            throw new IllegalArgumentException("fromSequence must be <= toSequence");
        }

        // Fetch all events in the range
        List<AuditEventEntity> events = eventRepository
                .findByTenantIdAndSequenceNumberBetween(
                        request.tenantId(), request.fromSequence(), request.toSequence());

        if (events.isEmpty()) {
            throw new IllegalStateException("No audit events found in sequence range ["
                    + request.fromSequence() + ", " + request.toSequence() + "]");
        }

        // Compute aggregate bundle hash over all entry hashes
        String bundleHash = computeBundleHash(events);

        // Sign the bundle hash via tshepo-keys-service
        String signature = signBundle(bundleHash);

        // Persist the export record
        AuditExportEntity export = new AuditExportEntity();
        export.setTenantId(request.tenantId());
        export.setRequestedBy(request.requestedBy());
        export.setFromSequence(request.fromSequence());
        export.setToSequence(request.toSequence());
        export.setEntryCount(events.size());
        export.setBundleHash(bundleHash);
        export.setSignature(signature);
        export.setStatus("COMPLETED");
        export.setCreatedAt(Instant.now());

        AuditExportEntity saved = exportRepository.save(export);

        log.info("Created audit export: id={}, tenant={}, range=[{},{}], entries={}, hash={}",
                saved.getId(), request.tenantId(), request.fromSequence(),
                request.toSequence(), events.size(), bundleHash);

        return toResponse(saved);
    }

    /**
     * Retrieve an export by ID, scoped to the given tenant.
     */
    @Transactional(readOnly = true)
    public Optional<AuditExportResponse> getExport(UUID tenantId, UUID exportId) {
        return exportRepository.findByIdAndTenantId(exportId, tenantId)
                .map(this::toResponse);
    }

    /**
     * Verify the integrity of a previously created export bundle.
     * Re-fetches the events, recomputes the bundle hash, and compares.
     */
    @Transactional(readOnly = true)
    public ExportVerificationResponse verifyExport(UUID tenantId, UUID exportId) {
        Optional<AuditExportEntity> exportOpt = exportRepository.findByIdAndTenantId(exportId, tenantId);
        if (exportOpt.isEmpty()) {
            return ExportVerificationResponse.notFound(exportId);
        }

        AuditExportEntity export = exportOpt.get();

        // Re-fetch events in the range
        List<AuditEventEntity> events = eventRepository
                .findByTenantIdAndSequenceNumberBetween(
                        tenantId, export.getFromSequence(), export.getToSequence());

        // Verify entry count matches
        if (events.size() != export.getEntryCount()) {
            return ExportVerificationResponse.hashMismatch(
                    exportId, export.getEntryCount(), events.size());
        }

        // Recompute bundle hash and compare
        String recomputedHash = computeBundleHash(events);
        if (!recomputedHash.equals(export.getBundleHash())) {
            return ExportVerificationResponse.hashMismatch(
                    exportId, export.getEntryCount(), events.size());
        }

        return ExportVerificationResponse.success(exportId, export.getEntryCount());
    }

    /**
     * Compute SHA-256 hash over the concatenation of all entry hashes.
     */
    private String computeBundleHash(List<AuditEventEntity> events) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (AuditEventEntity event : events) {
                digest.update(event.getEntryHash().getBytes(StandardCharsets.UTF_8));
            }
            byte[] hashBytes = digest.digest();
            return HexFormat.of().formatHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }

    /**
     * Sign the bundle hash via tshepo-keys-service POST /v1/sign.
     * Uses Ed25519 algorithm.
     */
    private String signBundle(String bundleHash) {
        String url = keysServiceBaseUrl + signEndpoint;
        SignRequest signRequest = SignRequest.ed25519(bundleHash);

        try {
            ResponseEntity<SignResponse> response = restTemplate.postForEntity(
                    url, signRequest, SignResponse.class);

            if (response.getBody() == null || response.getBody().signature() == null) {
                throw new IllegalStateException("tshepo-keys-service returned empty signature");
            }

            log.debug("Bundle signed successfully via keys-service, keyId={}",
                    response.getBody().keyId());
            return response.getBody().signature();
        } catch (RestClientException e) {
            log.error("Failed to sign audit export bundle via tshepo-keys-service: {}", e.getMessage());
            throw new IllegalStateException("Failed to sign audit export bundle: " + e.getMessage(), e);
        }
    }

    /**
     * Map export entity to response DTO.
     */
    private AuditExportResponse toResponse(AuditExportEntity entity) {
        return new AuditExportResponse(
                entity.getId(),
                entity.getTenantId(),
                entity.getRequestedBy(),
                entity.getFromSequence(),
                entity.getToSequence(),
                entity.getEntryCount(),
                entity.getBundleHash(),
                entity.getSignature(),
                entity.getStatus(),
                entity.getCreatedAt()
        );
    }
}
