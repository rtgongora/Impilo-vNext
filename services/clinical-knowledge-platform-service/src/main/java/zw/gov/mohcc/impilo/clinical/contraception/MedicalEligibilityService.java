package zw.gov.mohcc.impilo.clinical.contraception;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Loads the medical eligibility matrix and runs {@link MedicalEligibilityEngine} against it.
 *
 * <p>The engine deliberately holds no content, so this is the only class that knows where the
 * criteria live. That separation is what lets a ministry replace the matrix without a code change,
 * and it is why the WHO licence question never reaches compiled code.
 *
 * <p>Loading is fail-closed in the direction that matters. A cell whose category is outside 1–4, or
 * a condition with no logic, is dropped rather than coerced — and because the engine treats a
 * missing cell as unclassified rather than as category 1, a dropped cell degrades to
 * {@code CANNOT_DETERMINE} rather than to a green light.
 */
@Service
public class MedicalEligibilityService {

    private static final Logger log = LoggerFactory.getLogger(MedicalEligibilityService.class);

    static final String CONTENT_PATH = "clinical/rmnp-mec-matrix.json";

    private final ObjectMapper objectMapper;
    private final Map<String, MecMatrix> cache = new LinkedHashMap<>();

    public MedicalEligibilityService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Assess every method in the matrix against the recorded history.
     *
     * @param usePhase     starting the method or already using it; the categories differ
     * @param methodFilter method codes to assess, or null/empty for all. Assessing all is the
     *                     default on purpose: a woman is entitled to know which methods she may use,
     *                     not only whether the one she named is refused.
     */
    public MedicalEligibilityEngine.Assessment assess(
            MedicalEligibilityEngine.UsePhase usePhase, Map<String, Object> facts,
            List<String> methodFilter) {
        return MedicalEligibilityEngine.assess(matrix(), usePhase, facts, methodFilter);
    }

    /** The loaded matrix, or null when the content is unavailable — which the engine reports. */
    public MecMatrix matrix() {
        return matrix(CONTENT_PATH);
    }

    MecMatrix matrix(String resourcePath) {
        return cache.computeIfAbsent(resourcePath, this::read);
    }

    private MecMatrix read(String resourcePath) {
        try (InputStream stream = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
            if (stream == null) {
                log.error("Medical eligibility matrix not found on the classpath: {}", resourcePath);
                return null;
            }
            JsonNode root = objectMapper.readTree(stream);

            List<MecMatrix.Method> methods = new ArrayList<>();
            for (JsonNode node : root.path("methods")) {
                methods.add(new MecMatrix.Method(
                        node.path("code").asText(),
                        node.path("name").asText(),
                        node.path("methodClass").asText(null)));
            }

            List<MecMatrix.Condition> conditions = new ArrayList<>();
            for (JsonNode node : root.path("conditions")) {
                String code = node.path("code").asText(null);
                JsonNode logic = node.get("logic");
                if (code == null || logic == null || logic.isNull()) {
                    log.warn("Rejecting eligibility condition without a code or logic: {}", code);
                    continue;
                }
                conditions.add(new MecMatrix.Condition(code, node.path("name").asText(code), logic,
                        strings(node.path("requiredInputs")), strings(node.path("sourceRefs"))));
            }

            List<MecMatrix.Cell> cells = new ArrayList<>();
            for (JsonNode row : root.path("cells")) {
                String condition = row.path("condition").asText(null);
                if (condition == null) {
                    log.warn("Rejecting an eligibility row that names no condition");
                    continue;
                }
                String clarification = row.path("clarification").asText(null);
                List<String> sourceRefs = strings(row.path("sourceRefs"));
                JsonNode categories = row.path("categories");
                categories.fieldNames().forEachRemaining(methodCode -> {
                    JsonNode cell = categories.get(methodCode);
                    int initiation = cell.path("i").asInt(0);
                    int continuation = cell.path("c").asInt(0);
                    if (!valid(initiation) || !valid(continuation)) {
                        // Dropped, not coerced. The engine reports a missing cell as unclassified,
                        // so this degrades to "cannot determine" and never to "no restriction".
                        log.warn("Rejecting eligibility cell {} x {}: categories {}/{} are not 1-4",
                                condition, methodCode, initiation, continuation);
                        return;
                    }
                    cells.add(new MecMatrix.Cell(condition, methodCode, initiation, continuation,
                            clarification, sourceRefs));
                });
            }

            MecMatrix matrix = MecMatrix.of(
                    root.path("packId").asText(resourcePath),
                    root.path("version").asText("unknown"),
                    root.path("approvalStatus").asText("ENGINEERING_SEED"),
                    root.path("adaptationAuthority").asText(null),
                    root.path("provenance").path("primarySource").asText(null),
                    root.path("coverageNote").asText(null),
                    methods, conditions, strings(root.path("conditionsOutOfScope")), cells);

            log.info("Loaded medical eligibility matrix {} version={} approval={} "
                            + "methods={} conditions={} cells={} outOfScope={}",
                    matrix.packId(), matrix.version(), matrix.approvalStatus(),
                    methods.size(), conditions.size(), cells.size(),
                    matrix.conditionsOutOfScope().size());
            return matrix;

        } catch (IOException e) {
            log.error("Failed to read the medical eligibility matrix {}: {}", resourcePath,
                    e.getMessage(), e);
            return null;
        }
    }

    private static boolean valid(int category) {
        return category >= 1 && category <= 4;
    }

    private static List<String> strings(JsonNode node) {
        if (!node.isArray()) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        node.forEach(item -> out.add(item.asText()));
        return List.copyOf(out);
    }
}
