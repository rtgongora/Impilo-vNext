package zw.gov.mohcc.impilo.tuso.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.tuso.persistence.entity.ClassificationTemplateApplicabilityEntity;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityEntity;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityInspectionEntity;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityUnitEntity;
import zw.gov.mohcc.impilo.tuso.persistence.entity.InspectionRequirementModuleEntity;
import zw.gov.mohcc.impilo.tuso.persistence.entity.InspectionTemplateCompositionEntity;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityRegulatoryProfileEntity;
import zw.gov.mohcc.impilo.tuso.persistence.repository.ClassificationTemplateApplicabilityRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.FacilityRegulatoryProfileRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.FacilityUnitRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.InspectionRequirementModuleRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.InspectionTemplateCompositionRepository;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Resolves the correct inspection checklist for a facility from the
 * manual-derived content catalogue, and assembles composed checklists with
 * exact module-version provenance.
 *
 * <p>Resolution is EXPLICIT: facility classification label → facility type →
 * regulated unit types, via the applicability map. A facility whose profile
 * matches nothing gets an error naming the gap — never a silent generic
 * checklist. An authorised regulator may add justified supplementary modules
 * at scheduling time; the justification is recorded on the inspection.</p>
 */
@Service
public class InspectionContentService {

    private static final Logger log = LoggerFactory.getLogger(InspectionContentService.class);

    private final InspectionRequirementModuleRepository moduleRepository;
    private final InspectionTemplateCompositionRepository compositionRepository;
    private final ClassificationTemplateApplicabilityRepository applicabilityRepository;
    private final FacilityUnitRepository facilityUnitRepository;
    private final FacilityRegulatoryProfileRepository profileRepository;

    public InspectionContentService(InspectionRequirementModuleRepository moduleRepository,
                                    InspectionTemplateCompositionRepository compositionRepository,
                                    ClassificationTemplateApplicabilityRepository applicabilityRepository,
                                    FacilityUnitRepository facilityUnitRepository,
                                    FacilityRegulatoryProfileRepository profileRepository) {
        this.moduleRepository = moduleRepository;
        this.compositionRepository = compositionRepository;
        this.applicabilityRepository = applicabilityRepository;
        this.facilityUnitRepository = facilityUnitRepository;
        this.profileRepository = profileRepository;
    }

    private static final Set<String> RESOLVABLE_PROFILE_STATUSES = Set.of(
            "HUMAN_CONFIRMED", "DETERMINISTIC_MULTI_FIELD_MATCH", "EXACT_SOURCE_MATCH");

    /** The outcome of composition resolution, frozen onto the inspection row. */
    public record ResolvedContent(String compositionCode, Map<String, Integer> moduleVersions,
                                  List<String> moduleCodes, List<String> resolutionTrail) {}

    /**
     * Resolve applicable compositions for a facility (+ its regulated units),
     * merge their modules (common module first, no duplication), and freeze
     * exact module versions.
     *
     * @param explicitCompositionCode regulator-chosen composition (validated, never silent)
     * @param supplementaryModules    regulator-justified additional modules
     */
    @Transactional(readOnly = true)
    public ResolvedContent resolveForFacility(FacilityEntity facility,
                                              String explicitCompositionCode,
                                              List<String> supplementaryModules) {
        List<String> trail = new ArrayList<>();
        LinkedHashSet<String> compositionCodes = new LinkedHashSet<>();

        if (explicitCompositionCode != null && !explicitCompositionCode.isBlank()) {
            requireActiveComposition(explicitCompositionCode);
            compositionCodes.add(explicitCompositionCode);
            trail.add("explicit composition " + explicitCompositionCode + " selected by authorised regulator");
        } else {
            Optional<FacilityRegulatoryProfileEntity> profileOpt = profileRepository.findByFacilityId(facility.getId());
            if (profileOpt.isPresent()) {
                // A structured classification profile is authoritative and its
                // resolution is gated on the profile being CONFIRMED (or already
                // deterministic). An unconfirmed/ambiguous profile HARD-STOPS —
                // it never silently falls through to metadata/free-text.
                FacilityRegulatoryProfileEntity profile = profileOpt.get();
                String status = profile.getClassificationStatus();
                if (!RESOLVABLE_PROFILE_STATUSES.contains(status)) {
                    throw new IllegalStateException("Facility " + facility.getId()
                            + " has an unconfirmed classification profile (status=" + status
                            + ", required facts=" + profile.getRequiredFacts()
                            + "). Resolve it in the classification reconciliation queue before scheduling an "
                            + "inspection; a definitive checklist will NOT be substituted.");
                }
                if ("NOT_APPLICABLE".equals(profile.getHpaClass())) {
                    throw new IllegalStateException("Facility " + facility.getId()
                            + " classification is NOT_APPLICABLE (" + profile.getHpaClassJustification()
                            + "); no inspection composition applies.");
                }
                String label = profile.getHpaClassificationLabel();
                if (label != null) {
                    for (ClassificationTemplateApplicabilityEntity mapping
                            : applicabilityRepository.findByClassificationLabelIgnoreCase(label)) {
                        compositionCodes.add(mapping.getCompositionCode());
                        trail.add("confirmed classification '" + label + "' (status " + status
                                + ", rule " + profile.getMappingRuleCode() + " v" + profile.getMappingRuleVersion()
                                + ") → " + mapping.getCompositionCode());
                    }
                }
            } else {
                // No profile row at all (e.g. a facility born via a fresh registration
                // application before the estate backfill): legacy behaviour, unchanged.
                String classificationLabel = facility.getMetadata() != null
                        ? asText(facility.getMetadata().get("hpaClassificationLabel")) : null;
                if (classificationLabel != null) {
                    for (ClassificationTemplateApplicabilityEntity mapping
                            : applicabilityRepository.findByClassificationLabelIgnoreCase(classificationLabel)) {
                        compositionCodes.add(mapping.getCompositionCode());
                        trail.add("classification '" + classificationLabel + "' → " + mapping.getCompositionCode());
                    }
                }
                if (compositionCodes.isEmpty() && facility.getFacilityType() != null) {
                    for (ClassificationTemplateApplicabilityEntity mapping
                            : applicabilityRepository.findByFacilityTypeIgnoreCase(facility.getFacilityType())) {
                        compositionCodes.add(mapping.getCompositionCode());
                        trail.add("facility type '" + facility.getFacilityType() + "' → " + mapping.getCompositionCode());
                    }
                }
            }
            // Regulated units attach their specific compositions regardless of
            // how the facility-level composition was found.
            for (FacilityUnitEntity unit : facilityUnitRepository.findByFacilityIdOrderByCreatedAtAsc(facility.getId())) {
                if (unit.getUnitType() == null) {
                    continue;
                }
                List<ClassificationTemplateApplicabilityEntity> unitMappings =
                        applicabilityRepository.findByUnitTypeIgnoreCase(unit.getUnitType());
                for (ClassificationTemplateApplicabilityEntity mapping : unitMappings) {
                    if (compositionCodes.add(mapping.getCompositionCode())) {
                        trail.add("regulated unit '" + unit.getName() + "' (" + unit.getUnitType()
                                + ") → " + mapping.getCompositionCode());
                    }
                }
            }
        }

        if (compositionCodes.isEmpty()) {
            throw new IllegalStateException("No inspection template composition is mapped for facility "
                    + facility.getId() + " (classification="
                    + (facility.getMetadata() != null ? facility.getMetadata().get("hpaClassificationLabel") : null)
                    + ", facilityType=" + facility.getFacilityType()
                    + ", no mapped regulated units). A generic checklist will NOT be substituted — record the "
                    + "classification or select a composition explicitly with justification.");
        }

        // Merge module lists: common module first, then service-specific in
        // composition order, deduplicated.
        LinkedHashSet<String> moduleCodes = new LinkedHashSet<>();
        for (String compositionCode : compositionCodes) {
            InspectionTemplateCompositionEntity composition = requireActiveComposition(compositionCode);
            moduleCodes.addAll(composition.getModuleCodes());
        }
        if (supplementaryModules != null) {
            for (String supplementary : supplementaryModules) {
                if (moduleCodes.add(supplementary)) {
                    trail.add("supplementary module " + supplementary + " added by authorised regulator");
                }
            }
        }

        Map<String, Integer> frozenVersions = new LinkedHashMap<>();
        List<String> ordered = new ArrayList<>(moduleCodes);
        ordered.sort(Comparator.comparing(code -> !"MOD-ALL-HEALTH-INSTITUTIONS".equals(code)));
        for (String moduleCode : ordered) {
            InspectionRequirementModuleEntity module = moduleRepository
                    .findFirstByCodeAndStatusOrderByVersionDesc(moduleCode, "ACTIVE")
                    .orElseThrow(() -> new IllegalStateException("Module " + moduleCode
                            + " has no ACTIVE version — cannot compose an inspection checklist from it"));
            frozenVersions.put(module.getCode(), module.getVersion());
        }

        String compositionLabel = compositionCodes.size() == 1
                ? compositionCodes.iterator().next()
                : "MERGED:" + String.join("+", compositionCodes);
        log.info("Resolved inspection content for facility {}: {} ({} modules)",
                facility.getId(), compositionLabel, frozenVersions.size());
        return new ResolvedContent(compositionLabel, frozenVersions, ordered, trail);
    }

    /** Assemble the full checklist for an inspection from its frozen module versions. */
    @Transactional(readOnly = true)
    public Map<String, Object> checklistFor(FacilityInspectionEntity inspection) {
        Map<String, Object> checklist = new LinkedHashMap<>();
        checklist.put("inspectionId", inspection.getInspectionId());
        if (inspection.getModuleVersions() == null || inspection.getModuleVersions().isEmpty()) {
            // Legacy template-based inspection — surface the template content.
            checklist.put("mode", "TEMPLATE");
            if (inspection.getTemplate() != null) {
                checklist.put("templateCode", inspection.getTemplate().getCode());
                checklist.put("templateVersion", inspection.getTemplate().getVersion());
                checklist.put("templateStatus", inspection.getTemplate().getStatus());
                checklist.put("items", inspection.getTemplate().getItemsJson());
            }
            return checklist;
        }
        checklist.put("mode", "COMPOSED");
        checklist.put("compositionCode", inspection.getCompositionCode());
        List<Map<String, Object>> modules = new ArrayList<>();
        int totalItems = 0;
        // JSONB storage does not preserve key order — reassert: common module
        // first, then service-specific modules in stable alphabetical order.
        List<Map.Entry<String, Integer>> orderedEntries = new ArrayList<>(inspection.getModuleVersions().entrySet());
        orderedEntries.sort(Comparator
                .comparing((Map.Entry<String, Integer> e) -> !"MOD-ALL-HEALTH-INSTITUTIONS".equals(e.getKey()))
                .thenComparing(Map.Entry::getKey));
        for (Map.Entry<String, Integer> entry : orderedEntries) {
            InspectionRequirementModuleEntity module = moduleRepository
                    .findByCodeAndVersion(entry.getKey(), entry.getValue())
                    .orElseThrow(() -> new IllegalStateException("Frozen module version missing: "
                            + entry.getKey() + " v" + entry.getValue()
                            + " — version history must be immutable"));
            Map<String, Object> view = new LinkedHashMap<>();
            view.put("code", module.getCode());
            view.put("version", module.getVersion());
            view.put("title", module.getTitle());
            view.put("scope", module.getScope());
            view.put("sourceDoc", module.getSourceDoc());
            view.put("sourceHeading", module.getSourceHeading());
            view.put("sourcePages", module.getSourcePages());
            view.put("notes", module.getNotes());
            view.put("items", module.getItems());
            modules.add(view);
            totalItems += module.getItemCount();
        }
        checklist.put("modules", modules);
        checklist.put("totalItems", totalItems);
        return checklist;
    }

    @Transactional(readOnly = true)
    public List<InspectionRequirementModuleEntity> listModules() {
        return moduleRepository.findAllByOrderByCodeAscVersionDesc();
    }

    @Transactional(readOnly = true)
    public List<InspectionTemplateCompositionEntity> listCompositions() {
        return compositionRepository.findAllByOrderByCodeAscVersionDesc();
    }

    @Transactional(readOnly = true)
    public List<ClassificationTemplateApplicabilityEntity> listApplicability() {
        return applicabilityRepository.findAll();
    }

    private InspectionTemplateCompositionEntity requireActiveComposition(String code) {
        return compositionRepository.findFirstByCodeAndStatusOrderByVersionDesc(code, "ACTIVE")
                .orElseThrow(() -> new IllegalArgumentException(
                        "Inspection template composition not found or not ACTIVE: " + code));
    }

    private static String asText(Object value) {
        return value == null || value.toString().isBlank() ? null : value.toString();
    }
}
