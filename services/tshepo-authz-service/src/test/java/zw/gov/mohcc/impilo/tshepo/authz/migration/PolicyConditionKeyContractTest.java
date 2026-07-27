package zw.gov.mohcc.impilo.tshepo.authz.migration;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every condition key a <em>live</em> policy rule uses must be a key {@code PolicyEngine} implements.
 *
 * <p><strong>Why this exists.</strong> {@code evaluateConditions} once tested the keys it knew —
 * {@code if (conditions.containsKey("path_contains")) …} — and then fell through to
 * {@code return true}. An <em>unrecognised</em> key was therefore silently ignored, not failed, so a
 * rule carrying one did not sit inert: it evaluated on whatever remained and fired more broadly than
 * it read. The classic shape:</p>
 *
 * <pre>
 *   DENY … '{"path_contains": "/regulatory/", "deny_operational_lanes": true}'
 * </pre>
 *
 * <p>reads as "deny the operational lanes under /regulatory/", but evaluated as "deny
 * <em>everything</em> under /regulatory/", because the qualifying key did not exist — and DENY wins,
 * so it could swallow the very ALLOW rules seeded beside it. The engine now treats an unknown key as
 * a non-match with a WARN, which closes that hole in the runtime; this guard is the second lock,
 * catching a bad key at build time so a seed cannot ship a rule whose stated intent the engine will
 * decline to honour.</p>
 *
 * <p><strong>It reads effective state, not file text.</strong> An applied migration can never be
 * edited (Flyway validates its checksum), so a rule authored with a bad key is repaired by a later
 * migration that {@code UPDATE}s its conditions — exactly what V053 did for the ROM shadow rows. A
 * guard that re-scanned raw {@code '{…}'} literals would go on reporting keys that were rewritten
 * migrations ago, which is why the previous version needed a hand-maintained {@code BASELINE} of
 * known offenders that could never quite reach zero and rotted the moment a key was implemented (as
 * {@code allowed_jurisdictions} was, in RB-2c, with nothing making anyone remove it). Reading the
 * rules as the database would end up holding them ({@link SeededPolicyRules}) removes the baseline
 * entirely: a repaired rule is clean, a withdrawn rule ({@code active=false}) claims nothing, and
 * only a genuinely unimplemented key on a genuinely live rule fails the build.</p>
 *
 * <p>There is deliberately <strong>no baseline and no allowlist</strong>. Implemented keys are read
 * from the service source, so implementing one in the engine widens what the guard accepts with no
 * second edit; repairing or withdrawing a rule in a migration narrows what it must accept with no
 * second edit. Nothing here is kept in sync by hand — which is the property a ratchet is supposed to
 * have and a static list never does.</p>
 */
class PolicyConditionKeyContractTest {

    private static final Path SOURCE = Path.of("src/main/java/zw/gov/mohcc/impilo/tshepo/authz");
    private static final Path MIGRATIONS = Path.of("src/main/resources/db/migration");

    /**
     * Any map lookup of a snake_case literal (or {@code visibility}) anywhere in the service counts
     * as "implemented".
     *
     * <p>Deliberately broader than {@code evaluateConditions}: a condition can legitimately be
     * consumed elsewhere. {@code visibility} is read at {@code PolicyEngine} via
     * {@code parseConditions(rule).get("visibility")} and again by
     * {@code VisibilityObligationComposer} — it drives an obligation rather than a match, and a
     * narrower pattern flagged it as unimplemented, which would have blocked another lane over a
     * rule that works. A guard that cries wolf gets muted, so it errs toward believing a key is
     * consumed.</p>
     */
    private static final Pattern IMPLEMENTED =
            Pattern.compile("\\.(?:containsKey|get)\\(\"([a-z][a-z0-9_]*_[a-z0-9_]*|visibility)\"\\)");

    /**
     * A top-level snake_case key in a conditions JSON object. Camel-cased keys inside the
     * {@code visibility} overlay ({@code confidentialCategories}, {@code drillDownAllowed}, …) do not
     * match, and correctly so — they are read from the overlay object, not looked up as condition
     * keys, and the overlay itself is reached through the implemented {@code visibility}.
     */
    private static final Pattern SEEDED_KEY = Pattern.compile("\"([a-z][a-z0-9_]*)\"\\s*:");

    @Test
    void everyConditionKeyOnALiveRuleIsOneTheEngineImplements() throws IOException {
        Set<String> implemented = implementedKeys();
        assertThat(implemented)
                .withFailMessage("no condition keys resolved from the service source — the pattern "
                                 + "has stopped working, so this guard would pass while checking nothing")
                .hasSizeGreaterThan(5);

        SeededPolicyRules.Seeds seeds = SeededPolicyRules.read();

        // If a migration seeds policy_rule but nothing parsed out of it, every seed guard is blind
        // to it — surface that rather than quietly checking fewer rules than exist.
        assertThat(seeds.complaints())
                .withFailMessage("the seed parser could not read part of the migration set, so this "
                                 + "guard is not seeing every rule:%n  %s",
                                 join(seeds.complaints().stream().map(Object::toString).toList()))
                .isEmpty();

        List<String> violations = new ArrayList<>();
        int liveRulesWithConditions = 0;

        for (SeededPolicyRules.Rule rule : seeds.live()) {
            String conditions = rule.conditions();
            if (conditions == null || conditions.isBlank()) {
                continue;
            }
            liveRulesWithConditions++;
            for (String key : keysIn(conditions)) {
                if (!implemented.contains(key)) {
                    violations.add(rule.location() + " -> '" + key + "' (rule '" + rule.name() + "')");
                }
            }
        }

        // A live rule with conditions is exactly what this guard checks; none means the seed set or
        // the parser drifted out from under it.
        assertThat(liveRulesWithConditions)
                .withFailMessage("no live policy rules carry conditions — the seed set changed shape "
                                 + "and this guard is checking nothing")
                .isGreaterThan(10);

        assertThat(new TreeSet<>(violations))
                .withFailMessage("""
                        %d live policy rule condition key(s) are not implemented in PolicyEngine.

                        A key the engine does not recognise is treated as a NON-MATCH (fail-closed for
                        an ALLOW, fail-narrow for a DENY) — so the rule does not do what its seed says.
                        Either implement the key in evaluateConditions (coordinate the PolicyEngine
                        slot; keep the recognised-key set exhaustive), or repair the rule in a later
                        migration so its conditions say what the engine can enforce, or withdraw it
                        (active=false) when the PDP structurally cannot express it.

                        %s""", violations.size(), join(new TreeSet<>(violations)))
                .isEmpty();
    }

    /**
     * The country root's limits must be real rules, not an absence of grant: an ALLOW added later by
     * someone who has not read org-registry V009 would out-rank an absence, but cannot out-rank a
     * DENY.
     */
    @Test
    void theCountryRootIsDeniedCaseDataAndGrantedNothing() throws IOException {
        String sql = Files.readString(MIGRATIONS.resolve(
                "V052__regulator_administration_roles_and_country_root_deny.sql"));

        assertThat(sql).contains("'NATIONAL_TRUST_ADMINISTRATOR'");
        assertThat(sql)
                .as("the deny must ship active — a protective rule that ships inactive is a "
                    + "protection nobody has")
                .contains("'DENY', 10,");

        int allowForRoot = sql.split("NATIONAL_TRUST_ADMINISTRATOR', NULL, NULL, NULL, false, false, 'ALLOW'", -1).length - 1;
        assertThat(allowForRoot)
                .as("the country root is a trust function, not a superuser — it is granted nothing here")
                .isZero();
    }

    private static Set<String> implementedKeys() throws IOException {
        Set<String> implemented = new TreeSet<>();
        try (Stream<Path> sources = Files.walk(SOURCE)) {
            for (Path java : sources.filter(p -> p.toString().endsWith(".java")).toList()) {
                Matcher m = IMPLEMENTED.matcher(Files.readString(java));
                while (m.find()) {
                    implemented.add(m.group(1));
                }
            }
        }
        return implemented;
    }

    private static Set<String> keysIn(String conditionsJson) {
        Set<String> keys = new LinkedHashSet<>();
        Matcher m = SEEDED_KEY.matcher(conditionsJson);
        while (m.find()) {
            keys.add(m.group(1));
        }
        return keys;
    }

    private static String join(Iterable<String> lines) {
        return String.join("\n  ", lines);
    }
}
