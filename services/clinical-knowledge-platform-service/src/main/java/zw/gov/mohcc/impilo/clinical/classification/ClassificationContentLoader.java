package zw.gov.mohcc.impilo.clinical.classification;

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
 * Loads IMNCI classification tables from versioned classpath content.
 *
 * <p>Follows the same discipline as {@code RuleContentLoader}: content lives in files so a change
 * to a clinical threshold is a diff a clinician can read, and a malformed table is rejected rather
 * than half-loaded. A table missing its rows, or whose rows omit the disposition, is dropped with
 * a loud log — a classification table that silently loses its most severe row would be worse than
 * one that fails to load at all.</p>
 */
@Component
public class ClassificationContentLoader {

    private static final Logger log = LoggerFactory.getLogger(ClassificationContentLoader.class);

    private final ObjectMapper objectMapper;
    private final Map<String, Pack> packs = new LinkedHashMap<>();

    public ClassificationContentLoader(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** A loaded pack, carrying the provenance every classification must be able to cite. */
    public record Pack(
            String packId,
            String version,
            String approvalStatus,
            String adaptationAuthority,
            String provenance,
            List<ClassificationTable> tables) {

        public ClassificationTable table(String tableId) {
            return tables.stream().filter(t -> t.tableId().equals(tableId)).findFirst().orElse(null);
        }
    }

    public Pack load(String resourcePath) {
        return packs.computeIfAbsent(resourcePath, this::read);
    }

    private Pack read(String resourcePath) {
        try (InputStream stream = ClassificationContentLoader.class.getClassLoader()
                .getResourceAsStream(resourcePath)) {
            if (stream == null) {
                log.error("IMNCI classification content not found on the classpath: {}", resourcePath);
                return emptyPack(resourcePath);
            }
            JsonNode root = objectMapper.readTree(stream);

            List<ClassificationTable> tables = new ArrayList<>();
            int rejected = 0;
            for (JsonNode node : root.path("tables")) {
                ClassificationTable table = parseTable(node);
                if (table == null) {
                    rejected++;
                    continue;
                }
                tables.add(table);
            }

            Pack pack = new Pack(
                    root.path("packId").asText(resourcePath),
                    root.path("version").asText("unknown"),
                    root.path("approvalStatus").asText("ENGINEERING_SEED"),
                    root.path("adaptationAuthority").asText(null),
                    root.path("provenance").asText(null),
                    List.copyOf(tables));

            log.info("Loaded IMNCI classification pack {} version={} approval={} tables={} rejected={}",
                    pack.packId(), pack.version(), pack.approvalStatus(), tables.size(), rejected);
            if (rejected > 0) {
                log.error("{} classification table(s) in {} were rejected as malformed and are NOT active",
                        rejected, resourcePath);
            }
            return pack;

        } catch (IOException e) {
            log.error("Failed to read IMNCI classification content {}: {}", resourcePath, e.getMessage(), e);
            return emptyPack(resourcePath);
        }
    }

    private ClassificationTable parseTable(JsonNode node) {
        String tableId = text(node, "tableId");
        if (tableId == null) {
            log.error("Classification table without a tableId rejected");
            return null;
        }
        List<ClassificationTable.Tier> tiers = new ArrayList<>();
        for (JsonNode tierNode : node.path("tiers")) {
            ClassificationTable.Tier tier = parseTier(tableId, tierNode);
            if (tier == null) {
                log.error("Classification table {} rejected: a row is malformed, and a table missing a "
                          + "severity row would classify children too leniently", tableId);
                return null;
            }
            tiers.add(tier);
        }
        if (tiers.isEmpty()) {
            log.error("Classification table {} rejected: no rows", tableId);
            return null;
        }

        return new ClassificationTable(
                tableId,
                text(node, "name"),
                text(node, "axis"),
                intOrNull(node, "ageMinDays"),
                intOrNull(node, "ageMaxDays"),
                node.get("appliesWhen"),
                stringList(node.path("requiredInputs")),
                List.copyOf(tiers),
                stringList(node.path("sourceRefs")),
                parseTestCases(node.path("testCases")));
    }

    private ClassificationTable.Tier parseTier(String tableId, JsonNode node) {
        String code = text(node, "code");
        String colour = text(node, "colour");
        String action = text(node, "action");
        if (code == null || colour == null || action == null) {
            return null;
        }
        // Every row must state a disposition. A classification without an action is a label, and a
        // label is not something a health worker at a rural clinic can act on.
        return new ClassificationTable.Tier(
                code,
                text(node, "name"),
                colour,
                node.get("logic"),
                action,
                stringList(node.path("treatments")),
                node.path("referralRequired").asBoolean(false),
                node.path("urgentReferral").asBoolean(false));
    }

    private List<ClassificationTable.TestCase> parseTestCases(JsonNode node) {
        List<ClassificationTable.TestCase> cases = new ArrayList<>();
        for (JsonNode c : node) {
            Map<String, Object> facts = objectMapper.convertValue(
                    c.path("facts"), objectMapper.getTypeFactory()
                            .constructMapType(LinkedHashMap.class, String.class, Object.class));
            cases.add(new ClassificationTable.TestCase(
                    c.path("name").asText("unnamed"),
                    facts == null ? Map.of() : facts,
                    c.path("expectStatus").asText("CLASSIFIED"),
                    c.hasNonNull("expectClassification") ? c.get("expectClassification").asText() : null,
                    c.path("expectProvisional").asBoolean(false)));
        }
        return List.copyOf(cases);
    }

    private static Pack emptyPack(String resourcePath) {
        return new Pack(resourcePath, "unavailable", "ENGINEERING_SEED", null, null, List.of());
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() || value.asText().isBlank() ? null : value.asText();
    }

    private static Integer intOrNull(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asInt();
    }

    private static List<String> stringList(JsonNode node) {
        List<String> out = new ArrayList<>();
        node.forEach(n -> out.add(n.asText()));
        return List.copyOf(out);
    }
}
