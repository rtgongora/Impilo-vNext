package zw.gov.mohcc.impilo.tshepo.authz.migration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V060 is the D7 instrument: a live ALLOW so the probe is reachable once ext_authz is on,
 * and an inactive DENY that turns that 200 into a 403 when activated. Both halves are
 * load-bearing — either missing and the Flip-1 check is ambiguous.
 */
class GatewayProbeRuleTest extends MigrationPolicyTestBase {

    private static final String V060 = "V060__gateway_probe_deny_rule.sql";

    @Test
    @DisplayName("ALLOW is seeded active; DENY is seeded inactive")
    void polarityOfTheInstrument() throws IOException {
        String sql = readMigration(V060);
        Map<String, String> byName = conditionsByRuleName(sql);

        assertThat(byName.keySet())
                .containsExactlyInAnyOrder("d7-gateway-probe-allow", "d7-gateway-probe-deny");

        // Each VALUES row is: ... conditions, active). The active flag is the last boolean
        // before the closing paren. Pull it by name so a reordered file still fails clearly.
        assertThat(activeFlagFor(sql, "d7-gateway-probe-allow"))
                .as("without a live ALLOW the Flip-1 403 is ambiguous (NO_MATCHING_RULES)")
                .isTrue();
        assertThat(activeFlagFor(sql, "d7-gateway-probe-deny"))
                .as("a DENY seeded active would enforce on apply, defeating the instrument")
                .isFalse();
    }

    private static boolean activeFlagFor(String sql, String ruleName) {
        int nameAt = sql.indexOf("'" + ruleName + "'");
        assertThat(nameAt).as("rule %s missing", ruleName).isGreaterThanOrEqualTo(0);
        // Next row starts at the following uuid literal, or end of VALUES.
        int next = sql.indexOf("'00000000-0000-0000-0000-000000000001'", nameAt + 1);
        String row = next < 0 ? sql.substring(nameAt) : sql.substring(nameAt, next);
        if (row.contains("\n true)") || row.contains("\n true),")) return true;
        if (row.contains("\n false)") || row.contains("\n false),")) return false;
        throw new AssertionError("could not find active flag in row for " + ruleName);
    }

    @Test
    @DisplayName("pin has no trailing slash — otherwise the instrument matches no request")
    void pinIsSegmentBounded() throws IOException {
        Map<String, String> byName = conditionsByRuleName(readMigration(V060));
        for (var entry : byName.entrySet()) {
            assertThat(entry.getValue())
                    .as("%s", entry.getKey())
                    .contains("\"/internal/v1/gateway-probe\"")
                    .doesNotContain("\"/internal/v1/gateway-probe/\"");
        }
    }
}
