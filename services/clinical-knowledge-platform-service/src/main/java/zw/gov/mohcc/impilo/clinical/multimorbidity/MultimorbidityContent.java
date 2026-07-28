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
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The governed thresholds, repeat intervals and condition-level conflicts the multimorbidity engine
 * reasons with.
 *
 * <p>Thresholds live in content rather than in Java because "how many medicines is too many" and
 * "how many clinic visits in ninety days is too many" are national policy questions. What counts as
 * an excessive visit schedule for a patient who walks fifteen kilometres to the clinic is not an
 * engineering decision, and burying it in a constant would make it one.</p>
 */
@Component
public class MultimorbidityContent {

    public static final String CONTENT_PATH = "clinical/multimorbidity-rules.json";

    private static final Logger log = LoggerFactory.getLogger(MultimorbidityContent.class);

    private final ObjectMapper objectMapper;
    private volatile Loaded loaded;

    public MultimorbidityContent(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** A conflict between two conditions, expressed as two sets of code or display fragments. */
    public record ConditionConflict(
            String code,
            String kind,
            List<String> conditionsA,
            List<String> conditionsB,
            String severity,
            String message,
            String explanation,
            String requiredAction) {
    }

    /** The shortest interval after which repeating an investigation can tell you something new. */
    public record RepeatInterval(String investigation, int minimumRepeatDays) {
    }

    private record Loaded(Map<String, Integer> thresholds, List<RepeatInterval> repeatIntervals,
                          List<ConditionConflict> conflicts, String version, String approvalStatus) {
    }

    public int threshold(String key, int fallback) {
        Integer v = load().thresholds().get(key);
        return v == null ? fallback : v;
    }

    public List<RepeatInterval> repeatIntervals() {
        return load().repeatIntervals();
    }

    public List<ConditionConflict> conflicts() {
        return load().conflicts();
    }

    public String version() {
        return load().version();
    }

    public String approvalStatus() {
        return load().approvalStatus();
    }

    /** False when the pack could not be read — the engine must then decline rather than report clear. */
    public boolean isLoaded() {
        return !load().conflicts().isEmpty() || !load().thresholds().isEmpty();
    }

    private Loaded load() {
        Loaded local = loaded;
        if (local == null) {
            synchronized (this) {
                if (loaded == null) {
                    loaded = read();
                }
                local = loaded;
            }
        }
        return local;
    }

    private Loaded read() {
        try (InputStream stream = MultimorbidityContent.class.getClassLoader().getResourceAsStream(CONTENT_PATH)) {
            if (stream == null) {
                log.error("Multimorbidity content not found on the classpath: {}. Every detection will "
                          + "report UNDETERMINED rather than clear.", CONTENT_PATH);
                return new Loaded(Map.of(), List.of(), List.of(), "unavailable", "UNAVAILABLE");
            }
            JsonNode root = objectMapper.readTree(stream);

            Map<String, Integer> thresholds = new LinkedHashMap<>();
            JsonNode t = root.path("thresholds");
            t.fieldNames().forEachRemaining(name -> {
                JsonNode v = t.path(name).path("value");
                if (v.isInt()) {
                    thresholds.put(name, v.asInt());
                }
            });

            List<RepeatInterval> intervals = new ArrayList<>();
            for (JsonNode n : root.path("repeatIntervals")) {
                String investigation = n.path("investigation").asText("").trim();
                int days = n.path("minimumRepeatDays").asInt(-1);
                if (investigation.isEmpty() || days < 0) {
                    log.warn("Repeat-interval entry without an investigation or interval — dropped");
                    continue;
                }
                intervals.add(new RepeatInterval(investigation.toUpperCase(Locale.ROOT), days));
            }

            List<ConditionConflict> conflicts = new ArrayList<>();
            for (JsonNode n : root.path("conditionConflicts")) {
                String code = n.path("code").asText("").trim();
                String message = n.path("message").asText("").trim();
                String action = n.path("requiredAction").asText("").trim();
                // Fail-closed per entry, matching RuleContentLoader: a conflict that names a problem
                // with no next step is a notification, not decision support.
                if (code.isEmpty() || message.isEmpty() || action.isEmpty()) {
                    log.warn("Condition-conflict entry without a code, message or required action — dropped");
                    continue;
                }
                conflicts.add(new ConditionConflict(
                        code,
                        n.path("kind").asText("CONTRADICTORY_TARGETS"),
                        upper(n.path("conditionsA")),
                        upper(n.path("conditionsB")),
                        n.path("severity").asText("MODERATE"),
                        message,
                        n.path("explanation").asText(""),
                        action));
            }

            log.info("multimorbidity.content.loaded thresholds={} intervals={} conflicts={}",
                    thresholds.size(), intervals.size(), conflicts.size());
            return new Loaded(Map.copyOf(thresholds), List.copyOf(intervals), List.copyOf(conflicts),
                    root.path("version").asText("unknown"),
                    root.path("approvalStatus").asText("ENGINEERING_SEED"));
        } catch (IOException e) {
            log.error("Multimorbidity content could not be read: {}", e.getMessage());
            return new Loaded(Map.of(), List.of(), List.of(), "unavailable", "UNAVAILABLE");
        }
    }

    private static List<String> upper(JsonNode array) {
        List<String> out = new ArrayList<>();
        for (JsonNode n : array) {
            String v = n.asText("").trim().toUpperCase(Locale.ROOT);
            if (!v.isEmpty()) {
                out.add(v);
            }
        }
        return List.copyOf(out);
    }
}
