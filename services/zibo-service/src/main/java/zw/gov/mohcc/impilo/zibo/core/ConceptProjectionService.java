package zw.gov.mohcc.impilo.zibo.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.persistence.EntityManager;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.zibo.domain.ArtifactStatus;
import zw.gov.mohcc.impilo.zibo.domain.ArtifactType;
import zw.gov.mohcc.impilo.zibo.domain.AuthorityScope;
import zw.gov.mohcc.impilo.zibo.persistence.entity.ArtifactEntity;
import zw.gov.mohcc.impilo.zibo.persistence.entity.ConceptEntity;
import zw.gov.mohcc.impilo.zibo.persistence.repository.ArtifactRepository;
import zw.gov.mohcc.impilo.zibo.persistence.repository.ConceptRepository;

/**
 * Rebuilds the concept index from an artifact's {@code content_json}.
 *
 * <p>Deliberately the same shape as {@code MappingService.rebuildIndex}: derived rows, replaced
 * wholesale on publish, never merged. Merging is how a projection drifts from its source — a
 * concept removed from a CodeSystem in a new version would survive forever.</p>
 *
 * <p>Hooked into {@code ArtifactService.publish}, alongside the existing ConceptMap rebuild.</p>
 */
@Service
public class ConceptProjectionService {

    private static final Logger log = LoggerFactory.getLogger(ConceptProjectionService.class);

    /** Batched so a 109,000-concept LOINC release does not build one giant persistence context. */
    private static final int FLUSH_EVERY = 500;

    private final ConceptRepository conceptRepository;
    private final ArtifactRepository artifactRepository;
    private final ObjectMapper objectMapper;
    private final EntityManager entityManager;
    private final UUID nationalPlane;

    public ConceptProjectionService(
            ConceptRepository conceptRepository,
            ArtifactRepository artifactRepository,
            ObjectMapper objectMapper,
            EntityManager entityManager,
            @Value("${zibo.terminology.national-plane-tenant-id:00000000-0000-0000-0000-000000000001}")
            String nationalPlaneTenantId) {
        this.conceptRepository = conceptRepository;
        this.artifactRepository = artifactRepository;
        this.objectMapper = objectMapper;
        this.entityManager = entityManager;
        this.nationalPlane = UUID.fromString(nationalPlaneTenantId);
    }

    /**
     * Reprojects every servable CodeSystem in the estate, across all tenant planes.
     *
     * <p>Without this the index is empty on any database whose content arrived through migrations.
     * Every seeded artifact is {@code INSERT}ed directly at {@code PUBLISHED}/{@code ACTIVE} and
     * never passes through {@code ArtifactService.publish}, which was the only thing that called
     * {@link #rebuild}. So a freshly migrated ZIBO answers {@code $lookup} and {@code $expand} with
     * nothing at all, for every vocabulary it demonstrably holds — the table exists, the content
     * exists, and the two have never been introduced.</p>
     *
     * <p>{@code DRAFT} artifacts are skipped: they project when they are published. Reprojection is
     * idempotent — {@link #rebuild} replaces an artifact's rows wholesale.</p>
     *
     * @return per-artifact concept counts, keyed by artifact id
     */
    public ReprojectionResult reprojectAll() {
        List<ArtifactEntity> targets = artifactRepository.findByFhirTypeAndStatusIn(
                ArtifactType.CODE_SYSTEM,
                List.of(ArtifactStatus.PUBLISHED, ArtifactStatus.ACTIVE,
                        ArtifactStatus.DEPRECATED, ArtifactStatus.RETIRED));

        int artifacts = 0;
        int concepts = 0;
        int failed = 0;
        for (ArtifactEntity artifact : targets) {
            try {
                concepts += rebuild(artifact);
                artifacts++;
            } catch (Exception e) {
                // One unparseable artifact must not abandon the other forty.
                failed++;
                log.error("ZIBO: reprojection failed for {} v{} ({}): {}",
                        artifact.getCanonicalUrl(), artifact.getVersion(),
                        artifact.getArtifactId(), e.getMessage(), e);
            }
        }
        log.info("ZIBO: reprojected {} of {} CodeSystems, {} concepts, {} failed",
                artifacts, targets.size(), concepts, failed);
        return new ReprojectionResult(targets.size(), artifacts, concepts, failed);
    }

    /** Outcome of a full reprojection. {@code failed} is reported, never swallowed. */
    public record ReprojectionResult(int codeSystemsFound, int codeSystemsProjected,
                                     int conceptsWritten, int failed) {
    }

    /**
     * Projects a CodeSystem artifact's concepts into the index, replacing whatever was there.
     *
     * <p>Runs in its own transaction. {@code ArtifactService.publish} documents — and intends —
     * that a projection failure must not roll back the publish, but under the default
     * {@code REQUIRED} propagation this method joined the publish transaction, so an exception
     * here marked the shared transaction rollback-only and the caller's {@code catch} produced an
     * {@code UnexpectedRollbackException} at commit instead of a published artifact. The artifact
     * row is committed before this runs, so the foreign key is satisfied from the new
     * transaction.</p>
     *
     * @return how many concepts were written
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int rebuild(ArtifactEntity artifact) {
        if (artifact.getFhirType() != ArtifactType.CODE_SYSTEM) {
            // ValueSets are expanded from their CodeSystems at query time rather than materialised,
            // so a ValueSet publish has nothing to project. ConceptMaps go to zibo_mappings_index.
            return 0;
        }

        conceptRepository.deleteByArtifactId(artifact.getArtifactId());
        entityManager.flush();

        JsonNode root;
        try {
            root = objectMapper.readTree(artifact.getContentJson());
        } catch (Exception e) {
            log.error("ZIBO: cannot project concepts for {} — content_json is unparseable: {}",
                    artifact.getCanonicalUrl(), e.getMessage());
            return 0;
        }

        // A CodeSystem may legitimately carry no concepts inline: content=not-present is FHIR's own
        // construct for a terminology too large to inline, which is how LOINC and ICD-11 will
        // arrive. Their concepts are streamed in by their importer, not from this blob.
        String content = root.path("content").asText("complete");
        JsonNode concepts = root.path("concept");
        if (!concepts.isArray() || concepts.isEmpty()) {
            if ("not-present".equals(content)) {
                log.info("ZIBO: {} declares content=not-present — concepts come from its importer, "
                        + "not from content_json", artifact.getCanonicalUrl());
            }
            return 0;
        }

        String system = root.path("url").asText(artifact.getCanonicalUrl());
        String version = root.path("version").asText(artifact.getVersion());

        List<ConceptEntity> batch = new ArrayList<>();
        int written = flatten(concepts, null, artifact, system, version, batch, 0);

        log.info("ZIBO: projected {} concepts for {} v{} ({})",
                written, system, version, artifact.getArtifactId());
        return written;
    }

    /**
     * Walks {@code concept[]} including nested children.
     *
     * <p>FHIR nests sub-concepts inside their parent rather than carrying a parent pointer, so the
     * hierarchy is flattened here and {@code parent_code} recorded — otherwise a nested vocabulary
     * would project only its top level, which is the silent-truncation failure this service exists
     * to avoid.</p>
     */
    private int flatten(JsonNode concepts, String parentCode, ArtifactEntity artifact,
                        String system, String version, List<ConceptEntity> batch, int written) {
        for (JsonNode node : concepts) {
            String code = node.path("code").asText(null);
            if (code == null || code.isBlank()) {
                continue;
            }

            ConceptEntity c = new ConceptEntity();
            boolean national = nationalPlane.equals(artifact.getTenantId());
            c.setAuthorityScope(national ? AuthorityScope.NATIONAL : AuthorityScope.TENANT);
            c.setTenantId(national ? null : artifact.getTenantId());
            c.setArtifactId(artifact.getArtifactId());
            c.setSystem(system);
            c.setVersion(version);
            c.setCode(code);
            c.setDisplay(node.path("display").asText(null));
            c.setDefinition(node.path("definition").asText(null));
            c.setParentCode(parentCode);
            c.setActive(isActive(node));
            c.setDesignations(compact(node.path("designation")));
            c.setProperties(propertiesOf(node));

            batch.add(c);
            written++;

            if (batch.size() >= FLUSH_EVERY) {
                persist(batch);
                batch.clear();
            }

            JsonNode children = node.path("concept");
            if (children.isArray() && !children.isEmpty()) {
                written = flatten(children, code, artifact, system, version, batch, written);
            }
        }
        if (!batch.isEmpty()) {
            persist(batch);
            batch.clear();
        }
        return written;
    }

    /**
     * Saves a batch and populates {@code search_vector}.
     *
     * <p>The vector is maintained here rather than by a database trigger so the text that feeds it
     * — display plus every designation value — is chosen by the code that knows the artifact, and
     * so a change to what is searchable is a code change with a test, not an invisible DDL edit.</p>
     */
    private void persist(List<ConceptEntity> batch) {
        conceptRepository.saveAll(batch);
        entityManager.flush();
        for (ConceptEntity c : batch) {
            entityManager.createNativeQuery(
                            "UPDATE zibo_concept SET search_vector = "
                            + "to_tsvector('simple', coalesce(:text, '')) WHERE concept_id = :id")
                    .setParameter("text", searchableText(c))
                    .setParameter("id", c.getConceptId())
                    .executeUpdate();
        }
        // Detach only what this batch created. entityManager.clear() emptied the whole persistence
        // context, which also detached the caller's ArtifactEntity mid-publish — the batching was
        // meant to bound memory, not to evict the artifact being published.
        for (ConceptEntity c : batch) {
            entityManager.detach(c);
        }
    }

    private String searchableText(ConceptEntity c) {
        StringBuilder sb = new StringBuilder();
        if (c.getCode() != null) sb.append(c.getCode()).append(' ');
        if (c.getDisplay() != null) sb.append(c.getDisplay()).append(' ');
        try {
            JsonNode designations = objectMapper.readTree(c.getDesignations());
            for (JsonNode d : designations) {
                String value = d.path("value").asText(null);
                if (value != null) {
                    // Shona and Ndebele designations must be searchable, not decorative.
                    sb.append(value).append(' ');
                }
            }
        } catch (Exception ignored) {
            // A malformed designation array costs its synonyms, not the concept.
        }
        return sb.toString().trim();
    }

    /**
     * FHIR has no {@code concept.active}; inactivity is a property named {@code status} or
     * {@code inactive}. Absent means active — a concept is not silently retired by omission.
     */
    private static boolean isActive(JsonNode concept) {
        for (JsonNode p : concept.path("property")) {
            String code = p.path("code").asText("");
            if ("inactive".equals(code) && p.path("valueBoolean").asBoolean(false)) {
                return false;
            }
            if ("status".equals(code)) {
                String v = p.path("valueCode").asText(p.path("valueString").asText(""));
                if ("retired".equalsIgnoreCase(v) || "deprecated".equalsIgnoreCase(v)) {
                    return false;
                }
            }
        }
        return true;
    }

    /** Flattens {@code property[]} into an object keyed by property code, for cheap filtering. */
    private String propertiesOf(JsonNode concept) {
        ObjectNode out = objectMapper.createObjectNode();
        for (JsonNode p : concept.path("property")) {
            String code = p.path("code").asText(null);
            if (code == null) {
                continue;
            }
            p.fieldNames().forEachRemaining(field -> {
                if (field.startsWith("value")) {
                    out.set(code, p.get(field));
                }
            });
        }
        return out.toString();
    }

    private String compact(JsonNode node) {
        return node.isArray() ? node.toString() : "[]";
    }
}
