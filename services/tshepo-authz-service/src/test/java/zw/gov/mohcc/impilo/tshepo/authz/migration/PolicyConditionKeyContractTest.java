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
 * Every condition key a seeded policy rule uses must be a key {@code PolicyEngine} implements.
 *
 * <p><strong>Why this exists.</strong> {@code evaluateConditions} tests the keys it knows —
 * {@code if (conditions.containsKey("path_contains")) …} — and then falls through to
 * {@code return true}. An <em>unrecognised</em> key is therefore silently ignored, not failed.
 * That has a consequence nobody would guess from reading a seed migration:</p>
 *
 * <pre>
 *   DENY … '{"path_contains": "/regulatory/", "deny_operational_lanes": true}'
 * </pre>
 *
 * <p>reads as "deny the operational lanes under /regulatory/", but evaluates as "deny
 * <em>everything</em> under /regulatory/", because the qualifying key does not exist. On an ALLOW
 * the same mistake grants more than intended; on a DENY it denies more than intended — and DENY
 * wins, so it can swallow the very ALLOW rules seeded beside it.</p>
 *
 * <p>A rule carrying an unimplemented key is not inert. It is <em>differently active</em>, which is
 * worse, because the seed file documents an intention the engine never enforces.</p>
 *
 * <p><strong>It ratchets</strong>, like the BFF's downstream-route guard: known offenders live in
 * {@link #BASELINE} and are reported but do not fail; a new one fails the build. Fix a baseline
 * entry — by implementing the key or by rewriting the rule — and delete its line. The list may only
 * shrink.</p>
 */
class PolicyConditionKeyContractTest {

    private static final Path MIGRATIONS = Path.of("src/main/resources/db/migration");
    private static final Path SOURCE = Path.of("src/main/java/zw/gov/mohcc/impilo/tshepo/authz");

    /**
     * Any map lookup of a snake_case literal anywhere in the service counts as "implemented".
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

    /** Keys appearing in a seeded conditions JSON literal. */
    private static final Pattern SEEDED = Pattern.compile("\"([a-z_]+)\"\\s*:");

    /**
     * Known unimplemented keys, recorded rather than fixed here because they belong to the ROM
     * lane's SHADOW rows (authz V045–V047). Each is a real defect waiting on the engine work that
     * would give it meaning — and every one of those rows is {@code active=false}, so none is
     * currently mis-evaluating in production. They must not be flipped ACTIVE while listed here:
     * {@code rom-hpa-no-operational-access} in particular would, on activation, deny
     * HPA_OVERSIGHT_OFFICER every {@code /regulatory/} path — including the two oversight ALLOW
     * rules seeded immediately above it.
     */
    private static final Set<String> BASELINE = Set.of(
            "require_token_org_match",
            "deny_when_org_mismatch",
            "require_docket_assignment",
            "deny_when_not_docketed",
            "require_escalation_grant",
            "deny_operational_lanes");

    @Test
    void everyConditionKeySeededIsOneTheEngineImplements() throws IOException {
        Set<String> implemented = new TreeSet<>();
        try (Stream<Path> sources = Files.walk(SOURCE)) {
            for (Path java : sources.filter(p -> p.toString().endsWith(".java")).toList()) {
                Matcher m = IMPLEMENTED.matcher(Files.readString(java));
                while (m.find()) {
                    implemented.add(m.group(1));
                }
            }
        }
        assertThat(implemented)
                .withFailMessage("no condition keys resolved from the service source — the pattern "
                                 + "has stopped working, so this guard would pass while checking nothing")
                .hasSizeGreaterThan(5);

        List<String> violations = new ArrayList<>();
        int rulesScanned = 0;

        try (Stream<Path> files = Files.list(MIGRATIONS)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".sql")).sorted().toList()) {
                String sql = Files.readString(file);
                // Only look inside conditions JSON literals, not at arbitrary SQL strings.
                Matcher conditions = Pattern.compile("'\\{[^']*\\}'").matcher(sql);
                while (conditions.find()) {
                    rulesScanned++;
                    Matcher keys = SEEDED.matcher(conditions.group());
                    while (keys.find()) {
                        String key = keys.group(1);
                        if (!implemented.contains(key)) {
                            violations.add(file.getFileName() + " -> " + key);
                        }
                    }
                }
            }
        }

        assertThat(rulesScanned)
                .withFailMessage("no conditions literals found — the scanner has stopped working")
                .isGreaterThan(10);

        Set<String> fresh = new LinkedHashSet<>(new TreeSet<>(violations));
        fresh.removeIf(v -> BASELINE.contains(v.substring(v.lastIndexOf("-> ") + 3)));

        assertThat(fresh)
                .withFailMessage("""
                        %d seeded policy condition key(s) are not implemented in PolicyEngine.

                        An unrecognised key is SILENTLY IGNORED by evaluateConditions, which then
                        returns true — so the rule does not sit inert, it evaluates on whatever
                        remains. On a DENY that means denying more than intended, and DENY wins.

                        Either implement the key in evaluateConditions (CZO channel), or rewrite the
                        rule so its conditions say what it actually enforces.

                        %s""", fresh.size(), String.join("\n  ", fresh))
                .isEmpty();

        // Force the ratchet. Without this the list only ever grows stale: a key that has since
        // been implemented sits here implying a defect that no longer exists, and the next reader
        // cannot tell the live entries from the historical ones. allowed_jurisdictions was exactly
        // that — listed as unimplemented, then implemented in RB-2c, and nothing made anyone
        // remove it.
        Set<String> seededKeys = new TreeSet<>();
        for (String violation : violations) {
            seededKeys.add(violation.substring(violation.lastIndexOf("-> ") + 3));
        }
        Set<String> stale = new TreeSet<>(BASELINE);
        stale.removeAll(seededKeys);
        assertThat(stale)
                .withFailMessage("These baseline entries no longer violate — either the key is now "
                                 + "implemented or the rule was rewritten. Delete them so the list "
                                 + "keeps shrinking:%n  %s", String.join("\n  ", stale))
                .isEmpty();
    }

    /**
     * The country root's limits must be real rules, not an absence of grant: an ALLOW added later
     * by someone who has not read org-registry V009 would out-rank an absence, but cannot out-rank
     * a DENY.
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
}
