package zw.gov.mohcc.impilo.experience.service;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Deterministic free-text → service-capability interpreter for the citizen "find care" lane.
 *
 * <p>A person types what they need in ordinary language ("x-ray", "maternity", "hiv medicines",
 * "a paediatrician"); this maps that to the canonical TUSO capability tokens the facility-registry
 * search matches on, plus a few intent flags (telemedicine / emergency / accessibility). It is a
 * seeded, in-code synonym table — no network, no model — so the mapping is auditable and stable.</p>
 *
 * <p>The token list is ordered most-specific first; the orchestrator uses the first token as the
 * primary capability facet for the registry search. {@link #interpret(String)} is the single seam a
 * later LLM/semantic path can override without changing the orchestrator: return the same
 * {@link Interpretation} shape and everything downstream is unchanged.</p>
 */
@Component
public class FindCareServiceTaxonomy {

    /** Result of interpreting a free-text care need. */
    public record Interpretation(
            String rawText,
            List<String> serviceTokens,
            String primaryToken,
            boolean telemedicine,
            boolean emergency,
            boolean accessibility,
            boolean recognized) {}

    /**
     * Synonym → ordered capability tokens (most specific first). Keys are lower-cased phrases; a key
     * matches when it appears as a whole-word/substring of the normalized query. TUSO matches these
     * tokens against capability code OR display name, so the tokens double as both.
     */
    private static final Map<String, List<String>> SYNONYMS = new LinkedHashMap<>();
    static {
        // Maternal / child
        SYNONYMS.put("maternity", List.of("MATERNITY", "ANTENATAL"));
        SYNONYMS.put("antenatal", List.of("ANTENATAL", "MATERNITY"));
        SYNONYMS.put("pregnan", List.of("ANTENATAL", "MATERNITY"));
        SYNONYMS.put("delivery", List.of("MATERNITY", "OBSTETRICS"));
        SYNONYMS.put("birth", List.of("MATERNITY", "OBSTETRICS"));
        SYNONYMS.put("paediatric", List.of("PAEDIATRICS"));
        SYNONYMS.put("pediatric", List.of("PAEDIATRICS"));
        SYNONYMS.put("child", List.of("PAEDIATRICS"));
        SYNONYMS.put("immunis", List.of("IMMUNIZATION", "PAEDIATRICS"));
        SYNONYMS.put("immuniz", List.of("IMMUNIZATION", "PAEDIATRICS"));
        SYNONYMS.put("vaccin", List.of("IMMUNIZATION"));
        // Imaging / diagnostics
        SYNONYMS.put("x-ray", List.of("RADIOLOGY", "IMAGING"));
        SYNONYMS.put("xray", List.of("RADIOLOGY", "IMAGING"));
        SYNONYMS.put("radiology", List.of("RADIOLOGY", "IMAGING"));
        SYNONYMS.put("scan", List.of("IMAGING", "RADIOLOGY"));
        SYNONYMS.put("ultrasound", List.of("ULTRASOUND", "IMAGING"));
        SYNONYMS.put("mri", List.of("IMAGING", "RADIOLOGY"));
        SYNONYMS.put("ct scan", List.of("IMAGING", "RADIOLOGY"));
        SYNONYMS.put("lab", List.of("LABORATORY"));
        SYNONYMS.put("blood test", List.of("LABORATORY"));
        SYNONYMS.put("pathology", List.of("LABORATORY"));
        // Pharmacy / HIV / TB
        SYNONYMS.put("pharmacy", List.of("PHARMACY"));
        SYNONYMS.put("medicine", List.of("PHARMACY"));
        SYNONYMS.put("medication", List.of("PHARMACY"));
        SYNONYMS.put("prescription", List.of("PHARMACY"));
        SYNONYMS.put("hiv medicine", List.of("ART", "PHARMACY"));
        SYNONYMS.put("hiv medicines", List.of("ART", "PHARMACY"));
        SYNONYMS.put("art", List.of("ART"));
        SYNONYMS.put("arv", List.of("ART", "PHARMACY"));
        SYNONYMS.put("hiv", List.of("HIV", "ART"));
        SYNONYMS.put("tb", List.of("TB"));
        SYNONYMS.put("tuberculosis", List.of("TB"));
        // Chronic / specialist
        SYNONYMS.put("dialysis", List.of("RENAL", "DIALYSIS"));
        SYNONYMS.put("renal", List.of("RENAL", "DIALYSIS"));
        SYNONYMS.put("kidney", List.of("RENAL", "DIALYSIS"));
        SYNONYMS.put("diabet", List.of("DIABETES", "CHRONIC_CARE"));
        SYNONYMS.put("cancer", List.of("ONCOLOGY"));
        SYNONYMS.put("oncology", List.of("ONCOLOGY"));
        SYNONYMS.put("mental health", List.of("MENTAL_HEALTH", "PSYCHIATRY"));
        SYNONYMS.put("psychiatr", List.of("PSYCHIATRY", "MENTAL_HEALTH"));
        SYNONYMS.put("dental", List.of("DENTAL"));
        SYNONYMS.put("dentist", List.of("DENTAL"));
        SYNONYMS.put("eye", List.of("OPHTHALMOLOGY", "OPTICAL"));
        SYNONYMS.put("optical", List.of("OPTICAL", "OPHTHALMOLOGY"));
        SYNONYMS.put("surgery", List.of("SURGERY"));
        SYNONYMS.put("surgical", List.of("SURGERY"));
        // General / primary
        SYNONYMS.put("opd", List.of("OPD", "GENERAL"));
        SYNONYMS.put("outpatient", List.of("OPD", "GENERAL"));
        SYNONYMS.put("general", List.of("GENERAL", "OPD"));
        SYNONYMS.put("clinic", List.of("OPD", "GENERAL"));
        SYNONYMS.put("doctor", List.of("GENERAL", "OPD"));
        SYNONYMS.put("family plan", List.of("FAMILY_PLANNING"));
        SYNONYMS.put("contracept", List.of("FAMILY_PLANNING"));
    }

    /** Intent-flag phrases (do not, on their own, produce a capability token). */
    private static final List<String> TELEMED_TERMS = List.of("telemedicine", "telehealth", "online", "video", "remote", "virtual");
    private static final List<String> EMERGENCY_TERMS = List.of("emergency", "casualty", "accident", "urgent", "trauma", "ambulance");
    private static final List<String> ACCESSIBILITY_TERMS = List.of("wheelchair", "accessible", "disabled", "disability", "ramp");

    /**
     * Interpret a free-text care need into capability tokens + intent flags. Deterministic; the
     * override seam for a future LLM path. Never throws — an unrecognized phrase yields an
     * interpretation with {@code recognized=false} and no tokens (the caller then falls back to a
     * plain directory search).
     */
    public Interpretation interpret(String freeText) {
        String raw = freeText == null ? "" : freeText;
        String norm = raw.toLowerCase().trim();
        if (norm.isBlank()) {
            return new Interpretation(raw, List.of(), null, false, false, false, false);
        }

        List<String> tokens = new ArrayList<>();
        for (Map.Entry<String, List<String>> e : SYNONYMS.entrySet()) {
            if (norm.contains(e.getKey())) {
                for (String t : e.getValue()) {
                    if (!tokens.contains(t)) {
                        tokens.add(t);
                    }
                }
            }
        }

        boolean telemed = containsAny(norm, TELEMED_TERMS);
        boolean emergency = containsAny(norm, EMERGENCY_TERMS);
        boolean accessibility = containsAny(norm, ACCESSIBILITY_TERMS);

        String primary = tokens.isEmpty() ? null : tokens.get(0);
        boolean recognized = !tokens.isEmpty();
        return new Interpretation(raw, List.copyOf(tokens), primary, telemed, emergency, accessibility, recognized);
    }

    private static boolean containsAny(String norm, List<String> terms) {
        for (String t : terms) {
            if (norm.contains(t)) {
                return true;
            }
        }
        return false;
    }
}
