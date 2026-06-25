package zw.gov.mohcc.impilo.zibo.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.zibo.domain.ArtifactStatus;
import zw.gov.mohcc.impilo.zibo.domain.ArtifactType;
import zw.gov.mohcc.impilo.zibo.persistence.entity.ArtifactEntity;
import zw.gov.mohcc.impilo.zibo.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.zibo.persistence.repository.ArtifactRepository;
import zw.gov.mohcc.impilo.zibo.persistence.repository.EventOutboxRepository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.*;

/**
 * Manages the full lifecycle of FHIR terminology artifacts (CodeSystem,
 * ValueSet, ConceptMap, NamingSystem, StructureDefinition, ImplementationGuide).
 *
 * <p>Artifacts follow a strict state machine: {@code DRAFT -> PUBLISHED ->
 * DEPRECATED -> RETIRED}. Content is immutable once published; only draft
 * artifacts may have their content updated. Every state transition is
 * recorded via the transactional outbox for reliable Kafka delivery.</p>
 *
 * <p>When a ConceptMap artifact is published, the mapping index is
 * automatically rebuilt via {@link MappingService#rebuildIndex(UUID)}.</p>
 *
 * <p>All operations are tenant-scoped using {@link TrustContextHolder}.</p>
 */
@Service
public class ArtifactService {

    private static final Logger log = LoggerFactory.getLogger(ArtifactService.class);

    private final ArtifactRepository artifactRepository;
    private final EventOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    /** Lazily set via {@link #setMappingService} to break circular dependency. */
    private MappingService mappingService;

    public ArtifactService(ArtifactRepository artifactRepository,
                           EventOutboxRepository outboxRepository,
                           ObjectMapper objectMapper) {
        this.artifactRepository = artifactRepository;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Setter for MappingService to break the circular dependency between
     * ArtifactService and MappingService. Called by Spring post-construction.
     *
     * @param mappingService the mapping service instance
     */
    void setMappingService(MappingService mappingService) {
        this.mappingService = mappingService;
    }

    /**
     * Create a new terminology artifact in DRAFT status.
     *
     * <p>Validates that the combination of canonical URL and version is
     * unique within the current tenant. Computes a SHA-256 hash of the
     * content for integrity verification.</p>
     *
     * @param fhirType     the FHIR resource type (e.g. CODE_SYSTEM, VALUE_SET)
     * @param canonicalUrl the canonical URL identifying this artifact
     * @param version      the semantic version string
     * @param name         the computer-friendly name
     * @param title        the human-readable title
     * @param description  a narrative description of the artifact
     * @param contentJson  the full FHIR resource JSON content
     * @param publisher    the publisher organization or entity name
     * @return the persisted artifact entity
     * @throws IllegalArgumentException if the canonical URL + version already exists for this tenant
     */
    @Transactional
    public ArtifactEntity createDraft(ArtifactType fhirType, String canonicalUrl, String version,
                                      String name, String title, String description,
                                      String contentJson, String publisher) {
        return createDraft(fhirType, canonicalUrl, version, name, title, description,
                contentJson, publisher, null, null);
    }

    /**
     * Create a new terminology artifact in DRAFT status with an explicit
     * clinical applicability window.
     *
     * <p>Identical to {@link #createDraft(ArtifactType, String, String, String,
     * String, String, String, String)} but additionally records the
     * {@code effectiveStart}/{@code effectiveEnd} window — used by governed
     * {@code OBSERVATION_DEFINITION} artifacts to express when a reference
     * interval is effective. Both bounds are nullable (open-ended).</p>
     *
     * @param effectiveStart inclusive start of applicability (nullable = no lower bound)
     * @param effectiveEnd   exclusive end of applicability (nullable = no upper bound)
     */
    @Transactional
    public ArtifactEntity createDraft(ArtifactType fhirType, String canonicalUrl, String version,
                                      String name, String title, String description,
                                      String contentJson, String publisher,
                                      OffsetDateTime effectiveStart, OffsetDateTime effectiveEnd) {
        TrustContext ctx = TrustContextHolder.require();
        UUID tenantId = ctx.tenantId();

        // Validate uniqueness of canonical URL + version within the tenant
        if (artifactRepository.existsByTenantIdAndCanonicalUrlAndVersion(tenantId, canonicalUrl, version)) {
            throw new IllegalArgumentException(
                    "Artifact already exists for tenant with canonicalUrl=" + canonicalUrl
                            + " and version=" + version);
        }

        ArtifactEntity artifact = new ArtifactEntity();
        artifact.setId(UUID.randomUUID());
        artifact.setTenantId(tenantId);
        artifact.setFhirType(fhirType);
        artifact.setCanonicalUrl(canonicalUrl);
        artifact.setVersion(version);
        artifact.setName(name);
        artifact.setTitle(title);
        artifact.setDescription(description);
        artifact.setContentJson(contentJson);
        artifact.setContentHash(computeHash(contentJson));
        artifact.setPublisher(publisher);
        artifact.setStatus(ArtifactStatus.DRAFT);
        artifact.setEffectiveStart(effectiveStart);
        artifact.setEffectiveEnd(effectiveEnd);
        artifact.setCreatedBy(ctx.actorId());
        artifact.setCreatedAt(OffsetDateTime.now());
        artifact.setUpdatedAt(OffsetDateTime.now());

        artifact = artifactRepository.save(artifact);

        writeOutbox("ARTIFACT", artifact.getId().toString(), "ARTIFACT_CREATED",
                buildPayload(artifact, "ARTIFACT_CREATED"));

        log.info("Draft artifact created: id={}, type={}, url={}, version={}, tenant={}",
                artifact.getId(), fhirType, canonicalUrl, version, tenantId);

        return artifact;
    }

    /**
     * Update a draft artifact's metadata and content.
     *
     * <p>Only artifacts in {@link ArtifactStatus#DRAFT} status can be updated.
     * The content hash is recomputed on every update.</p>
     *
     * @param artifactId  the artifact to update
     * @param name        updated computer-friendly name
     * @param title       updated human-readable title
     * @param description updated narrative description
     * @param contentJson updated FHIR resource JSON content
     * @return the updated artifact entity
     * @throws IllegalArgumentException if the artifact is not found for this tenant
     * @throws IllegalStateException    if the artifact is not in DRAFT status
     */
    @Transactional
    public ArtifactEntity updateDraft(UUID artifactId, String name, String title,
                                      String description, String contentJson) {
        TrustContext ctx = TrustContextHolder.require();

        ArtifactEntity artifact = findByTenantAndId(ctx.tenantId(), artifactId);

        if (artifact.getStatus() != ArtifactStatus.DRAFT) {
            throw new IllegalStateException(
                    "Only DRAFT artifacts can be updated. Current status: " + artifact.getStatus());
        }

        artifact.setName(name);
        artifact.setTitle(title);
        artifact.setDescription(description);
        artifact.setContentJson(contentJson);
        artifact.setContentHash(computeHash(contentJson));
        artifact.setUpdatedAt(OffsetDateTime.now());

        artifact = artifactRepository.save(artifact);

        writeOutbox("ARTIFACT", artifactId.toString(), "ARTIFACT_UPDATED",
                buildPayload(artifact, "ARTIFACT_UPDATED"));

        log.info("Draft artifact updated: id={}, url={}, version={}", artifactId,
                artifact.getCanonicalUrl(), artifact.getVersion());

        return artifact;
    }

    /**
     * Publish an artifact, transitioning it from DRAFT (or ACTIVE) to PUBLISHED.
     *
     * <p>After publication the artifact's content becomes immutable. If the
     * artifact is a ConceptMap, the mapping index is automatically rebuilt.</p>
     *
     * @param artifactId  the artifact to publish
     * @param publishedBy the identity of the publisher (actor ID)
     * @return the published artifact entity
     * @throws IllegalArgumentException if the artifact is not found for this tenant
     * @throws IllegalStateException    if the artifact is not in DRAFT or ACTIVE status
     */
    @Transactional
    public ArtifactEntity publish(UUID artifactId, String publishedBy) {
        TrustContext ctx = TrustContextHolder.require();

        ArtifactEntity artifact = findByTenantAndId(ctx.tenantId(), artifactId);

        if (artifact.getStatus() != ArtifactStatus.DRAFT && artifact.getStatus() != ArtifactStatus.ACTIVE) {
            throw new IllegalStateException(
                    "Only DRAFT or ACTIVE artifacts can be published. Current status: " + artifact.getStatus());
        }

        artifact.setStatus(ArtifactStatus.PUBLISHED);
        artifact.setPublishedBy(publishedBy);
        artifact.setPublishedAt(OffsetDateTime.now());
        artifact.setUpdatedAt(OffsetDateTime.now());

        artifact = artifactRepository.save(artifact);

        writeOutbox("ARTIFACT", artifactId.toString(), "ARTIFACT_PUBLISHED",
                buildPayload(artifact, "ARTIFACT_PUBLISHED"));

        log.info("Artifact published: id={}, url={}, version={}, publishedBy={}",
                artifactId, artifact.getCanonicalUrl(), artifact.getVersion(), publishedBy);

        // Rebuild mapping index if this is a ConceptMap
        if (artifact.getFhirType() == ArtifactType.CONCEPT_MAP && mappingService != null) {
            try {
                mappingService.rebuildIndex(artifactId);
                log.info("Mapping index rebuilt for ConceptMap artifact: {}", artifactId);
            } catch (Exception e) {
                log.error("Failed to rebuild mapping index for ConceptMap {}: {}",
                        artifactId, e.getMessage(), e);
            }
        }

        return artifact;
    }

    /**
     * Deprecate a published artifact, transitioning it from PUBLISHED to DEPRECATED.
     *
     * <p>Deprecated artifacts remain queryable but are flagged as no longer
     * recommended for new use.</p>
     *
     * @param artifactId the artifact to deprecate
     * @return the deprecated artifact entity
     * @throws IllegalArgumentException if the artifact is not found for this tenant
     * @throws IllegalStateException    if the artifact is not in PUBLISHED status
     */
    @Transactional
    public ArtifactEntity deprecate(UUID artifactId) {
        TrustContext ctx = TrustContextHolder.require();

        ArtifactEntity artifact = findByTenantAndId(ctx.tenantId(), artifactId);

        if (artifact.getStatus() != ArtifactStatus.PUBLISHED) {
            throw new IllegalStateException(
                    "Only PUBLISHED artifacts can be deprecated. Current status: " + artifact.getStatus());
        }

        artifact.setStatus(ArtifactStatus.DEPRECATED);
        artifact.setDeprecatedAt(OffsetDateTime.now());
        artifact.setUpdatedAt(OffsetDateTime.now());

        artifact = artifactRepository.save(artifact);

        writeOutbox("ARTIFACT", artifactId.toString(), "ARTIFACT_DEPRECATED",
                buildPayload(artifact, "ARTIFACT_DEPRECATED"));

        log.info("Artifact deprecated: id={}, url={}, version={}",
                artifactId, artifact.getCanonicalUrl(), artifact.getVersion());

        return artifact;
    }

    /**
     * Retire a deprecated artifact, transitioning it from DEPRECATED to RETIRED.
     *
     * <p>Retired artifacts are end-of-life and should no longer be referenced
     * by any active packs or assignments.</p>
     *
     * @param artifactId the artifact to retire
     * @return the retired artifact entity
     * @throws IllegalArgumentException if the artifact is not found for this tenant
     * @throws IllegalStateException    if the artifact is not in DEPRECATED status
     */
    @Transactional
    public ArtifactEntity retire(UUID artifactId) {
        TrustContext ctx = TrustContextHolder.require();

        ArtifactEntity artifact = findByTenantAndId(ctx.tenantId(), artifactId);

        if (artifact.getStatus() != ArtifactStatus.DEPRECATED) {
            throw new IllegalStateException(
                    "Only DEPRECATED artifacts can be retired. Current status: " + artifact.getStatus());
        }

        artifact.setStatus(ArtifactStatus.RETIRED);
        artifact.setUpdatedAt(OffsetDateTime.now());

        artifact = artifactRepository.save(artifact);

        writeOutbox("ARTIFACT", artifactId.toString(), "ARTIFACT_RETIRED",
                buildPayload(artifact, "ARTIFACT_RETIRED"));

        log.info("Artifact retired: id={}, url={}, version={}",
                artifactId, artifact.getCanonicalUrl(), artifact.getVersion());

        return artifact;
    }

    /**
     * Retrieve a single artifact by its ID within the current tenant.
     *
     * @param artifactId the artifact identifier
     * @return the artifact entity
     * @throws IllegalArgumentException if the artifact is not found for this tenant
     */
    @Transactional(readOnly = true)
    public ArtifactEntity getArtifact(UUID artifactId) {
        TrustContext ctx = TrustContextHolder.require();
        return findByTenantAndId(ctx.tenantId(), artifactId);
    }

    /**
     * List all artifacts for the current tenant with pagination.
     *
     * @param pageable pagination parameters
     * @return a page of artifact entities
     */
    @Transactional(readOnly = true)
    public Page<ArtifactEntity> listArtifacts(Pageable pageable) {
        TrustContext ctx = TrustContextHolder.require();
        return artifactRepository.findByTenantId(ctx.tenantId(), pageable);
    }

    /**
     * List all artifacts of a specific FHIR type for the current tenant.
     *
     * @param type the FHIR artifact type to filter by
     * @return a list of matching artifact entities
     */
    @Transactional(readOnly = true)
    public List<ArtifactEntity> listByType(ArtifactType type) {
        TrustContext ctx = TrustContextHolder.require();
        return artifactRepository.findByTenantIdAndFhirType(ctx.tenantId(), type);
    }

    /**
     * List all versions of an artifact identified by its canonical URL.
     *
     * @param canonicalUrl the canonical URL
     * @return a list of all version entities for that canonical URL within the tenant
     */
    @Transactional(readOnly = true)
    public List<ArtifactEntity> listVersions(String canonicalUrl) {
        TrustContext ctx = TrustContextHolder.require();
        return artifactRepository.findByTenantIdAndCanonicalUrl(ctx.tenantId(), canonicalUrl);
    }

    /**
     * Retrieve a specific artifact by canonical URL and version within the current tenant.
     *
     * @param canonicalUrl the canonical URL
     * @param version      the version string
     * @return the matching artifact entity
     * @throws IllegalArgumentException if no artifact matches the given URL and version
     */
    @Transactional(readOnly = true)
    public ArtifactEntity getByCanonicalAndVersion(String canonicalUrl, String version) {
        TrustContext ctx = TrustContextHolder.require();
        return artifactRepository.findByTenantIdAndCanonicalUrlAndVersion(ctx.tenantId(), canonicalUrl, version)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Artifact not found: canonicalUrl=" + canonicalUrl + ", version=" + version));
    }

    // ------------------------------------------------------------------
    // Internal helpers
    // ------------------------------------------------------------------

    /**
     * Compute SHA-256 hash of the given content string.
     *
     * @param content the content to hash
     * @return the hex-encoded SHA-256 digest
     */
    static String computeHash(String content) {
        if (content == null || content.isEmpty()) {
            return "";
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder(2 * hash.length);
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }

    private ArtifactEntity findByTenantAndId(UUID tenantId, UUID artifactId) {
        return artifactRepository.findByTenantIdAndArtifactId(tenantId, artifactId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Artifact not found: " + artifactId + " for tenant " + tenantId));
    }

    private void writeOutbox(String aggregateType, String aggregateId,
                             String eventType, String payloadJson) {
        EventOutboxEntity outbox = new EventOutboxEntity();
        outbox.setAggregateType(aggregateType);
        outbox.setAggregateId(aggregateId);
        outbox.setEventType(eventType);
        outbox.setPayload(payloadJson);
        outbox.setTenantId(TrustContextHolder.require().tenantId());
        outboxRepository.save(outbox);
    }

    private String buildPayload(ArtifactEntity artifact, String eventType) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("eventType", eventType);
        payload.put("artifactId", artifact.getId().toString());
        payload.put("tenantId", artifact.getTenantId().toString());
        payload.put("fhirType", artifact.getFhirType().name());
        payload.put("canonicalUrl", artifact.getCanonicalUrl());
        payload.put("version", artifact.getVersion());
        payload.put("name", artifact.getName());
        payload.put("status", artifact.getStatus().name());
        payload.put("contentHash", artifact.getContentHash());
        if (artifact.getPublishedBy() != null) {
            payload.put("publishedBy", artifact.getPublishedBy());
        }
        if (artifact.getPublishedAt() != null) {
            payload.put("publishedAt", artifact.getPublishedAt().toString());
        }
        if (artifact.getDeprecatedAt() != null) {
            payload.put("deprecatedAt", artifact.getDeprecatedAt().toString());
        }
        payload.put("timestamp", OffsetDateTime.now().toString());
        return toJson(payload);
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialise outbox payload: {}", e.getMessage());
            return "{}";
        }
    }
}
