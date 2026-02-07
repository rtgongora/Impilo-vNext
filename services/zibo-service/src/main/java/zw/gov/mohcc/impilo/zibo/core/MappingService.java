package zw.gov.mohcc.impilo.zibo.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.zibo.domain.ArtifactStatus;
import zw.gov.mohcc.impilo.zibo.domain.ArtifactType;
import zw.gov.mohcc.impilo.zibo.persistence.entity.ArtifactEntity;
import zw.gov.mohcc.impilo.zibo.persistence.entity.MappingIndexEntity;
import zw.gov.mohcc.impilo.zibo.persistence.repository.ArtifactRepository;
import zw.gov.mohcc.impilo.zibo.persistence.repository.MappingIndexRepository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Provides concept mapping functionality based on FHIR ConceptMap resources.
 *
 * <p>Maintains a denormalized mapping index (the {@code mapping_index} table)
 * that enables fast lookups without parsing ConceptMap JSON at query time.
 * The index is rebuilt when a ConceptMap artifact is published, and can be
 * fully rebuilt on demand for all published ConceptMaps.</p>
 *
 * <p>Supports source-to-target code mapping with equivalence levels and
 * confidence scores. When no direct mapping exists, an empty result is
 * returned.</p>
 */
@Service
public class MappingService {

    private static final Logger log = LoggerFactory.getLogger(MappingService.class);

    private final MappingIndexRepository mappingIndexRepository;
    private final ArtifactRepository artifactRepository;
    private final ObjectMapper objectMapper;
    private final ArtifactService artifactService;

    public MappingService(MappingIndexRepository mappingIndexRepository,
                          ArtifactRepository artifactRepository,
                          ObjectMapper objectMapper,
                          ArtifactService artifactService) {
        this.mappingIndexRepository = mappingIndexRepository;
        this.artifactRepository = artifactRepository;
        this.objectMapper = objectMapper;
        this.artifactService = artifactService;
    }

    /**
     * Resolves the circular dependency between ArtifactService and MappingService.
     * Sets this instance on the ArtifactService so publish can trigger index rebuilds.
     */
    @PostConstruct
    void init() {
        artifactService.setMappingService(this);
    }

    /**
     * Map a source code to a target system using the pre-built mapping index.
     *
     * <p>Looks up the mapping index for the given source system and code,
     * then filters results to the requested target system. Returns the
     * best match (highest confidence) if multiple mappings exist.</p>
     *
     * @param sourceSystem the source coding system URI
     * @param sourceCode   the source code to map
     * @param targetSystem the desired target coding system URI
     * @return a mapping result; empty fields if no mapping exists
     */
    @Transactional(readOnly = true)
    public MappingResult mapCode(String sourceSystem, String sourceCode, String targetSystem) {
        TrustContext ctx = TrustContextHolder.require();
        UUID tenantId = ctx.tenantId();

        List<MappingIndexEntity> candidates =
                mappingIndexRepository.findByTenantIdAndSourceSystemAndSourceCode(
                        tenantId, sourceSystem, sourceCode);

        // Filter to requested target system and find best match by confidence
        Optional<MappingIndexEntity> bestMatch = candidates.stream()
                .filter(m -> targetSystem.equals(m.getTargetSystem()))
                .max(Comparator.comparing(m -> m.getConfidence() != null ? m.getConfidence() : BigDecimal.ZERO));

        if (bestMatch.isPresent()) {
            MappingIndexEntity match = bestMatch.get();
            return new MappingResult(
                    match.getTargetSystem(),
                    match.getTargetCode(),
                    match.getTargetDisplay(),
                    match.getEquivalence(),
                    match.getConfidence(),
                    match.getConceptmapRef());
        }

        log.debug("No mapping found: sourceSystem={}, sourceCode={}, targetSystem={}, tenant={}",
                sourceSystem, sourceCode, targetSystem, tenantId);

        return new MappingResult(targetSystem, null, null, null, null, null);
    }

    /**
     * Rebuild the mapping index for a single ConceptMap artifact.
     *
     * <p>Reads the ConceptMap's content JSON, parses the FHIR ConceptMap
     * group/element/target structure, deletes any existing index entries
     * for this ConceptMap, and inserts new entries.</p>
     *
     * @param conceptMapArtifactId the artifact ID of the ConceptMap
     * @throws IllegalArgumentException if the artifact is not found or is not a ConceptMap
     */
    @Transactional
    public void rebuildIndex(UUID conceptMapArtifactId) {
        TrustContext ctx = TrustContextHolder.require();
        UUID tenantId = ctx.tenantId();

        ArtifactEntity artifact = artifactRepository
                .findByTenantIdAndArtifactId(tenantId, conceptMapArtifactId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Artifact not found: " + conceptMapArtifactId));

        if (artifact.getFhirType() != ArtifactType.CONCEPT_MAP) {
            throw new IllegalArgumentException(
                    "Artifact " + conceptMapArtifactId + " is not a ConceptMap (type: " + artifact.getFhirType() + ")");
        }

        // Delete existing index entries for this ConceptMap
        mappingIndexRepository.deleteByConceptmapRef(conceptMapArtifactId);

        // Parse and index the ConceptMap content
        String contentJson = artifact.getContentJson();
        if (contentJson == null || contentJson.isBlank()) {
            log.warn("ConceptMap {} has no content JSON, index will be empty", conceptMapArtifactId);
            return;
        }

        List<MappingIndexEntity> entries = parseConceptMapToIndexEntries(
                contentJson, conceptMapArtifactId, tenantId);

        if (!entries.isEmpty()) {
            mappingIndexRepository.saveAll(entries);
        }

        log.info("Mapping index rebuilt for ConceptMap {}: {} entries indexed",
                conceptMapArtifactId, entries.size());
    }

    /**
     * Rebuild the mapping index for all published ConceptMap artifacts
     * across all tenants.
     *
     * <p>Useful for full reindexing after a migration or data repair.</p>
     */
    @Transactional
    public void rebuildAllIndexes() {
        TrustContext ctx = TrustContextHolder.require();
        UUID tenantId = ctx.tenantId();

        List<ArtifactEntity> conceptMaps = artifactRepository
                .findByTenantIdAndFhirType(tenantId, ArtifactType.CONCEPT_MAP)
                .stream()
                .filter(a -> a.getStatus() == ArtifactStatus.PUBLISHED)
                .toList();

        log.info("Rebuilding mapping indexes for {} published ConceptMaps in tenant {}",
                conceptMaps.size(), tenantId);

        int totalEntries = 0;
        for (ArtifactEntity cm : conceptMaps) {
            try {
                mappingIndexRepository.deleteByConceptmapRef(cm.getId());

                List<MappingIndexEntity> entries = parseConceptMapToIndexEntries(
                        cm.getContentJson(), cm.getId(), tenantId);

                if (!entries.isEmpty()) {
                    mappingIndexRepository.saveAll(entries);
                    totalEntries += entries.size();
                }
            } catch (Exception e) {
                log.error("Failed to rebuild index for ConceptMap {}: {}",
                        cm.getId(), e.getMessage(), e);
            }
        }

        log.info("Mapping index rebuild complete: {} ConceptMaps, {} total entries",
                conceptMaps.size(), totalEntries);
    }

    /**
     * List all available target systems for a given source system within the
     * current tenant.
     *
     * @param sourceSystem the source coding system URI
     * @return a list of distinct target system URIs that have mappings from the source
     */
    @Transactional(readOnly = true)
    public List<String> getAvailableMappings(String sourceSystem) {
        TrustContext ctx = TrustContextHolder.require();
        UUID tenantId = ctx.tenantId();

        // Get all mappings for this source system and extract distinct target systems
        List<MappingIndexEntity> mappings =
                mappingIndexRepository.findByTenantIdAndSourceSystemAndSourceCode(tenantId, sourceSystem, null);

        // If the repository doesn't support null sourceCode, fall back to broader query
        // We use a different approach: get all for the source system by querying by conceptmap refs
        List<ArtifactEntity> conceptMaps = artifactRepository
                .findByTenantIdAndFhirType(tenantId, ArtifactType.CONCEPT_MAP)
                .stream()
                .filter(a -> a.getStatus() == ArtifactStatus.PUBLISHED)
                .toList();

        Set<String> targetSystems = new LinkedHashSet<>();
        for (ArtifactEntity cm : conceptMaps) {
            List<MappingIndexEntity> entries = mappingIndexRepository.findByConceptmapRef(cm.getId());
            for (MappingIndexEntity entry : entries) {
                if (sourceSystem.equals(entry.getSourceSystem()) && entry.getTargetSystem() != null) {
                    targetSystems.add(entry.getTargetSystem());
                }
            }
        }

        return new ArrayList<>(targetSystems);
    }

    // ------------------------------------------------------------------
    // Internal helpers
    // ------------------------------------------------------------------

    /**
     * Parse a FHIR ConceptMap JSON into mapping index entries.
     *
     * <p>Follows the standard FHIR ConceptMap structure:
     * {@code group[] -> element[] -> target[]}. Each group defines a
     * source/target system pair. Each element defines a source code.
     * Each target defines a target code with equivalence.</p>
     */
    private List<MappingIndexEntity> parseConceptMapToIndexEntries(
            String contentJson, UUID conceptMapId, UUID tenantId) {

        List<MappingIndexEntity> entries = new ArrayList<>();

        try {
            JsonNode root = objectMapper.readTree(contentJson);
            JsonNode groups = root.path("group");

            if (!groups.isArray()) {
                log.warn("ConceptMap {} has no 'group' array", conceptMapId);
                return entries;
            }

            for (JsonNode group : groups) {
                String sourceSystem = group.path("source").asText(null);
                String targetSystem = group.path("target").asText(null);

                JsonNode elements = group.path("element");
                if (!elements.isArray()) {
                    continue;
                }

                for (JsonNode element : elements) {
                    String sourceCode = element.path("code").asText(null);
                    String sourceDisplay = element.path("display").asText(null);

                    JsonNode targets = element.path("target");
                    if (!targets.isArray()) {
                        continue;
                    }

                    for (JsonNode target : targets) {
                        String targetCode = target.path("code").asText(null);
                        String targetDisplay = target.path("display").asText(null);
                        String equivalence = target.path("equivalence").asText(null);

                        MappingIndexEntity entry = new MappingIndexEntity();
                        entry.setId(UUID.randomUUID());
                        entry.setTenantId(tenantId);
                        entry.setSourceSystem(sourceSystem);
                        entry.setSourceCode(sourceCode);
                        entry.setSourceDisplay(sourceDisplay);
                        entry.setTargetSystem(targetSystem);
                        entry.setTargetCode(targetCode);
                        entry.setTargetDisplay(targetDisplay);
                        entry.setEquivalence(equivalence);
                        entry.setConceptmapRef(conceptMapId);
                        entry.setConfidence(deriveConfidence(equivalence));
                        entry.setCreatedAt(OffsetDateTime.now());

                        entries.add(entry);
                    }
                }
            }
        } catch (JsonProcessingException e) {
            log.error("Failed to parse ConceptMap JSON for artifact {}: {}",
                    conceptMapId, e.getMessage());
        }

        return entries;
    }

    /**
     * Derive a confidence score from the FHIR equivalence value.
     *
     * <p>Maps standard ConceptMap equivalence values to numeric confidence:</p>
     * <ul>
     *   <li>{@code equivalent}, {@code equal} = 1.0</li>
     *   <li>{@code wider} = 0.9</li>
     *   <li>{@code narrower}, {@code subsumes} = 0.8</li>
     *   <li>{@code inexact}, {@code relatedto} = 0.6</li>
     *   <li>{@code unmatched}, {@code disjoint} = 0.0</li>
     *   <li>unknown/null = 0.5</li>
     * </ul>
     */
    private BigDecimal deriveConfidence(String equivalence) {
        if (equivalence == null) {
            return new BigDecimal("0.5");
        }
        return switch (equivalence.toLowerCase(Locale.ROOT)) {
            case "equivalent", "equal" -> BigDecimal.ONE;
            case "wider" -> new BigDecimal("0.9");
            case "narrower", "subsumes" -> new BigDecimal("0.8");
            case "inexact", "relatedto" -> new BigDecimal("0.6");
            case "unmatched", "disjoint" -> BigDecimal.ZERO;
            default -> new BigDecimal("0.5");
        };
    }

    // ------------------------------------------------------------------
    // Inner data class
    // ------------------------------------------------------------------

    /**
     * Result of a concept code mapping operation.
     *
     * @param targetSystem  the target coding system URI
     * @param targetCode    the mapped target code; {@code null} if no mapping found
     * @param targetDisplay the display text for the target code; may be {@code null}
     * @param equivalence   the FHIR equivalence level (e.g. "equivalent", "wider")
     * @param confidence    numeric confidence score (0.0 to 1.0)
     * @param conceptmapRef the UUID of the ConceptMap artifact that provided this mapping
     */
    public record MappingResult(String targetSystem, String targetCode, String targetDisplay,
                                String equivalence, BigDecimal confidence, UUID conceptmapRef) {}
}
