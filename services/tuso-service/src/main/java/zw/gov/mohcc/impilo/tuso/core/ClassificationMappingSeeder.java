package zw.gov.mohcc.impilo.tuso.core;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityClassificationMappingRuleEntity;
import zw.gov.mohcc.impilo.tuso.persistence.repository.FacilityClassificationMappingRuleRepository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Loads the versioned facility-classification mapping rule pack from a classpath
 * resource into {@code tuso.facility_classification_mapping_rule}.
 *
 * <p>Mirrors {@link InspectionContentSeeder}'s governance discipline:</p>
 * <ul>
 *   <li>Immutability — a {@code (code, version)} pair, once persisted, never
 *       changes; a differing checksum for an existing version fails fast at
 *       startup. Policy changes require a new version.</li>
 *   <li>Governed activation — the target status per rule code is declared in
 *       {@code regulatory/content-governance.json#classificationMappingStatuses};
 *       ACTIVE stamps the approval provenance from the same manifest.</li>
 * </ul>
 */
@Component
public class ClassificationMappingSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ClassificationMappingSeeder.class);

    static final Set<String> VALID_STATUSES = Set.of(
            "DRAFT", "SOURCE_RECONCILED", "VALIDATED_CURRENT_SOURCE", "ACTIVE", "RETIRED");

    private final FacilityClassificationMappingRuleRepository ruleRepository;
    private final ObjectMapper objectMapper;

    public ClassificationMappingSeeder(FacilityClassificationMappingRuleRepository ruleRepository,
                                       ObjectMapper objectMapper) {
        this.ruleRepository = ruleRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) throws Exception {
        Map<String, Object> governance = readJson("classpath:regulatory/content-governance.json");
        @SuppressWarnings("unchecked")
        Map<String, String> statuses =
                (Map<String, String>) governance.getOrDefault("classificationMappingStatuses", Map.of());
        String approvedBy = (String) governance.get("approvedBy");
        LocalDate effectiveFrom = LocalDate.parse((String) governance.get("effectiveFrom"));

        var resolver = new PathMatchingResourcePatternResolver();
        Resource[] packs = resolver.getResources("classpath:regulatory/classification-mapping/*.json");
        int loaded = 0;
        for (Resource pack : packs) {
            Map<String, Object> doc = objectMapper.readValue(pack.getInputStream(), new TypeReference<>() {});
            loadRule(doc, statuses, approvedBy, effectiveFrom);
            loaded++;
        }
        log.info("Classification mapping rule packs loaded: {}", loaded);
    }

    private void loadRule(Map<String, Object> doc, Map<String, String> statuses,
                          String approvedBy, LocalDate effectiveFrom) throws Exception {
        String code = requireText(doc, "code");
        int version = ((Number) doc.getOrDefault("version", 1)).intValue();
        String targetStatus = statuses.getOrDefault(code, "DRAFT");
        if (!VALID_STATUSES.contains(targetStatus)) {
            throw new IllegalStateException("Unknown governance status " + targetStatus + " for mapping rule " + code);
        }

        // Activation proof: an ACTIVE/VALIDATED rule must carry the tables the
        // engine consumes — refuse to activate a structurally hollow pack.
        if ("ACTIVE".equals(targetStatus) || "VALIDATED_CURRENT_SOURCE".equals(targetStatus)) {
            validateForActivation(code, doc);
        }

        String checksum = checksum(objectMapper.writeValueAsString(doc));
        Optional<FacilityClassificationMappingRuleEntity> existing =
                ruleRepository.findByCodeAndVersion(code, version);
        if (existing.isPresent()) {
            if (!existing.get().getContentChecksum().equals(checksum)) {
                throw new IllegalStateException("Immutable content violation: mapping rule " + code + " v" + version
                        + " already persisted with a different checksum. Policy changes require a new version.");
            }
            return; // already loaded, verified identical
        }

        FacilityClassificationMappingRuleEntity entity = new FacilityClassificationMappingRuleEntity();
        entity.setCode(code);
        entity.setVersion(version);
        entity.setTitle(requireText(doc, "title"));
        entity.setRuleJson(doc);
        entity.setContentChecksum(checksum);
        entity.setStatus(targetStatus);
        entity.setSourceDoc((String) doc.get("sourceDoc"));
        if ("ACTIVE".equals(targetStatus) || "VALIDATED_CURRENT_SOURCE".equals(targetStatus)) {
            entity.setApprovedBy(approvedBy);
            entity.setApprovedAt(Instant.now());
            entity.setEffectiveFrom(effectiveFrom);
        }
        ruleRepository.save(entity);
        log.info("Loaded classification mapping rule {} v{} status={}", code, version, targetStatus);
    }

    private void validateForActivation(String code, Map<String, Object> doc) {
        for (String required : new String[]{"ownershipAuthority", "facilityType", "bedBands", "catalogueLabels"}) {
            Object value = doc.get(required);
            if (!(value instanceof Map) && !(value instanceof java.util.List)) {
                throw new IllegalStateException(code + ": cannot activate a mapping rule without a '"
                        + required + "' table");
            }
        }
    }

    private Map<String, Object> readJson(String location) throws Exception {
        Resource resource = new PathMatchingResourcePatternResolver().getResource(location);
        return objectMapper.readValue(resource.getInputStream(), new TypeReference<>() {});
    }

    private static String requireText(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null || value.toString().isBlank()) {
            throw new IllegalStateException("Missing required field: " + key);
        }
        return value.toString();
    }

    static String checksum(String content) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return HexFormat.of().formatHex(digest.digest(content.getBytes(StandardCharsets.UTF_8)));
    }
}
