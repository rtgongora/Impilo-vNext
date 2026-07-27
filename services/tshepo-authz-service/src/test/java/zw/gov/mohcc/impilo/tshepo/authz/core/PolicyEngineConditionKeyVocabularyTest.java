package zw.gov.mohcc.impilo.tshepo.authz.core;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link PolicyEngine#RECOGNISED_CONDITION_KEYS} must be <em>exactly</em> the keys the engine
 * source acts on — no more, no less.
 *
 * <p><strong>Why this exists.</strong> The set is the single source of truth for the runtime
 * unknown-key guard: a condition key outside it is treated as a non-match. That creates a footgun
 * in both directions, and each direction is a real defect:</p>
 *
 * <ul>
 *   <li>Add a {@code conditions.containsKey("new_key")} handler to {@code evaluateConditions} but
 *       forget to list {@code new_key} here → the engine would evaluate the key correctly in its
 *       own block, then reject the rule at the unknown-key guard. A key the engine implements would
 *       be unusable.</li>
 *   <li>List a key here that no handler reads → the set drifts into fiction; the guard would accept
 *       a seed the engine does nothing with — the very "reads meaningful, matches nothing" bug this
 *       whole seam exists to remove.</li>
 * </ul>
 *
 * <p>So this pins the set to what the source actually accesses. It reads {@code PolicyEngine.java}
 * and extracts every {@code conditions.get("…")}/{@code containsKey("…")} on a snake_case key (or
 * {@code visibility}) — the same shape {@code PolicyConditionKeyContractTest} recognises as
 * "implemented" — and asserts equality. Within {@code PolicyEngine} the only such accesses are the
 * condition keys, so the scan is exact.</p>
 */
class PolicyEngineConditionKeyVocabularyTest {

    private static final Path ENGINE =
            Path.of("src/main/java/zw/gov/mohcc/impilo/tshepo/authz/core/PolicyEngine.java");

    /**
     * A condition-key access: {@code .get("key")} or {@code .containsKey("key")} where the key is a
     * snake_case literal (at least one underscore) or {@code visibility}. The underscore requirement
     * is what keeps this from matching unrelated single-word map lookups, and it matches the pattern
     * the seed contract guard uses so the two guards agree on what "a condition key" looks like.
     * {@code Set.of("min_loa", …)} entries are NOT matched — they are not preceded by {@code .get(}
     * or {@code .containsKey(} — so the RECOGNISED literal does not scan as its own evidence.
     */
    private static final java.util.regex.Pattern KEY_ACCESS =
            java.util.regex.Pattern.compile(
                    "\\.(?:containsKey|get)\\(\"([a-z][a-z0-9_]*_[a-z0-9_]*|visibility)\"\\)");

    @Test
    void recognisedSetMatchesTheKeysTheEngineActuallyHandles() throws IOException {
        String source = Files.readString(ENGINE);

        Set<String> accessed = new TreeSet<>();
        Matcher m = KEY_ACCESS.matcher(source);
        while (m.find()) {
            accessed.add(m.group(1));
        }

        // Guard the guard: if the scan finds nothing the pattern has rotted and this test would pass
        // while asserting nothing meaningful.
        assertThat(accessed)
                .withFailMessage("no condition-key accesses found in PolicyEngine.java — the scan "
                                 + "pattern has stopped working, so this guard is checking nothing")
                .hasSizeGreaterThan(10);

        assertThat(new TreeSet<>(PolicyEngine.RECOGNISED_CONDITION_KEYS))
                .withFailMessage("""
                        RECOGNISED_CONDITION_KEYS has drifted from what evaluateConditions handles.

                        Keys the engine accesses but the set omits (the engine would REJECT a rule
                        using them at the unknown-key guard): %s
                        Keys the set lists but no handler reads (fiction — a seed could carry them and
                        the engine would do nothing): %s

                        Keep the constant in lockstep with the condition checks in evaluateConditions.""",
                        minus(accessed, PolicyEngine.RECOGNISED_CONDITION_KEYS),
                        minus(PolicyEngine.RECOGNISED_CONDITION_KEYS, accessed))
                .isEqualTo(accessed);
    }

    private static Set<String> minus(Set<String> a, Set<String> b) {
        Set<String> diff = new TreeSet<>(a);
        diff.removeAll(b);
        return diff;
    }
}
