package zw.gov.mohcc.impilo.experience.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import zw.gov.mohcc.impilo.experience.client.LlmStructuredServiceClient;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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

    private static final Logger log = LoggerFactory.getLogger(FindCareServiceTaxonomy.class);

    /**
     * The canonical vocabulary — the union of every token the deterministic map can emit. The
     * optional LLM path is fenced to this set: a model may only ever return a token that already
     * exists here, so the NL upgrade can never invent a capability the registry search does not
     * understand (honesty gate). Built lazily from {@link #SYNONYMS} in the static initializer.
     */
    private static final Set<String> KNOWN_TOKENS = new LinkedHashSet<>();

    /**
     * Optional LLM structured-interpret seam. Off by default ({@code impilo.findcare.llm-interpret.enabled=false})
     * so the deterministic path always ships; when enabled AND a governed provider is configured,
     * free text is routed through {@link LlmStructuredServiceClient} and the result is validated
     * against {@link #KNOWN_TOKENS} before it is trusted. Any failure falls back to the synonym map.
     */
    private final LlmStructuredServiceClient llmClient;
    private final boolean llmInterpretEnabled;

    public FindCareServiceTaxonomy(
            LlmStructuredServiceClient llmClient,
            @Value("${impilo.findcare.llm-interpret.enabled:false}") boolean llmInterpretEnabled) {
        this.llmClient = llmClient;
        this.llmInterpretEnabled = llmInterpretEnabled;
    }

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

    /**
     * Related-service families keyed by canonical token → the tokens that should be searched together.
     * Populated from {@link #SYNONYMS} co-occurrence in the static initializer below.
     */
    private static final Map<String, List<String>> TOKEN_FAMILY = new LinkedHashMap<>();

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
        // Emergency / casualty — the EMERGENCY capability is seeded for every hospital tier
        // (tuso V028). Without these, free-text "emergency" was unrecognized and degraded to a
        // facility-name search (matching only a facility literally named "Emergency…").
        SYNONYMS.put("emergency", List.of("EMERGENCY"));
        SYNONYMS.put("casualty", List.of("EMERGENCY"));
        SYNONYMS.put("accident and emergency", List.of("EMERGENCY"));
        SYNONYMS.put("a&e", List.of("EMERGENCY"));
        SYNONYMS.put("ambulance", List.of("EMERGENCY"));
        SYNONYMS.put("trauma", List.of("EMERGENCY"));

        for (List<String> tokens : SYNONYMS.values()) {
            KNOWN_TOKENS.addAll(tokens);
        }

        // Related-service families, derived from the curated synonym groups: two tokens are related
        // when they co-occur in any single synonym's token list (1-hop, non-transitive). This is what
        // lets a MATERNITY search also return ANTENATAL-coded clinics (and vice versa) instead of the
        // two capability codes siloing their result sets.
        Map<String, Set<String>> fam = new LinkedHashMap<>();
        for (List<String> group : SYNONYMS.values()) {
            for (String t : group) {
                fam.computeIfAbsent(t, k -> new LinkedHashSet<>()).addAll(group);
            }
        }
        for (Map.Entry<String, Set<String>> e : fam.entrySet()) {
            TOKEN_FAMILY.put(e.getKey(), List.copyOf(e.getValue()));
        }
    }

    /**
     * Expand a single capability token into its related-service family (the requested token first,
     * then any co-occurring tokens). An unknown token returns just itself. This is applied to the
     * explicit chip {@code service=} param so "Maternity" and "Antenatal care" stop returning
     * disjoint result sets. Never returns an empty list for a non-blank token.
     */
    public List<String> expand(String token) {
        if (token == null || token.isBlank()) {
            return List.of();
        }
        String norm = token.trim().toUpperCase();
        List<String> family = TOKEN_FAMILY.get(norm);
        if (family == null || family.isEmpty()) {
            return List.of(norm);
        }
        List<String> out = new ArrayList<>();
        out.add(norm);
        for (String t : family) {
            if (!out.contains(t)) {
                out.add(t);
            }
        }
        return List.copyOf(out);
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

        // Optional NL upgrade: only when the deterministic map found nothing, the flag is on, and a
        // provider is configured. The model's tokens are fenced to KNOWN_TOKENS so nothing new is
        // invented; any failure or an all-unknown result leaves the deterministic (empty) tokens.
        if (tokens.isEmpty() && llmInterpretEnabled) {
            List<String> llmTokens = interpretViaLlm(norm);
            if (!llmTokens.isEmpty()) {
                tokens = llmTokens;
            }
        }

        String primary = tokens.isEmpty() ? null : tokens.get(0);
        boolean recognized = !tokens.isEmpty();
        return new Interpretation(raw, List.copyOf(tokens), primary, telemed, emergency, accessibility, recognized);
    }

    /**
     * Route free text through the governed LLM structured-output endpoint and keep only tokens that
     * already exist in {@link #KNOWN_TOKENS}. Deterministic, honest fallback on every failure: an
     * empty list means "the model added nothing we can trust", and the caller uses the synonym map.
     */
    private List<String> interpretViaLlm(String norm) {
        try {
            Map<String, Object> schema = Map.of(
                    "type", "object",
                    "properties", Map.of(
                            "serviceTokens", Map.of(
                                    "type", "array",
                                    "items", Map.of("type", "string", "enum", new ArrayList<>(KNOWN_TOKENS)))),
                    "required", List.of("serviceTokens"));
            String system = "You map a person's free-text health need to canonical service capability "
                    + "tokens. Only use tokens from the provided enum. If nothing fits, return an empty "
                    + "array. Do not invent tokens.";
            JsonNode response = llmClient.structured("FIND_CARE_SERVICE_INTERPRET", system, norm, schema);
            if (response == null) {
                return List.of();
            }
            JsonNode tokensNode = findServiceTokens(response);
            if (tokensNode == null || !tokensNode.isArray()) {
                return List.of();
            }
            List<String> accepted = new ArrayList<>();
            for (JsonNode t : tokensNode) {
                String token = t.asText(null);
                if (token != null && KNOWN_TOKENS.contains(token) && !accepted.contains(token)) {
                    accepted.add(token);
                }
            }
            return accepted;
        } catch (Exception e) {
            log.warn("Find-care LLM interpret failed, using deterministic map: {}", e.getMessage());
            return List.of();
        }
    }

    /** Locate the {@code serviceTokens} array wherever the orchestrator envelope nests it. */
    private static JsonNode findServiceTokens(JsonNode root) {
        if (root.has("serviceTokens")) {
            return root.get("serviceTokens");
        }
        // Orchestrator responses vary by provider; probe the common structured-output carriers.
        for (String path : List.of("structuredOutput", "output", "data", "result", "content", "message")) {
            JsonNode child = root.get(path);
            if (child != null) {
                JsonNode found = findServiceTokens(child);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
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
