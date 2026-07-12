package zw.gov.mohcc.impilo.tuso.core;

import org.springframework.stereotype.Component;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityClassificationMappingRuleEntity;
import zw.gov.mohcc.impilo.tuso.persistence.repository.FacilityClassificationMappingRuleRepository;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Interprets the ACTIVE classification mapping rule pack against a single
 * facility's source facts (facility_type, ownership, bed capacity) plus any
 * human-supplied facts (care_setting, confirmed bed count), producing a
 * conservative classification outcome.
 *
 * <p>Determinism is per dimension; the facility-level status is the weakest
 * HPA-relevant dimension. Nothing receives a definitive catalogue label without
 * the facts the manual requires; a catalogue gap yields AMBIGUOUS plus an
 * exception, never a rounded-up label.</p>
 */
@Component
public class ClassificationRuleEvaluator {

    private final FacilityClassificationMappingRuleRepository ruleRepository;

    public ClassificationRuleEvaluator(FacilityClassificationMappingRuleRepository ruleRepository) {
        this.ruleRepository = ruleRepository;
    }

    /** Facts fed into an evaluation. Human-supplied facts are null until reconciled. */
    public record Inputs(String sourceFacilityType,
                         String sourceOwnership,
                         Integer bedCountClaimed,
                         Integer bedCountConfirmed,
                         String careSetting) {}

    /** The evaluated outcome — mapped straight onto a profile row by the engine. */
    public record Outcome(String canonicalType,
                          String ownershipAuthority,
                          String hpaRoute,
                          String careSetting,
                          String hpaClass,
                          String hpaClassificationLabel,
                          String classificationStatus,
                          Map<String, Object> dimensionStatuses,
                          List<Map<String, Object>> suggestion,
                          List<String> requiredFacts,
                          Map<String, Object> sourceFieldsUsed,
                          String bedBand,
                          String bedBandBasis,
                          boolean unitsReviewRequired,
                          List<Map<String, Object>> exceptions,
                          String ruleCode,
                          Integer ruleVersion,
                          String evaluationFingerprint) {}

    @SuppressWarnings("unchecked")
    public Outcome evaluate(Inputs in) {
        FacilityClassificationMappingRuleEntity rule = ruleRepository
                .findFirstByStatusOrderByVersionDesc("ACTIVE")
                .orElseThrow(() -> new IllegalStateException(
                        "No ACTIVE facility classification mapping rule pack is loaded"));
        Map<String, Object> pack = rule.getRuleJson();
        Map<String, Object> ownershipTable = (Map<String, Object>) pack.get("ownershipAuthority");
        Map<String, Object> typeTable = (Map<String, Object>) pack.get("facilityType");
        List<String> unmappedTypes = (List<String>) pack.getOrDefault("unmappedFacilityTypes", List.of());
        List<Map<String, Object>> bedBands = (List<Map<String, Object>>) pack.get("bedBands");
        Map<String, Object> labels = (Map<String, Object>) pack.get("catalogueLabels");

        String type = norm(in.sourceFacilityType());
        String ownership = norm(in.sourceOwnership());

        List<Map<String, Object>> exceptions = new ArrayList<>();
        Map<String, Object> dims = new LinkedHashMap<>();
        List<Map<String, Object>> suggestion = new ArrayList<>();
        List<String> requiredFacts = new ArrayList<>();
        Map<String, Object> used = new LinkedHashMap<>();
        used.put("facility_type", in.sourceFacilityType());
        used.put("ownership", in.sourceOwnership());

        // ── Canonical type + care setting from the facility type ──────────────
        String canonicalType = null;
        String careSetting = "UNKNOWN";
        String canonicalConfidence = "UNMAPPED";
        boolean forceAmbiguous = false;
        String impliedOwnership = null;
        boolean inpatientDeterministic = false;

        if (type.isBlank()) {
            requiredFacts.add("facility_type");
            dims.put("canonicalType", "MISSING_REQUIRED_FACTS");
        } else if (unmappedTypes.contains(type)) {
            dims.put("canonicalType", "UNMAPPED");
            exceptions.add(exc("OWNERSHIP_AS_TYPE_DEFECT",
                    "facility_type '" + type + "' is an ownership/context value, not a facility type"));
        } else if (typeTable.containsKey(type)) {
            Map<String, Object> t = (Map<String, Object>) typeTable.get(type);
            canonicalType = (String) t.get("canonicalType");
            careSetting = (String) t.getOrDefault("careSetting", "UNKNOWN");
            inpatientDeterministic = "DETERMINISTIC".equals(t.get("careSettingConfidence"))
                    && "INPATIENT".equals(careSetting);
            canonicalConfidence = (String) t.getOrDefault("canonicalConfidence", "DETERMINISTIC");
            forceAmbiguous = Boolean.TRUE.equals(t.get("forceAmbiguous"));
            impliedOwnership = (String) t.get("impliedOwnership");
            dims.put("canonicalType", canonicalConfidence);
        } else {
            dims.put("canonicalType", "UNMAPPED");
            exceptions.add(exc("UNKNOWN_FACILITY_TYPE", "unrecognised facility_type '" + type + "'"));
        }

        // A human-supplied care setting always wins over the (usually UNKNOWN) source-derived one.
        if (in.careSetting() != null && !in.careSetting().isBlank()) {
            careSetting = in.careSetting();
            used.put("care_setting", careSetting);
            dims.put("careSetting", "HUMAN_SUPPLIED");
        } else {
            dims.put("careSetting", inpatientDeterministic ? "DETERMINISTIC" : "UNKNOWN");
        }

        // ── Ownership authority + HPA route ───────────────────────────────────
        String effectiveOwnership = !ownership.isBlank() ? ownership : impliedOwnership;
        String ownershipAuthority = "UNKNOWN";
        String route;
        String ownershipGroup;
        if (effectiveOwnership == null || effectiveOwnership.isBlank()) {
            route = "MISSING_REQUIRED_FACTS";
            ownershipGroup = "MISSING";
            requiredFacts.add("ownership");
            dims.put("ownership", "MISSING_REQUIRED_FACTS");
        } else if (ownershipTable.containsKey(effectiveOwnership)) {
            Map<String, Object> o = (Map<String, Object>) ownershipTable.get(effectiveOwnership);
            ownershipAuthority = (String) o.get("authority");
            route = (String) o.get("route");
            ownershipGroup = (String) o.get("group");
            dims.put("ownership", o.getOrDefault("confidence", "DETERMINISTIC"));
        } else {
            route = "AMBIGUOUS";
            ownershipGroup = "AMBIGUOUS";
            dims.put("ownership", "AMBIGUOUS");
            exceptions.add(exc("UNKNOWN_OWNERSHIP_VALUE", "unrecognised ownership '" + effectiveOwnership + "'"));
        }

        // Cross-field conflict: the type implies one owner group, the source says another.
        if (impliedOwnership != null && !ownership.isBlank()
                && ownershipTable.containsKey(impliedOwnership) && ownershipTable.containsKey(ownership)) {
            String impliedGroup = (String) ((Map<String, Object>) ownershipTable.get(impliedOwnership)).get("group");
            String sourceGroup = (String) ((Map<String, Object>) ownershipTable.get(ownership)).get("group");
            if (impliedGroup != null && !impliedGroup.equals(sourceGroup)) {
                route = "AMBIGUOUS";
                ownershipGroup = "AMBIGUOUS";
                exceptions.add(exc("OWNERSHIP_TYPE_CONFLICT",
                        "facility_type implies " + impliedOwnership + " (" + impliedGroup
                                + ") but ownership says " + ownership + " (" + sourceGroup + ")"));
            }
        }

        // ── Bed band (confirmed wins over claimed) ────────────────────────────
        Integer bedCount = in.bedCountConfirmed() != null ? in.bedCountConfirmed() : in.bedCountClaimed();
        String bedBandBasis = in.bedCountConfirmed() != null ? "CONFIRMED"
                : (in.bedCountClaimed() != null ? "CLAIMED" : null);
        String bedBand = bedCount != null ? bandFor(bedCount, bedBands) : null;
        if (bedCount != null) used.put("bed_count", bedCount);

        // ── HPA class letter ──────────────────────────────────────────────────
        String hpaClass = "NOT_YET_DETERMINED";
        if ("PUBLIC_MISSION_LA".equals(route)) {
            hpaClass = "C";                       // definitional for the public group (HPA-2017-V1 p15)
            dims.put("hpaClass", "DETERMINISTIC");
        } else if ("PRIVATE".equals(route)) {
            dims.put("hpaClass", "PENDING_LABEL"); // A vs B decided with the label
        } else {
            dims.put("hpaClass", "NOT_YET_DETERMINED");
        }

        // ── Institution label + facility-level status ─────────────────────────
        String label = null;
        String status;

        if (canonicalType == null) {
            status = type.isBlank() ? "MISSING_REQUIRED_FACTS" : "UNMAPPED";
        } else if (forceAmbiguous) {
            status = "AMBIGUOUS";
        } else if ("AMBIGUOUS".equals(route) || "AMBIGUOUS".equals(ownershipGroup)) {
            status = "AMBIGUOUS";
        } else if ("MISSING_REQUIRED_FACTS".equals(route)) {
            status = "MISSING_REQUIRED_FACTS";
        } else if ("HOSPITAL".equals(canonicalType)) {
            if (bedBand == null) {
                requiredFacts.add("bed_count_confirmed");
                status = "MISSING_REQUIRED_FACTS";
            } else if ("PUBLIC_MISSION_LA".equals(route)) {
                Map<String, Object> pub = (Map<String, Object>) labels.get("publicHospital");
                String l = (String) pub.get(bedBand);
                if (l == null || "__CATALOGUE_GAP__".equals(l)) {
                    status = "AMBIGUOUS";
                    exceptions.add(exc("CATALOGUE_GAP_PUBLIC_HOSPITAL_UNDER_51_BEDS",
                            "no Class-C catalogue row exists for a public hospital in band " + bedBand));
                } else {
                    label = l;
                    status = "DETERMINISTIC_MULTI_FIELD_MATCH";
                }
            } else { // PRIVATE
                Map<String, Object> priv = (Map<String, Object>) labels.get("privateHospital");
                label = (String) priv.get(bedBand);
                hpaClass = "A";
                status = label != null ? "DETERMINISTIC_MULTI_FIELD_MATCH" : "AMBIGUOUS";
            }
        } else { // CLINIC or RURAL_HEALTH_CENTRE
            if ("UNKNOWN".equals(careSetting)) {
                requiredFacts.add("care_setting");
                status = "MISSING_REQUIRED_FACTS";
                if ("PRIVATE".equals(route)) {
                    suggestion.add(candidate("B", (String) labels.get("privateClinicOutpatient"), "if outpatient-only"));
                    suggestion.add(candidate("A", (String) labels.get("privateClinicBedded"), "if up to 50 beds"));
                    status = "AMBIGUOUS";
                }
            } else if ("PUBLIC_MISSION_LA".equals(route)) {
                if ("OUTPATIENT_ONLY".equals(careSetting)) {
                    label = (String) labels.get("publicClinicOutpatient");
                    status = "DETERMINISTIC_MULTI_FIELD_MATCH";
                } else { // inpatient public clinic — catalogue gap
                    status = "AMBIGUOUS";
                    exceptions.add(exc("CATALOGUE_GAP_PUBLIC_CLINIC_WITH_BEDS",
                            "no Class-C catalogue row exists for a public clinic with beds"));
                }
            } else { // PRIVATE
                if ("OUTPATIENT_ONLY".equals(careSetting)) {
                    label = (String) labels.get("privateClinicOutpatient");
                    hpaClass = "B";
                } else {
                    label = (String) labels.get("privateClinicBedded");
                    hpaClass = "A";
                }
                status = "DETERMINISTIC_MULTI_FIELD_MATCH";
            }
        }

        // A high-confidence canonical suggestion (HC/HEALTH_CENTRE) never auto-applies.
        if ("SUGGESTED".equals(canonicalConfidence) && !"UNMAPPED".equals(status)) {
            status = "SUGGESTED_HIGH_CONFIDENCE";
        }

        boolean unitsReview = "HOSPITAL".equals(canonicalType);

        String fingerprint;
        try {
            fingerprint = ClassificationMappingSeeder.checksum(
                    rule.getCode() + "|" + rule.getVersion() + "|" + used);
        } catch (Exception e) {
            fingerprint = rule.getCode() + ":" + rule.getVersion() + ":" + used.hashCode();
        }

        return new Outcome(canonicalType, ownershipAuthority, route, careSetting, hpaClass, label, status,
                dims, suggestion, requiredFacts.stream().distinct().toList(), used, bedBand, bedBandBasis,
                unitsReview, exceptions, rule.getCode(), rule.getVersion(), fingerprint);
    }

    private static String bandFor(int beds, List<Map<String, Object>> bedBands) {
        for (Map<String, Object> b : bedBands) {
            Integer min = b.get("minInclusive") == null ? null : ((Number) b.get("minInclusive")).intValue();
            Integer max = b.get("maxInclusive") == null ? null : ((Number) b.get("maxInclusive")).intValue();
            if ((min == null || beds >= min) && (max == null || beds <= max)) {
                return (String) b.get("band");
            }
        }
        return null;
    }

    private static Map<String, Object> candidate(String classCode, String label, String condition) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("classCode", classCode);
        m.put("label", label);
        m.put("condition", condition);
        return m;
    }

    private static Map<String, Object> exc(String code, String detail) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("code", code);
        m.put("detail", detail);
        return m;
    }

    private static String norm(String s) {
        return s == null ? "" : s.trim();
    }
}
