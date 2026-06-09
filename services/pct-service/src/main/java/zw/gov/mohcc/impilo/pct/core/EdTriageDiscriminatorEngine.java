package zw.gov.mohcc.impilo.pct.core;

import java.util.*;

/**
 * Formal ESI (Emergency Severity Index) and MTS (Manchester Triage System) discriminator scoring.
 * Maps structured answers to acuity 1–5 and national colour bands.
 */
public final class EdTriageDiscriminatorEngine {

    public record TriageScoreResult(
            String system,
            int acuity,
            String category,
            String colour,
            String rationale,
            Map<String, Object> computedDiscriminators) {}

    private EdTriageDiscriminatorEngine() {}

    public static List<Map<String, Object>> discriminatorCatalog() {
        return List.of(
                Map.of("id", "airway_compromise", "label", "Airway compromise", "systems", List.of("ESI", "MTS")),
                Map.of("id", "requires_resuscitation", "label", "Requires immediate life-saving intervention", "systems", List.of("ESI", "MTS")),
                Map.of("id", "high_risk_situation", "label", "High-risk situation / critical history", "systems", List.of("ESI")),
                Map.of("id", "altered_consciousness", "label", "New confusion / lethargy / disorientation", "systems", List.of("ESI", "MTS")),
                Map.of("id", "severe_pain_distress", "label", "Severe pain or distress", "systems", List.of("ESI", "MTS")),
                Map.of("id", "vital_sign_danger", "label", "Vital signs in danger zone", "systems", List.of("ESI", "MTS")),
                Map.of("id", "haemorrhage_uncontrolled", "label", "Uncontrolled haemorrhage", "systems", List.of("MTS")),
                Map.of("id", "resources_needed", "label", "Expected ED resources (labs, imaging, IV, specialist)", "systems", List.of("ESI"), "type", "integer"),
                Map.of("id", "pain_score", "label", "Pain score 0–10", "systems", List.of("ESI", "MTS"), "type", "integer"),
                Map.of("id", "news2_score", "label", "NEWS2 score", "systems", List.of("MTS"), "type", "integer")
        );
    }

    @SuppressWarnings("unchecked")
    public static TriageScoreResult calculate(String system, Map<String, Object> discriminators, Map<String, Object> vitals) {
        Map<String, Object> merged = new LinkedHashMap<>();
        if (discriminators != null) merged.putAll(discriminators);
        if (vitals != null) merged.put("vitals", vitals);
        String normalized = system == null ? "ESI" : system.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "MTS", "MANCHESTER" -> scoreMts(merged);
            default -> scoreEsi(merged);
        };
    }

    public static Map<String, Object> scoreBoth(Map<String, Object> discriminators, Map<String, Object> vitals) {
        TriageScoreResult esi = scoreEsi(merge(discriminators, vitals));
        TriageScoreResult mts = scoreMts(merge(discriminators, vitals));
        return Map.of(
                "esi", resultMap(esi),
                "mts", resultMap(mts),
                "recommended_acuity", Math.min(esi.acuity(), mts.acuity()),
                "recommended_system", esi.acuity() <= mts.acuity() ? "ESI" : "MTS");
    }

    private static Map<String, Object> merge(Map<String, Object> discriminators, Map<String, Object> vitals) {
        Map<String, Object> m = new LinkedHashMap<>();
        if (discriminators != null) m.putAll(discriminators);
        if (vitals != null) m.put("vitals", vitals);
        return m;
    }

    private static Map<String, Object> resultMap(TriageScoreResult r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("system", r.system());
        m.put("acuity", r.acuity());
        m.put("category", r.category());
        m.put("colour", r.colour());
        m.put("rationale", r.rationale());
        m.put("computed_discriminators", r.computedDiscriminators());
        return m;
    }

    @SuppressWarnings("unchecked")
    private static TriageScoreResult scoreEsi(Map<String, Object> input) {
        boolean resus = bool(input, "requires_resuscitation", "requiresResuscitation");
        boolean airway = bool(input, "airway_compromise", "airwayCompromise");
        boolean highRisk = bool(input, "high_risk_situation", "highRiskSituation");
        boolean altered = bool(input, "altered_consciousness", "alteredConsciousness");
        boolean severePain = bool(input, "severe_pain_distress", "severePainDistress")
                || intVal(input, "pain_score", "painScore") >= 8;
        boolean dangerVitals = bool(input, "vital_sign_danger", "vitalSignDanger") || vitalsInDangerZone(input);
        int resources = intVal(input, "resources_needed", "resourcesNeeded", "expected_resources");

        int level;
        String rationale;
        if (resus || airway) {
            level = 1;
            rationale = "ESI 1 — immediate life-saving intervention indicated";
        } else if (highRisk || altered || severePain || dangerVitals) {
            level = 2;
            rationale = "ESI 2 — high risk, distress, or danger-zone physiology";
        } else if (resources >= 2) {
            level = 3;
            rationale = "ESI 3 — two or more ED resources anticipated";
        } else if (resources == 1) {
            level = 4;
            rationale = "ESI 4 — one ED resource anticipated";
        } else {
            level = 5;
            rationale = "ESI 5 — no additional ED resources anticipated";
        }

        return new TriageScoreResult("ESI", level, "ESI_" + level, colourForAcuity(level), rationale, Map.copyOf(input));
    }

    @SuppressWarnings("unchecked")
    private static TriageScoreResult scoreMts(Map<String, Object> input) {
        boolean immediate = bool(input, "requires_resuscitation", "requiresResuscitation")
                || bool(input, "airway_compromise", "airwayCompromise")
                || bool(input, "haemorrhage_uncontrolled", "haemorrhageUncontrolled");
        boolean veryUrgent = bool(input, "altered_consciousness", "alteredConsciousness")
                || bool(input, "severe_pain_distress", "severePainDistress")
                || vitalsInDangerZone(input)
                || intVal(input, "news2_score", "news2Score") >= 7
                || intVal(input, "pain_score", "painScore") >= 8;
        int resources = intVal(input, "resources_needed", "resourcesNeeded");
        boolean urgent = resources >= 2 || intVal(input, "pain_score", "painScore") >= 5;

        int acuity;
        String category;
        String rationale;
        if (immediate) {
            acuity = 1;
            category = "RED";
            rationale = "MTS RED — immediate life threat";
        } else if (veryUrgent) {
            acuity = 2;
            category = "ORANGE";
            rationale = "MTS ORANGE — very urgent presentation";
        } else if (urgent) {
            acuity = 3;
            category = "YELLOW";
            rationale = "MTS YELLOW — urgent, multiple resources or significant pain";
        } else if (resources == 1) {
            acuity = 4;
            category = "GREEN";
            rationale = "MTS GREEN — standard priority, one resource";
        } else {
            acuity = 5;
            category = "BLUE";
            rationale = "MTS BLUE — non-urgent";
        }
        return new TriageScoreResult("MTS", acuity, category, colourForAcuity(acuity), rationale, Map.copyOf(input));
    }

    @SuppressWarnings("unchecked")
    private static boolean vitalsInDangerZone(Map<String, Object> input) {
        Object vitalsObj = input.get("vitals");
        if (!(vitalsObj instanceof Map<?, ?> vitals)) return false;
        Map<String, Object> v = (Map<String, Object>) vitals;
        int hr = intVal(v, "heartRate", "hr", "heart_rate");
        int sbp = intVal(v, "systolicBp", "sbp", "systolic_bp", "bp_systolic");
        int spo2 = intVal(v, "spo2", "spO2", "oxygen_saturation");
        int rr = intVal(v, "respiratoryRate", "rr", "resp_rate");
        return (hr > 0 && (hr < 40 || hr > 130))
                || (sbp > 0 && sbp < 90)
                || (spo2 > 0 && spo2 < 92)
                || (rr > 0 && (rr < 8 || rr > 30));
    }

    private static String colourForAcuity(int acuity) {
        return switch (acuity) {
            case 1 -> "RED";
            case 2 -> "ORANGE";
            case 3 -> "YELLOW";
            case 4 -> "GREEN";
            default -> "BLUE";
        };
    }

    private static boolean bool(Map<String, Object> m, String... keys) {
        for (String key : keys) {
            Object v = m.get(key);
            if (v instanceof Boolean b) return b;
            if (v != null && Boolean.parseBoolean(v.toString())) return true;
        }
        return false;
    }

    private static int intVal(Map<String, Object> m, String... keys) {
        for (String key : keys) {
            Object v = m.get(key);
            if (v instanceof Number n) return n.intValue();
            if (v != null) {
                try {
                    return Integer.parseInt(v.toString().trim());
                } catch (NumberFormatException ignored) {
                    /* next key */
                }
            }
        }
        return 0;
    }
}
