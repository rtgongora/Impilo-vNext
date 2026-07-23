package zw.gov.mohcc.impilo.zibo.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.zibo.domain.ArtifactStatus;
import zw.gov.mohcc.impilo.zibo.domain.ArtifactType;
import zw.gov.mohcc.impilo.zibo.persistence.entity.ArtifactEntity;
import zw.gov.mohcc.impilo.zibo.persistence.repository.ArtifactRepository;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * OF-B8/OF-D — national medicine registry read model over the WHO ATC
 * CodeSystem artifact (canonical system {@code http://www.whocc.no/atc},
 * seeded by V007).
 *
 * <p>Deliberately NOT a parallel terminology store: medicines are concepts of
 * a registered {@link ArtifactType#CODE_SYSTEM} artifact (the same machinery
 * validate-coding and artifact resolve already use). This service only
 * projects those concepts into a medicine-shaped view — resolve by ATC code,
 * search by name — reading the latest PUBLISHED artifact for the tenant.</p>
 */
@Service
public class MedicineRegistryService {

    /** Canonical WHO ATC coding-system URI — the {@code system} of every medication coding. */
    public static final String ATC_SYSTEM = "http://www.whocc.no/atc";

    private static final Logger log = LoggerFactory.getLogger(MedicineRegistryService.class);

    private final ArtifactRepository artifactRepository;
    private final ObjectMapper objectMapper;

    public MedicineRegistryService(ArtifactRepository artifactRepository, ObjectMapper objectMapper) {
        this.artifactRepository = artifactRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * One registry entry — a concept of the ATC CodeSystem with its governed
     * medicine properties.
     */
    public record Medicine(String atcCode, String display, String genericName, String form,
                           String strength, String scheduleClass, boolean controlled,
                           String system, String systemVersion) {}

    /** Resolve one medicine by its exact ATC code (case-sensitive per the CodeSystem). */
    @Transactional(readOnly = true)
    public Optional<Medicine> resolveByCode(String atcCode) {
        if (atcCode == null || atcCode.isBlank()) {
            return Optional.empty();
        }
        return loadConcepts().stream()
                .filter(m -> m.atcCode().equals(atcCode.trim()))
                .findFirst();
    }

    /**
     * Search medicines by name — matches generic name, display or ATC-code
     * prefix, case-insensitively.
     */
    @Transactional(readOnly = true)
    public List<Medicine> searchByName(String query, int limit) {
        int max = limit > 0 ? Math.min(limit, 100) : 20;
        List<Medicine> all = loadConcepts();
        if (query == null || query.isBlank()) {
            return all.stream().limit(max).toList();
        }
        String q = query.trim().toLowerCase(Locale.ROOT);
        return all.stream()
                .filter(m -> contains(m.genericName(), q)
                        || contains(m.display(), q)
                        || m.atcCode().toLowerCase(Locale.ROOT).startsWith(q))
                .limit(max)
                .toList();
    }

    // ------------------------------------------------------------------
    // Internal
    // ------------------------------------------------------------------

    private static boolean contains(String value, String lowerQuery) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(lowerQuery);
    }

    /**
     * Loads the concepts of the latest PUBLISHED ATC CodeSystem for the
     * current tenant. Empty when no artifact is registered — callers surface
     * that honestly (404 / empty list), never a fabricated registry.
     */
    private List<Medicine> loadConcepts() {
        TrustContext ctx = TrustContextHolder.require();
        Optional<ArtifactEntity> artifact =
                artifactRepository.findByTenantIdAndCanonicalUrl(ctx.tenantId(), ATC_SYSTEM).stream()
                        .filter(a -> a.getFhirType() == ArtifactType.CODE_SYSTEM)
                        .filter(a -> a.getStatus() == ArtifactStatus.PUBLISHED)
                        .max(Comparator.comparing(ArtifactEntity::getPublishedAt));
        if (artifact.isEmpty()) {
            log.warn("No PUBLISHED ATC CodeSystem registered for tenant {} — medicine registry empty",
                    ctx.tenantId());
            return List.of();
        }
        try {
            JsonNode root = objectMapper.readTree(artifact.get().getContentJson());
            String systemVersion = root.path("version").asText(artifact.get().getVersion());
            List<Medicine> out = new ArrayList<>();
            for (JsonNode concept : root.path("concept")) {
                String code = concept.path("code").asText(null);
                if (code == null || code.isBlank()) {
                    continue;
                }
                out.add(new Medicine(
                        code,
                        concept.path("display").asText(null),
                        property(concept, "genericName"),
                        property(concept, "form"),
                        property(concept, "strength"),
                        property(concept, "scheduleClass"),
                        booleanProperty(concept),
                        ATC_SYSTEM,
                        systemVersion));
            }
            return out;
        } catch (Exception e) {
            log.warn("Failed to parse ATC CodeSystem content: {}", e.getMessage());
            return List.of();
        }
    }

    private static String property(JsonNode concept, String propertyCode) {
        for (JsonNode p : concept.path("property")) {
            if (propertyCode.equals(p.path("code").asText(null))) {
                return p.path("valueString").asText(null);
            }
        }
        return null;
    }

    private static boolean booleanProperty(JsonNode concept) {
        for (JsonNode p : concept.path("property")) {
            if ("controlled".equals(p.path("code").asText(null))) {
                return p.path("valueBoolean").asBoolean(false);
            }
        }
        return false;
    }
}
