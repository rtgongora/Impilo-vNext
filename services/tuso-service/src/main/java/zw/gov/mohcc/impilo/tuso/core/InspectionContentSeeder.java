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
import zw.gov.mohcc.impilo.tuso.persistence.entity.ClassificationTemplateApplicabilityEntity;
import zw.gov.mohcc.impilo.tuso.persistence.entity.InspectionRequirementModuleEntity;
import zw.gov.mohcc.impilo.tuso.persistence.entity.InspectionTemplateCompositionEntity;
import zw.gov.mohcc.impilo.tuso.persistence.repository.ClassificationTemplateApplicabilityRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.InspectionRequirementModuleRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.InspectionTemplateCompositionRepository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Loads the manual-derived inspection content catalogue from versioned
 * classpath resources into the module/composition/applicability tables.
 *
 * <p>Governance pipeline: DRAFT → SOURCE_RECONCILED → VALIDATED_CURRENT_SOURCE
 * → ACTIVE. The target status per module/composition is declared in
 * {@code regulatory/content-governance.json} together with approval
 * provenance; this loader is the automated half of the activation proof — it
 * REFUSES to activate content that violates the invariants below.</p>
 *
 * <p>Immutability: a (code, version) pair, once persisted, never changes.
 * If the classpath content for an existing version has a different checksum,
 * the service fails fast at startup rather than silently rewriting history.
 * Content changes require a new version.</p>
 */
@Component
public class InspectionContentSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(InspectionContentSeeder.class);

    static final Set<String> VALID_STATUSES = Set.of(
            "DRAFT", "SOURCE_RECONCILED", "VALIDATED_CURRENT_SOURCE", "ACTIVE", "RETIRED");
    static final Set<String> VALID_OBLIGATIONS = Set.of(
            "MANDATORY", "OPTIONAL", "RECOMMENDED", "WHERE_APPLICABLE", "WHERE_POSSIBLE");

    private final InspectionRequirementModuleRepository moduleRepository;
    private final InspectionTemplateCompositionRepository compositionRepository;
    private final ClassificationTemplateApplicabilityRepository applicabilityRepository;
    private final ObjectMapper objectMapper;

    public InspectionContentSeeder(InspectionRequirementModuleRepository moduleRepository,
                                   InspectionTemplateCompositionRepository compositionRepository,
                                   ClassificationTemplateApplicabilityRepository applicabilityRepository,
                                   ObjectMapper objectMapper) {
        this.moduleRepository = moduleRepository;
        this.compositionRepository = compositionRepository;
        this.applicabilityRepository = applicabilityRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) throws Exception {
        Map<String, Object> governance = readJson("classpath:regulatory/content-governance.json",
                new TypeReference<>() {});
        @SuppressWarnings("unchecked")
        Map<String, String> moduleStatuses = (Map<String, String>) governance.get("moduleStatuses");
        String approvedBy = (String) governance.get("approvedBy");
        LocalDate effectiveFrom = LocalDate.parse((String) governance.get("effectiveFrom"));

        Set<String> loadedModuleCodes = new HashSet<>();
        Set<String> allItemCodes = new HashSet<>();
        var resolver = new PathMatchingResourcePatternResolver();
        Resource[] moduleFiles = resolver.getResources("classpath:regulatory/inspection-modules/*.json");
        for (Resource file : moduleFiles) {
            Map<String, Object> module = objectMapper.readValue(file.getInputStream(), new TypeReference<>() {});
            loadModule(module, moduleStatuses, approvedBy, effectiveFrom, allItemCodes);
            loadedModuleCodes.add((String) module.get("moduleCode"));
        }

        Map<String, Object> compositionsDoc = readJson("classpath:regulatory/inspection-compositions.json",
                new TypeReference<>() {});
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> compositions = (List<Map<String, Object>>) compositionsDoc.get("compositions");
        for (Map<String, Object> composition : compositions) {
            loadComposition(composition, loadedModuleCodes, approvedBy, effectiveFrom);
        }

        Map<String, Object> applicabilityDoc = readJson("classpath:regulatory/classification-applicability.json",
                new TypeReference<>() {});
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> mappings = (List<Map<String, Object>>) applicabilityDoc.get("mappings");
        Set<String> compositionCodes = new HashSet<>();
        compositions.forEach(c -> compositionCodes.add((String) c.get("code")));
        loadApplicability(mappings, compositionCodes);

        log.info("Inspection content catalogue loaded: {} modules, {} compositions, {} applicability mappings",
                moduleFiles.length, compositions.size(), mappings.size());
    }

    private void loadModule(Map<String, Object> module, Map<String, String> moduleStatuses,
                            String approvedBy, LocalDate effectiveFrom, Set<String> allItemCodes) throws Exception {
        String code = requireText(module, "moduleCode");
        int version = ((Number) module.getOrDefault("version", 1)).intValue();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) module.get("items");
        String targetStatus = moduleStatuses.getOrDefault(code, "DRAFT");
        if (!VALID_STATUSES.contains(targetStatus)) {
            throw new IllegalStateException("Unknown governance status " + targetStatus + " for " + code);
        }

        // Activation proof: every active item must carry source provenance and
        // faithful-extraction markers; no invented severity/scoring fields.
        if ("ACTIVE".equals(targetStatus) || "VALIDATED_CURRENT_SOURCE".equals(targetStatus)) {
            validateModuleForActivation(code, module, items, allItemCodes);
        }

        String checksum = checksum(objectMapper.writeValueAsString(items));
        Optional<InspectionRequirementModuleEntity> existing = moduleRepository.findByCodeAndVersion(code, version);
        if (existing.isPresent()) {
            if (!existing.get().getContentChecksum().equals(checksum)) {
                throw new IllegalStateException("Immutable content violation: module " + code + " v" + version
                        + " already persisted with a different checksum. Content changes require a new version.");
            }
            return; // version already loaded, verified identical
        }

        InspectionRequirementModuleEntity entity = new InspectionRequirementModuleEntity();
        entity.setCode(code);
        entity.setVersion(version);
        entity.setTitle(requireText(module, "title"));
        entity.setScope(requireText(module, "scope"));
        entity.setSourceDoc((String) module.get("sourceDoc"));
        entity.setSourceHeading((String) module.get("sourceHeading"));
        entity.setSourcePages(objectMapper.convertValue(module.get("sourcePages"), new TypeReference<>() {}));
        entity.setNotes((String) module.get("notes"));
        entity.setItems(items);
        entity.setItemCount(items.size());
        entity.setReviewRequiredCount((int) items.stream()
                .filter(i -> Boolean.TRUE.equals(i.get("reviewRequired"))).count());
        entity.setContentChecksum(checksum);
        entity.setStatus(targetStatus);
        if ("ACTIVE".equals(targetStatus) || "VALIDATED_CURRENT_SOURCE".equals(targetStatus)) {
            entity.setApprovedBy(approvedBy);
            entity.setApprovedAt(Instant.now());
            entity.setEffectiveFrom(effectiveFrom);
        }
        moduleRepository.save(entity);
        log.info("Loaded inspection module {} v{} ({} items, {} review-required) status={}",
                code, version, entity.getItemCount(), entity.getReviewRequiredCount(), targetStatus);
    }

    private void validateModuleForActivation(String code, Map<String, Object> module,
                                             List<Map<String, Object>> items, Set<String> allItemCodes) {
        if (items == null || items.isEmpty()) {
            throw new IllegalStateException(code + ": cannot activate a module without items");
        }
        if (module.get("sourceHeading") == null || module.get("sourcePages") == null) {
            throw new IllegalStateException(code + ": activation requires module source heading and pages");
        }
        for (Map<String, Object> item : items) {
            String itemCode = (String) item.get("code");
            if (itemCode == null || itemCode.isBlank()) {
                throw new IllegalStateException(code + ": item without a stable code");
            }
            if (!allItemCodes.add(itemCode)) {
                throw new IllegalStateException(code + ": duplicate stable requirement code " + itemCode);
            }
            if (item.get("sourcePage") == null || item.get("sourceHeading") == null) {
                throw new IllegalStateException(code + "/" + itemCode
                        + ": every active item must map to a source page and heading");
            }
            if (item.get("requirement") == null || ((String) item.get("requirement")).isBlank()) {
                throw new IllegalStateException(code + "/" + itemCode + ": empty requirement text");
            }
            Object obligation = item.get("obligation");
            if (obligation == null || !VALID_OBLIGATIONS.contains(obligation.toString())) {
                throw new IllegalStateException(code + "/" + itemCode + ": invalid obligation " + obligation);
            }
            // No invented policy: severity/scoring/pass-thresholds are not part
            // of the manual and must not appear in source-derived content.
            if (item.containsKey("severity") || item.containsKey("critical")
                    || item.containsKey("score") || item.containsKey("passThreshold")) {
                throw new IllegalStateException(code + "/" + itemCode
                        + ": invented severity/scoring field present in source-derived content");
            }
            if (Boolean.TRUE.equals(item.get("reviewRequired"))
                    && (item.get("reviewReason") == null || ((String) item.get("reviewReason")).isBlank())) {
                throw new IllegalStateException(code + "/" + itemCode + ": reviewRequired without reviewReason");
            }
        }
    }

    private void loadComposition(Map<String, Object> composition, Set<String> loadedModuleCodes,
                                 String approvedBy, LocalDate effectiveFrom) {
        String code = requireText(composition, "code");
        int version = ((Number) composition.getOrDefault("version", 1)).intValue();
        @SuppressWarnings("unchecked")
        List<String> moduleCodes = (List<String>) composition.get("moduleCodes");
        if (moduleCodes == null || moduleCodes.isEmpty()) {
            throw new IllegalStateException("Composition " + code + " has no modules");
        }
        for (String moduleCode : moduleCodes) {
            if (!loadedModuleCodes.contains(moduleCode)) {
                throw new IllegalStateException("Composition " + code + " references unknown module " + moduleCode);
            }
        }
        if (compositionRepository.findByCodeAndVersion(code, version).isPresent()) {
            return;
        }
        InspectionTemplateCompositionEntity entity = new InspectionTemplateCompositionEntity();
        entity.setCode(code);
        entity.setVersion(version);
        entity.setTitle(requireText(composition, "title"));
        entity.setDescription((String) composition.get("description"));
        entity.setModuleCodes(moduleCodes);
        @SuppressWarnings("unchecked")
        List<String> inspectionTypes = (List<String>) composition.get("inspectionTypes");
        entity.setInspectionTypes(inspectionTypes);
        entity.setStatus((String) composition.getOrDefault("status", "ACTIVE"));
        entity.setApprovedBy(approvedBy);
        entity.setApprovedAt(Instant.now());
        entity.setEffectiveFrom(effectiveFrom);
        compositionRepository.save(entity);
    }

    private void loadApplicability(List<Map<String, Object>> mappings, Set<String> compositionCodes) {
        if (applicabilityRepository.count() > 0) {
            return; // idempotent: mapping set already loaded
        }
        for (Map<String, Object> mapping : mappings) {
            String compositionCode = requireText(mapping, "compositionCode");
            if (!compositionCodes.contains(compositionCode)) {
                throw new IllegalStateException("Applicability mapping references unknown composition "
                        + compositionCode);
            }
            ClassificationTemplateApplicabilityEntity entity = new ClassificationTemplateApplicabilityEntity();
            entity.setClassCode((String) mapping.get("classCode"));
            entity.setClassificationLabel((String) mapping.get("classificationLabel"));
            entity.setFacilityType((String) mapping.get("facilityType"));
            entity.setUnitType((String) mapping.get("unitType"));
            entity.setCompositionCode(compositionCode);
            entity.setRationale((String) mapping.get("rationale"));
            entity.setSourceDoc((String) mapping.get("sourceDoc"));
            applicabilityRepository.save(entity);
        }
    }

    private <T> T readJson(String location, TypeReference<T> type) throws Exception {
        Resource resource = new PathMatchingResourcePatternResolver().getResource(location);
        return objectMapper.readValue(resource.getInputStream(), type);
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
