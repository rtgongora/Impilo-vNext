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
import zw.gov.mohcc.impilo.zibo.persistence.entity.ArtifactEntity;
import zw.gov.mohcc.impilo.zibo.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.zibo.persistence.entity.PackArtifactEntity;
import zw.gov.mohcc.impilo.zibo.persistence.entity.PackEntity;
import zw.gov.mohcc.impilo.zibo.persistence.repository.ArtifactRepository;
import zw.gov.mohcc.impilo.zibo.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.zibo.persistence.repository.PackArtifactRepository;
import zw.gov.mohcc.impilo.zibo.persistence.repository.PackRepository;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Manages terminology packs — curated collections of FHIR artifacts that
 * can be assigned to tenants, facilities, or workspaces.
 *
 * <p>Packs follow the same lifecycle as artifacts: {@code DRAFT -> PUBLISHED
 * -> DEPRECATED}. Artifacts can only be added to or removed from packs while
 * in DRAFT status. A pack can only be published when all its constituent
 * artifacts are themselves in PUBLISHED status.</p>
 *
 * <p>All operations are tenant-scoped via {@link TrustContextHolder}.</p>
 */
@Service
public class PackService {

    private static final Logger log = LoggerFactory.getLogger(PackService.class);

    private final PackRepository packRepository;
    private final PackArtifactRepository packArtifactRepository;
    private final ArtifactRepository artifactRepository;
    private final EventOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public PackService(PackRepository packRepository,
                       PackArtifactRepository packArtifactRepository,
                       ArtifactRepository artifactRepository,
                       EventOutboxRepository outboxRepository,
                       ObjectMapper objectMapper) {
        this.packRepository = packRepository;
        this.packArtifactRepository = packArtifactRepository;
        this.artifactRepository = artifactRepository;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Create a new terminology pack in DRAFT status.
     *
     * <p>Validates that the name + version combination is unique within the
     * current tenant.</p>
     *
     * @param name         the pack name (computer-friendly)
     * @param version      the semantic version string
     * @param description  a narrative description of the pack's purpose
     * @param manifestJson optional JSON manifest describing the pack contents or metadata
     * @return the persisted pack entity
     * @throws IllegalArgumentException if the name + version already exists for this tenant
     */
    @Transactional
    public PackEntity createPack(String name, String version, String description, String manifestJson) {
        TrustContext ctx = TrustContextHolder.require();
        UUID tenantId = ctx.tenantId();

        // Validate uniqueness of name + version within the tenant
        if (packRepository.findByTenantIdAndNameAndVersion(tenantId, name, version).isPresent()) {
            throw new IllegalArgumentException(
                    "Pack already exists for tenant with name=" + name + " and version=" + version);
        }

        PackEntity pack = new PackEntity();
        pack.setId(UUID.randomUUID());
        pack.setTenantId(tenantId);
        pack.setName(name);
        pack.setVersion(version);
        pack.setDescription(description);
        pack.setManifestJson(manifestJson);
        pack.setStatus(ArtifactStatus.DRAFT);
        pack.setCreatedBy(ctx.actorId());
        pack.setCreatedAt(OffsetDateTime.now());
        pack.setUpdatedAt(OffsetDateTime.now());

        pack = packRepository.save(pack);

        log.info("Pack created: id={}, name={}, version={}, tenant={}",
                pack.getId(), name, version, tenantId);

        return pack;
    }

    /**
     * Add an artifact to a draft pack.
     *
     * <p>Both the pack and the artifact must exist within the same tenant.
     * The pack must be in DRAFT status. Duplicate artifact additions are
     * rejected.</p>
     *
     * @param packId     the pack to add the artifact to
     * @param artifactId the artifact to add
     * @return the pack-artifact association entity
     * @throws IllegalArgumentException if the pack or artifact is not found, or the artifact is already in the pack
     * @throws IllegalStateException    if the pack is not in DRAFT status
     */
    @Transactional
    public PackArtifactEntity addArtifact(UUID packId, UUID artifactId) {
        TrustContext ctx = TrustContextHolder.require();
        UUID tenantId = ctx.tenantId();

        PackEntity pack = findPackByTenantAndId(tenantId, packId);

        if (pack.getStatus() != ArtifactStatus.DRAFT) {
            throw new IllegalStateException(
                    "Only DRAFT packs can be modified. Current status: " + pack.getStatus());
        }

        // Validate the artifact exists in this tenant
        artifactRepository.findByTenantIdAndArtifactId(tenantId, artifactId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Artifact not found: " + artifactId + " for tenant " + tenantId));

        // Prevent duplicate entries
        if (packArtifactRepository.existsByPackIdAndArtifactId(packId, artifactId)) {
            throw new IllegalArgumentException(
                    "Artifact " + artifactId + " is already in pack " + packId);
        }

        PackArtifactEntity packArtifact = new PackArtifactEntity();
        packArtifact.setId(UUID.randomUUID());
        packArtifact.setPackId(packId);
        packArtifact.setArtifactId(artifactId);

        packArtifact = packArtifactRepository.save(packArtifact);

        // Update pack timestamp
        pack.setUpdatedAt(OffsetDateTime.now());
        packRepository.save(pack);

        log.info("Artifact {} added to pack {}", artifactId, packId);

        return packArtifact;
    }

    /**
     * Remove an artifact from a draft pack.
     *
     * <p>The pack must be in DRAFT status.</p>
     *
     * @param packId     the pack to remove the artifact from
     * @param artifactId the artifact to remove
     * @throws IllegalArgumentException if the pack is not found
     * @throws IllegalStateException    if the pack is not in DRAFT status
     */
    @Transactional
    public void removeArtifact(UUID packId, UUID artifactId) {
        TrustContext ctx = TrustContextHolder.require();
        UUID tenantId = ctx.tenantId();

        PackEntity pack = findPackByTenantAndId(tenantId, packId);

        if (pack.getStatus() != ArtifactStatus.DRAFT) {
            throw new IllegalStateException(
                    "Only DRAFT packs can be modified. Current status: " + pack.getStatus());
        }

        packArtifactRepository.deleteByPackIdAndArtifactId(packId, artifactId);

        // Update pack timestamp
        pack.setUpdatedAt(OffsetDateTime.now());
        packRepository.save(pack);

        log.info("Artifact {} removed from pack {}", artifactId, packId);
    }

    /**
     * Publish a pack, transitioning it from DRAFT to PUBLISHED.
     *
     * <p>All artifacts referenced by the pack must be in PUBLISHED status.
     * A pack with unpublished artifacts cannot be published.</p>
     *
     * @param packId the pack to publish
     * @return the published pack entity
     * @throws IllegalArgumentException if the pack is not found
     * @throws IllegalStateException    if the pack is not in DRAFT status, or if any artifact is not PUBLISHED
     */
    @Transactional
    public PackEntity publishPack(UUID packId) {
        TrustContext ctx = TrustContextHolder.require();
        UUID tenantId = ctx.tenantId();

        PackEntity pack = findPackByTenantAndId(tenantId, packId);

        if (pack.getStatus() != ArtifactStatus.DRAFT) {
            throw new IllegalStateException(
                    "Only DRAFT packs can be published. Current status: " + pack.getStatus());
        }

        // Validate all referenced artifacts are PUBLISHED
        List<PackArtifactEntity> packArtifacts = packArtifactRepository.findByPackId(packId);
        List<String> unpublishedArtifacts = new ArrayList<>();

        for (PackArtifactEntity pa : packArtifacts) {
            Optional<ArtifactEntity> artifactOpt =
                    artifactRepository.findByTenantIdAndArtifactId(tenantId, pa.getArtifactId());
            if (artifactOpt.isEmpty()) {
                unpublishedArtifacts.add(pa.getArtifactId() + " (not found)");
            } else if (artifactOpt.get().getStatus() != ArtifactStatus.PUBLISHED) {
                unpublishedArtifacts.add(pa.getArtifactId() + " (status: " + artifactOpt.get().getStatus() + ")");
            }
        }

        if (!unpublishedArtifacts.isEmpty()) {
            throw new IllegalStateException(
                    "All artifacts must be PUBLISHED before the pack can be published. "
                            + "Non-published artifacts: " + String.join(", ", unpublishedArtifacts));
        }

        pack.setStatus(ArtifactStatus.PUBLISHED);
        pack.setPublishedBy(ctx.actorId());
        pack.setPublishedAt(OffsetDateTime.now());
        pack.setUpdatedAt(OffsetDateTime.now());

        pack = packRepository.save(pack);

        writeOutbox("PACK", packId.toString(), "PACK_PUBLISHED", buildPayload(pack, "PACK_PUBLISHED"));

        log.info("Pack published: id={}, name={}, version={}, artifactCount={}",
                packId, pack.getName(), pack.getVersion(), packArtifacts.size());

        return pack;
    }

    /**
     * Deprecate a published pack, transitioning it from PUBLISHED to DEPRECATED.
     *
     * <p>Deprecated packs remain queryable but are flagged as no longer
     * recommended for new assignments.</p>
     *
     * @param packId the pack to deprecate
     * @return the deprecated pack entity
     * @throws IllegalArgumentException if the pack is not found
     * @throws IllegalStateException    if the pack is not in PUBLISHED status
     */
    @Transactional
    public PackEntity deprecatePack(UUID packId) {
        TrustContext ctx = TrustContextHolder.require();
        UUID tenantId = ctx.tenantId();

        PackEntity pack = findPackByTenantAndId(tenantId, packId);

        if (pack.getStatus() != ArtifactStatus.PUBLISHED) {
            throw new IllegalStateException(
                    "Only PUBLISHED packs can be deprecated. Current status: " + pack.getStatus());
        }

        pack.setStatus(ArtifactStatus.DEPRECATED);
        pack.setUpdatedAt(OffsetDateTime.now());

        pack = packRepository.save(pack);

        writeOutbox("PACK", packId.toString(), "PACK_DEPRECATED", buildPayload(pack, "PACK_DEPRECATED"));

        log.info("Pack deprecated: id={}, name={}, version={}", packId, pack.getName(), pack.getVersion());

        return pack;
    }

    /**
     * Retrieve a single pack by its ID within the current tenant.
     *
     * @param packId the pack identifier
     * @return the pack entity
     * @throws IllegalArgumentException if the pack is not found for this tenant
     */
    @Transactional(readOnly = true)
    public PackEntity getPack(UUID packId) {
        TrustContext ctx = TrustContextHolder.require();
        return findPackByTenantAndId(ctx.tenantId(), packId);
    }

    /**
     * Retrieve all artifacts contained in a pack.
     *
     * <p>Resolves the pack-artifact associations and returns the full
     * artifact entities.</p>
     *
     * @param packId the pack identifier
     * @return a list of artifact entities in the pack
     * @throws IllegalArgumentException if the pack is not found for this tenant
     */
    @Transactional(readOnly = true)
    public List<ArtifactEntity> getPackArtifacts(UUID packId) {
        TrustContext ctx = TrustContextHolder.require();
        UUID tenantId = ctx.tenantId();

        // Validate pack exists for this tenant
        findPackByTenantAndId(tenantId, packId);

        List<PackArtifactEntity> associations = packArtifactRepository.findByPackId(packId);

        return associations.stream()
                .map(pa -> artifactRepository.findByTenantIdAndArtifactId(tenantId, pa.getArtifactId()))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toList());
    }

    /**
     * List all packs for the current tenant with pagination.
     *
     * @param pageable pagination parameters
     * @return a page of pack entities
     */
    @Transactional(readOnly = true)
    public Page<PackEntity> listPacks(Pageable pageable) {
        TrustContext ctx = TrustContextHolder.require();
        return packRepository.findByTenantId(ctx.tenantId(), pageable);
    }

    // ------------------------------------------------------------------
    // Internal helpers
    // ------------------------------------------------------------------

    private PackEntity findPackByTenantAndId(UUID tenantId, UUID packId) {
        return packRepository.findByTenantIdAndPackId(tenantId, packId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Pack not found: " + packId + " for tenant " + tenantId));
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

    private String buildPayload(PackEntity pack, String eventType) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("eventType", eventType);
        payload.put("packId", pack.getId().toString());
        payload.put("tenantId", pack.getTenantId().toString());
        payload.put("name", pack.getName());
        payload.put("version", pack.getVersion());
        payload.put("status", pack.getStatus().name());
        if (pack.getPublishedBy() != null) {
            payload.put("publishedBy", pack.getPublishedBy());
        }
        if (pack.getPublishedAt() != null) {
            payload.put("publishedAt", pack.getPublishedAt().toString());
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
