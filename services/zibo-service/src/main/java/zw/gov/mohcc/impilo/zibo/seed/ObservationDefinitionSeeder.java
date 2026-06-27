package zw.gov.mohcc.impilo.zibo.seed;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import zw.gov.mohcc.impilo.shared.auth.AccessMode;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.zibo.core.ArtifactService;
import zw.gov.mohcc.impilo.zibo.domain.ArtifactType;
import zw.gov.mohcc.impilo.zibo.persistence.repository.ArtifactRepository;

import java.io.InputStream;
import java.util.UUID;

/**
 * Loads the cited DRAFT vitals reference-range {@code ObservationDefinition} seed (generated from the
 * single-source UI assets by {@code export-reference-ranges.ts}) into ZIBO at startup, idempotently.
 *
 * <p>Each artifact is created in DRAFT status (provisional/advisory per doctrine) only if a row with the
 * same canonical URL + version does not already exist for the seed tenant. Failures never abort boot —
 * the resolver degrades to {@code NO_REFERENCE_RANGE} (fail-closed) when ranges are absent.</p>
 *
 * <p>Enable/disable with {@code impilo.zibo.seed.observation-definitions.enabled} (default true). The
 * seed tenant must match the tenant the clinical-knowledge-platform requests carry; configure via
 * {@code impilo.zibo.seed.tenant-id}.</p>
 */
@Component
@ConditionalOnProperty(name = "impilo.zibo.seed.observation-definitions.enabled", havingValue = "true", matchIfMissing = true)
public class ObservationDefinitionSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ObservationDefinitionSeeder.class);
    private static final String RESOURCE = "seed/observation-definitions.seed.json";

    private final ArtifactService artifactService;
    private final ArtifactRepository artifactRepository;
    private final ObjectMapper objectMapper;
    private final UUID seedTenantId;

    public ObservationDefinitionSeeder(
            ArtifactService artifactService,
            ArtifactRepository artifactRepository,
            ObjectMapper objectMapper,
            @Value("${impilo.zibo.seed.tenant-id:00000000-0000-0000-0000-000000000001}") String seedTenantId) {
        this.artifactService = artifactService;
        this.artifactRepository = artifactRepository;
        this.objectMapper = objectMapper;
        this.seedTenantId = UUID.fromString(seedTenantId);
    }

    @Override
    public void run(ApplicationArguments args) {
        try (InputStream in = openSeed()) {
            if (in == null) {
                log.info("No ObservationDefinition seed resource on classpath — skipping.");
                return;
            }
            JsonNode root = objectMapper.readTree(in);
            JsonNode definitions = root.get("definitions");
            if (definitions == null || !definitions.isArray()) {
                log.warn("ObservationDefinition seed has no 'definitions' array — skipping.");
                return;
            }

            TrustContextHolder.set(systemContext());
            try {
                int created = 0;
                int skipped = 0;
                for (JsonNode def : definitions) {
                    if (seedOne(def)) {
                        created++;
                    } else {
                        skipped++;
                    }
                }
                log.info("ObservationDefinition seed complete: {} created, {} already present (tenant={}).",
                        created, skipped, seedTenantId);
            } finally {
                TrustContextHolder.clear();
            }
        } catch (Exception e) {
            // Never abort boot — fail-closed (resolver yields NO_REFERENCE_RANGE without ranges).
            log.warn("ObservationDefinition seeding failed (continuing): {}", e.getMessage());
        }
    }

    /** @return true if a new artifact was created, false if it already existed / was unusable. */
    private boolean seedOne(JsonNode def) {
        String canonicalUrl = text(def, "canonicalUrl");
        String version = text(def, "version");
        if (canonicalUrl == null || version == null || def.get("content") == null) {
            return false;
        }
        if (artifactRepository.existsByTenantIdAndCanonicalUrlAndVersion(seedTenantId, canonicalUrl, version)) {
            return false;
        }
        String contentJson;
        try {
            contentJson = objectMapper.writeValueAsString(def.get("content"));
        } catch (Exception e) {
            log.warn("Skipping unserialisable seed definition {}: {}", canonicalUrl, e.getMessage());
            return false;
        }
        artifactService.createDraft(
                ArtifactType.OBSERVATION_DEFINITION,
                canonicalUrl,
                version,
                text(def, "name"),
                text(def, "title"),
                text(def, "citation"),
                contentJson,
                text(def, "publisher"));
        return true;
    }

    private InputStream openSeed() {
        ClassPathResource resource = new ClassPathResource(RESOURCE);
        try {
            return resource.exists() ? resource.getInputStream() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private TrustContext systemContext() {
        return new TrustContext(
                seedTenantId,
                "system-seeder",
                "SERVICE",
                "GOVERNANCE",
                null,
                UUID.randomUUID(),
                null,
                null,
                null,
                AccessMode.INTERNAL);
    }

    private static String text(JsonNode parent, String field) {
        JsonNode n = parent.get(field);
        return n == null || n.isNull() ? null : n.asText();
    }
}
