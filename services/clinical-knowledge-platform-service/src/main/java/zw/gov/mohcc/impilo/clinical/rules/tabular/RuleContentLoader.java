package zw.gov.mohcc.impilo.clinical.rules.tabular;

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
import java.util.Map;

/**
 * Loads clinical rule content from packaged content files.
 *
 * <p>Loading is fail-closed at the level of the individual rule: a rule that is malformed —
 * missing its code, its logic, its message or its required action — is dropped with a warning
 * rather than loaded in a degraded form. A half-understood clinical rule is worse than an
 * absent one, because an absent rule is visibly absent while a degraded rule looks like it is
 * working.</p>
 *
 * <p>Content lives in files rather than in migrations so that a change to a clinical threshold
 * is a reviewable diff a clinician can read, and so the same pack can be shipped to an offline
 * package unchanged.</p>
 */
@Component
public class RuleContentLoader {

    private static final Logger log = LoggerFactory.getLogger(RuleContentLoader.class);

    private final ObjectMapper objectMapper;
    private final Map<String, List<TabularRule>> packs = new LinkedHashMap<>();

    public RuleContentLoader(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Load a pack from the classpath, caching by resource path.
     *
     * @return the rules that loaded cleanly; never null, possibly empty
     */
    public List<TabularRule> load(String resourcePath) {
        return packs.computeIfAbsent(resourcePath, this::read);
    }

    private List<TabularRule> read(String resourcePath) {
        try (InputStream stream = RuleContentLoader.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (stream == null) {
                log.error("Clinical rule content not found on the classpath: {}", resourcePath);
                return List.of();
            }
            JsonNode root = objectMapper.readTree(stream);
            String contentVersion = root.path("version").asText("unknown");
            String packApproval = root.path("approvalStatus").asText("ENGINEERING_SEED");
            String authority = root.path("adaptationAuthority").asText(null);

            List<TabularRule> rules = new ArrayList<>();
            int rejected = 0;
            for (JsonNode node : root.path("rules")) {
                TabularRule rule = parse(node, contentVersion, packApproval, authority);
                if (rule == null) {
                    rejected++;
                    continue;
                }
                rules.add(rule);
            }

            log.info("Loaded clinical rule pack {} version={} approval={} rules={} rejected={}",
                    root.path("packId").asText(resourcePath), contentVersion, packApproval, rules.size(), rejected);
            if (rejected > 0) {
                log.warn("{} rule(s) in {} were rejected as malformed and are NOT active", rejected, resourcePath);
            }
            return List.copyOf(rules);

        } catch (IOException e) {
            log.error("Failed to read clinical rule content {}: {}", resourcePath, e.getMessage(), e);
            return List.of();
        }
    }

    private TabularRule parse(JsonNode node, String contentVersion, String packApproval, String authority) {
        String code = text(node, "code");
        JsonNode logic = node.get("logic");
        String message = text(node, "message");
        String requiredAction = text(node, "requiredAction");

        if (code == null || logic == null || logic.isNull() || message == null) {
            log.warn("Rejecting rule without a code, logic or message: {}", code == null ? "(no code)" : code);
            return null;
        }
        if (requiredAction == null) {
            // An alert that names a finding without naming the action is a notification, not
            // decision support, and would leave a clinician holding a problem and no next step.
            log.warn("Rejecting rule {}: no requiredAction, so it could not tell a clinician what to do", code);
            return null;
        }

        return new TabularRule(
                code,
                text(node, "name"),
                text(node, "layer"),
                text(node, "ruleType"),
                node.path("severity").asText("MEDIUM"),
                node.path("interruptive").asBoolean(false),
                node.path("overrideAllowed").asBoolean(true),
                node.path("referralRequired").asBoolean(false),
                integer(node, "ageMinDays"),
                integer(node, "ageMaxDays"),
                node.hasNonNull("appliesWhen") ? node.get("appliesWhen") : null,
                strings(node.path("requiredInputs")),
                logic,
                message,
                text(node, "explanation"),
                requiredAction,
                text(node, "monitoringInstruction"),
                strings(node.path("sourceRefs")),
                node.path("approvalStatus").asText(packApproval),
                node.path("adaptationAuthority").asText(authority),
                contentVersion,
                DakProvenance.from(node.get("dakRef"), node.get("adaptation")),
                testCases(node.path("testCases")),
                text(node, "signalFamily"),
                node.path("signalRank").asInt(0));
    }

    private List<TabularRule.TestCase> testCases(JsonNode node) {
        if (!node.isArray()) {
            return List.of();
        }
        List<TabularRule.TestCase> cases = new ArrayList<>();
        for (JsonNode entry : node) {
            Map<String, Object> facts = objectMapper.convertValue(entry.path("facts"), Map.class);
            cases.add(new TabularRule.TestCase(
                    entry.path("name").asText("unnamed"),
                    facts == null ? Map.of() : facts,
                    entry.path("expectFires").asBoolean(false),
                    entry.path("expectIncomplete").asBoolean(false)));
        }
        return List.copyOf(cases);
    }

    private static List<String> strings(JsonNode node) {
        if (!node.isArray()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        node.forEach(entry -> values.add(entry.asText()));
        return List.copyOf(values);
    }

    private static String text(JsonNode node, String field) {
        if (!node.hasNonNull(field)) {
            return null;
        }
        String value = node.get(field).asText();
        return value == null || value.isBlank() ? null : value;
    }

    private static Integer integer(JsonNode node, String field) {
        return node.hasNonNull(field) ? node.get(field).asInt() : null;
    }
}
