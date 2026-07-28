package zw.gov.mohcc.impilo.clinical.multimorbidity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Resolves a medicine — an ATC code, a local code or a generic name — to the therapeutic classes
 * that the multimorbidity safety detections reason over.
 *
 * <p><strong>Why this exists.</strong> Detecting duplicate therapy requires knowing that lisinopril
 * and enalapril are the same kind of thing. Nothing in the estate could state that. The deprescribing
 * pack worked around it by asking the caller for boolean flags, and the only producer of those flags
 * was a clinician ticking a dropdown — so the rule fired on a clinician's assertion that duplicate
 * therapy existed, not on a detection of it.</p>
 *
 * <p><strong>Not a parallel registry.</strong> zibo owns the national medicine registry over the WHO
 * ATC CodeSystem and remains the system of record for what a medicine is. This class owns only the
 * clinical grouping decision support reasons over, and keys it on ATC prefixes so the two stay
 * joined rather than diverging.</p>
 *
 * <p><strong>Unclassifiable is reported, never absorbed.</strong> Codes arriving here are
 * heterogeneous — {@code OrosMedicationIntegration} forwards whichever of {@code medication_code},
 * {@code atc_code} or {@code code} a prescription row happens to carry — so a medicine that matches
 * neither an ATC prefix nor a known generic name cannot be classified. It is returned in
 * {@link Classification#unclassified()} so that a caller can refuse to assert a negative over a list
 * it did not fully understand. Silently dropping it is how a renal-safety check comes to clear a
 * patient whose NSAID it never recognised.</p>
 */
@Component
public class MedicineClassifier {

    public static final String CONTENT_PATH = "clinical/medicine-classification.json";

    private static final Logger log = LoggerFactory.getLogger(MedicineClassifier.class);

    private final ObjectMapper objectMapper;

    private volatile Content content;

    public MedicineClassifier(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** One therapeutic class as governed content describes it. */
    public record TherapeuticClass(
            String className,
            String display,
            List<String> atcPrefixes,
            List<String> generics,
            boolean renallyCleared,
            boolean nephrotoxic,
            boolean duplicateWithin,
            String duplicateNote) {
    }

    /** A combination the content declares unsafe. */
    public record UnsafeCombination(
            String code,
            List<String> classes,
            List<List<String>> alsoSatisfiedBy,
            Integer withinClassCount,
            String severity,
            String message,
            String explanation,
            String requiredAction) {
    }

    /**
     * The result of classifying a medicine list.
     *
     * @param byClass       class name to the medicines found in it, in list order
     * @param unclassified  the medicines that matched no class
     * @param contentLoaded false when the classification content could not be read, which must not
     *                      be reported as "no medicines were classifiable"
     */
    public record Classification(
            Map<String, List<String>> byClass,
            List<String> unclassified,
            boolean contentLoaded) {

        /** True when every supplied medicine was recognised — the precondition for asserting a negative. */
        public boolean complete() {
            return contentLoaded && unclassified.isEmpty();
        }

        public boolean has(String className) {
            return byClass.containsKey(className);
        }

        public int countIn(String className) {
            return byClass.getOrDefault(className, List.of()).size();
        }
    }

    private record Content(List<TherapeuticClass> classes, List<UnsafeCombination> combinations) {
    }

    public List<TherapeuticClass> classes() {
        return content().classes();
    }

    public List<UnsafeCombination> unsafeCombinations() {
        return content().combinations();
    }

    public boolean contentLoaded() {
        return !content().classes().isEmpty();
    }

    /**
     * Classify a list of medicine codes or names.
     *
     * @param medicines codes and/or generic names; {@code null} means the list was never obtained,
     *                  which is different from an empty list and is preserved as such by the caller
     */
    public Classification classify(List<String> medicines) {
        Content c = content();
        if (c.classes().isEmpty()) {
            // Content missing is a system failure, not a clinical finding. Report every medicine as
            // unclassified so no caller can read this as a clean list.
            return new Classification(Map.of(),
                    medicines == null ? List.of() : List.copyOf(medicines), false);
        }
        Map<String, List<String>> byClass = new LinkedHashMap<>();
        List<String> unclassified = new ArrayList<>();
        if (medicines != null) {
            for (String medicine : medicines) {
                if (medicine == null || medicine.isBlank()) {
                    continue;
                }
                Set<String> matched = classesOf(medicine, c);
                if (matched.isEmpty()) {
                    unclassified.add(medicine);
                    continue;
                }
                for (String className : matched) {
                    byClass.computeIfAbsent(className, k -> new ArrayList<>()).add(medicine);
                }
            }
        }
        return new Classification(byClass, unclassified, true);
    }

    /** Every class a single medicine belongs to (usually one; combination products may match more). */
    private Set<String> classesOf(String medicine, Content c) {
        String trimmed = medicine.trim();
        String upper = trimmed.toUpperCase(Locale.ROOT);
        String lower = trimmed.toLowerCase(Locale.ROOT);
        Set<String> matched = new LinkedHashSet<>();
        for (TherapeuticClass tc : c.classes()) {
            // ATC prefix first — the code is the governed identity, the name is the fallback.
            for (String prefix : tc.atcPrefixes()) {
                if (!prefix.isBlank() && upper.startsWith(prefix)) {
                    matched.add(tc.className());
                    break;
                }
            }
            if (matched.contains(tc.className())) {
                continue;
            }
            for (String generic : tc.generics()) {
                if (!generic.isBlank() && lower.contains(generic)) {
                    matched.add(tc.className());
                    break;
                }
            }
        }
        return matched;
    }

    private Content content() {
        Content local = content;
        if (local == null) {
            synchronized (this) {
                if (content == null) {
                    content = read();
                }
                local = content;
            }
        }
        return local;
    }

    private Content read() {
        try (InputStream stream = MedicineClassifier.class.getClassLoader().getResourceAsStream(CONTENT_PATH)) {
            if (stream == null) {
                log.error("Medicine classification content not found on the classpath: {}. Every medicine "
                          + "will be reported unclassifiable, so no negative safety assertion can be made.",
                        CONTENT_PATH);
                return new Content(List.of(), List.of());
            }
            JsonNode root = objectMapper.readTree(stream);
            List<TherapeuticClass> classes = new ArrayList<>();
            for (JsonNode node : root.path("classes")) {
                String className = node.path("class").asText("");
                if (className.isBlank()) {
                    log.warn("Medicine classification entry without a class name — dropped");
                    continue;
                }
                classes.add(new TherapeuticClass(
                        className,
                        node.path("display").asText(className),
                        strings(node.path("atcPrefixes")),
                        lowerStrings(node.path("generics")),
                        node.path("renallyCleared").asBoolean(false),
                        node.path("nephrotoxic").asBoolean(false),
                        node.path("duplicateWithin").asBoolean(true),
                        node.path("duplicateNote").asText(null)));
            }
            List<UnsafeCombination> combinations = new ArrayList<>();
            for (JsonNode node : root.path("unsafeCombinations")) {
                String code = node.path("code").asText("");
                String message = node.path("message").asText("");
                if (code.isBlank() || message.isBlank()) {
                    log.warn("Unsafe-combination entry without a code or message — dropped");
                    continue;
                }
                List<List<String>> also = new ArrayList<>();
                for (JsonNode alt : node.path("alsoSatisfiedBy")) {
                    also.add(strings(alt));
                }
                combinations.add(new UnsafeCombination(
                        code,
                        strings(node.path("classes")),
                        also,
                        node.hasNonNull("withinClassCount") ? node.path("withinClassCount").asInt() : null,
                        node.path("severity").asText("MODERATE"),
                        message,
                        node.path("explanation").asText(""),
                        node.path("requiredAction").asText("")));
            }
            log.info("medicine.classification.loaded classes={} combinations={}", classes.size(), combinations.size());
            return new Content(List.copyOf(classes), List.copyOf(combinations));
        } catch (IOException e) {
            log.error("Medicine classification content could not be read: {}", e.getMessage());
            return new Content(List.of(), List.of());
        }
    }

    private static List<String> strings(JsonNode array) {
        List<String> out = new ArrayList<>();
        for (JsonNode n : array) {
            String v = n.asText("").trim();
            if (!v.isBlank()) {
                out.add(v);
            }
        }
        return List.copyOf(out);
    }

    private static List<String> lowerStrings(JsonNode array) {
        return strings(array).stream().map(s -> s.toLowerCase(Locale.ROOT)).toList();
    }
}
