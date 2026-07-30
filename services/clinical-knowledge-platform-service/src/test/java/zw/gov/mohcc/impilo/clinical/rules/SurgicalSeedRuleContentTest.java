package zw.gov.mohcc.impilo.clinical.rules;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDate;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The 147 surgical CDS rules loaded by {@code V300__surgical_cds_rules.sql} are engineering-authored
 * and NOT ratified by MoHCC. They must remain inspectable content that cannot drive care until a
 * clinician says otherwise, and this test is what stops that quietly changing.
 *
 * <p>It checks the two committed artifacts against each other — the authored fragments under
 * {@code docs/clinical/surgical-domain-pack/rule-fragments/} and the migration that loads them — and
 * asserts the four independent guarantees the migration header claims:
 *
 * <ol>
 *   <li>no evaluable logic: {@code logic_json} and {@code logic_expression} are NULL on every row;</li>
 *   <li>not in force: {@code effective_start} is beyond any operational horizon, so
 *       {@link zw.gov.mohcc.impilo.clinical.persistence.entity.RuleDefinitionEntity#activeOn} is false;</li>
 *   <li>unratified: {@code approval_status} is ENGINEERING_SEED with the pending authority named;</li>
 *   <li>harmless if surfaced: non-interruptive and always overridable.</li>
 * </ol>
 *
 * <p>It also checks the load is faithful — every authored rule present exactly once, no invented
 * ones — and that the codes are namespaced, which is what keeps a globally UNIQUE {@code code}
 * column from colliding with another lane's rules.
 */
class SurgicalSeedRuleContentTest {

    private static final int EXPECTED_RULES = 147;
    private static final int EXPECTED_SPECIALTY_FILES = 15;
    private static final Set<String> LAYERS = Set.of(
            "DANGER_SIGN", "THERAPY", "MONITORING", "FOLLOW_UP", "CLASSIFICATION", "DATA_VALIDATION");

    private static Path repoRoot;
    private static Path fragmentDir;
    private static String migration;
    private static List<String> valueRows;

    @BeforeAll
    static void locateArtifacts() throws IOException {
        Path p = Paths.get("").toAbsolutePath();
        while (p != null && !Files.isDirectory(p.resolve("docs/clinical/surgical-domain-pack/rule-fragments"))) {
            p = p.getParent();
        }
        assertNotNull(p, "could not locate the repository root from " + Paths.get("").toAbsolutePath()
                + " — a test that cannot find the tree it audits must fail, not pass vacuously");
        repoRoot = p;
        fragmentDir = repoRoot.resolve("docs/clinical/surgical-domain-pack/rule-fragments");

        Path sql = repoRoot.resolve(
                "services/clinical-knowledge-platform-service/src/main/resources/db/migration/V300__surgical_cds_rules.sql");
        assertTrue(Files.isRegularFile(sql), "V300__surgical_cds_rules.sql must exist at " + sql);
        migration = Files.readString(sql);

        valueRows = migration.lines()
                .filter(l -> l.startsWith("('SURG_"))
                .collect(Collectors.toList());
        assertFalse(valueRows.isEmpty(), "the migration matched no rule rows — the parser, not the content, is wrong");
    }

    /** The authored keys, read from the fragments themselves rather than from a remembered list. */
    private static List<String> authoredRuleKeys() throws IOException {
        List<String> keys = new ArrayList<>();
        try (DirectoryStream<Path> files = Files.newDirectoryStream(fragmentDir, "*.jsonl")) {
            for (Path f : files) {
                for (String line : Files.readAllLines(f)) {
                    if (line.isBlank() || line.contains("\"_meta\"")) {
                        continue;
                    }
                    Matcher m = Pattern.compile("\"rule_key\"\\s*:\\s*\"([^\"]+)\"").matcher(line);
                    assertTrue(m.find(), "every non-meta fragment line must carry a rule_key: " + f.getFileName());
                    keys.add(m.group(1));
                }
            }
        }
        return keys;
    }

    @Test
    @DisplayName("the load is faithful: every authored rule appears exactly once, and nothing else does")
    void migrationLoadsExactlyTheAuthoredRules() throws IOException {
        List<String> authored = authoredRuleKeys();
        assertEquals(EXPECTED_RULES, authored.size(),
                "the fragments should carry " + EXPECTED_RULES + " rules (162 raw lines less 15 _meta headers)");

        try (DirectoryStream<Path> files = Files.newDirectoryStream(fragmentDir, "*.jsonl")) {
            long count = 0;
            for (Path ignored : files) {
                count++;
            }
            assertEquals(EXPECTED_SPECIALTY_FILES, count, "one fragment file per surgical specialty");
        }

        List<String> loaded = valueRows.stream()
                .map(r -> r.substring(2, r.indexOf('\'', 2)))
                .collect(Collectors.toList());

        assertEquals(EXPECTED_RULES, loaded.size(), "the migration must load every authored rule");
        assertEquals(new HashSet<>(loaded).size(), loaded.size(), "a rule must not be loaded twice");

        Set<String> expected = authored.stream().map(k -> k.replace('-', '_')).collect(Collectors.toSet());
        assertEquals(expected, new HashSet<>(loaded),
                "the migration must load exactly the authored rules — no drops, no inventions");
    }

    @Test
    @DisplayName("codes are namespaced, because clinical.rule_definitions.code is globally UNIQUE")
    void codesAreNamespaced() {
        for (String row : valueRows) {
            String code = row.substring(2, row.indexOf('\'', 2));
            assertTrue(code.startsWith("SURG_"),
                    "an un-namespaced code would collide with another lane's rules: " + code);
        }
    }

    @Test
    @DisplayName("guarantees 1-4 — every loaded row is non-executable, not in force, unratified and harmless")
    void everyRowIsInertByConstruction() {
        LocalDate today = LocalDate.now();
        for (String row : valueRows) {
            String code = row.substring(2, row.indexOf('\'', 2));

            assertTrue(row.contains(", NULL, NULL, "),
                    code + ": logic_expression and logic_json must both be NULL — the authored logic is "
                            + "English prose, and a rule whose condition is a sentence cannot be evaluated");

            Matcher start = Pattern.compile("DATE '(\\d{4})-(\\d{2})-(\\d{2})'").matcher(row);
            assertTrue(start.find(), code + ": must carry an explicit effective_start");
            LocalDate effectiveStart = LocalDate.of(
                    Integer.parseInt(start.group(1)), Integer.parseInt(start.group(2)), Integer.parseInt(start.group(3)));
            assertTrue(effectiveStart.isAfter(today.plusYears(100)),
                    code + ": effective_start must lie beyond any operational horizon so activeOn() is false. "
                            + "Ratification replaces it with a real date; nothing else should.");

            assertTrue(row.contains("'ENGINEERING_SEED'"),
                    code + ": engineering-authored content must say so in approval_status");
            assertTrue(row.contains("'PENDING_MOHCC_RATIFICATION'"),
                    code + ": the pending approving authority must be named");
            assertTrue(row.contains("\"severity_origin\":\"LAYER_DEFAULT_NOT_AUTHORED\""),
                    code + ": severity is a layer default, not a clinical judgement the authors supplied, "
                            + "and ratification needs to be able to see that");

            assertTrue(row.contains(", false, true, DATE "),
                    code + ": must be non-interruptive and overridable, so that even if some future engine "
                            + "emitted this code it could not block care");
        }
    }

    @Test
    @DisplayName("every rule belongs to one of the six documented decision-support layers")
    void everyRuleCarriesAKnownLayer() {
        for (String row : valueRows) {
            String code = row.substring(2, row.indexOf('\'', 2));
            assertTrue(LAYERS.stream().anyMatch(l -> row.contains("'" + l + "'")),
                    code + ": must carry one of " + LAYERS);
        }
    }
}
