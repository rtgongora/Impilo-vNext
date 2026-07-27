package zw.gov.mohcc.impilo.clinical.maternal;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import zw.gov.mohcc.impilo.clinical.rules.tabular.RuleContentLoader;
import zw.gov.mohcc.impilo.clinical.rules.tabular.TabularRule;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Routine antenatal actions — the recommended offers, surfaced as a due-action checklist.
 *
 * <p>These are recommendations, not danger signs, so they run through the maternal engine at LOW
 * severity. The behaviours worth pinning are the ones where a routine offer quietly goes undone: the
 * aspirin window that closes at 20 weeks, IPTp counted as owed until three doses, and tetanus owed
 * until protection is complete.
 */
class AncRoutineActionsTest {

    private static final String PACK = "clinical/rmnp-anc-routine-actions.json";

    private final RuleContentLoader loader = new RuleContentLoader(new ObjectMapper());
    private final MaternalDangerSignService service = new MaternalDangerSignService(loader);

    private static Map<String, Object> facts(Object... pairs) {
        Map<String, Object> map = new HashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put((String) pairs[i], pairs[i + 1]);
        }
        return map;
    }

    private List<String> due(Map<String, Object> f) {
        return service.evaluate(PACK, f, true).alerts().stream()
                .map(MaternalDangerSignService.Alert::code).toList();
    }

    @Test
    @DisplayName("iron and folic acid is offered at every contact")
    void ifaIsUniversal() {
        assertThat(due(facts("gestationalAgeWeeks", 8))).contains("ANC_RA_IFA");
        assertThat(due(facts("gestationalAgeWeeks", 38))).contains("ANC_RA_IFA");
    }

    @Test
    @DisplayName("aspirin is due for a high-risk woman before 20 weeks")
    void aspirinInWindow() {
        assertThat(due(facts("gestationalAgeWeeks", 14, "preeclampsiaHighRisk", "YES")))
                .contains("ANC_RA_ASPIRIN");
    }

    @Test
    @DisplayName("aspirin is NOT offered after the window closes — a late high-risk booker has missed it")
    void aspirinWindowClosesAt20Weeks() {
        // The rule does not fire, because starting aspirin after 20 weeks has largely lost its
        // benefit. The action text tells the clinician to record the miss rather than start it as
        // if on time — the point is that "not offered" here is a real clinical fact, not an omission.
        assertThat(due(facts("gestationalAgeWeeks", 28, "preeclampsiaHighRisk", "YES")))
                .doesNotContain("ANC_RA_ASPIRIN");
    }

    @Test
    @DisplayName("aspirin is not offered to a woman who is not high risk")
    void aspirinOnlyForHighRisk() {
        assertThat(due(facts("gestationalAgeWeeks", 14, "preeclampsiaHighRisk", "NO")))
                .doesNotContain("ANC_RA_ASPIRIN");
    }

    @Test
    @DisplayName("IPTp is owed until three doses, in an endemic area, from the second trimester")
    void iptpOwedUntilThree() {
        assertThat(due(facts("malariaEndemicArea", true, "gestationalAgeWeeks", 22, "iptpDosesReceived", 1)))
                .contains("ANC_RA_IPTP");
        assertThat(due(facts("malariaEndemicArea", true, "gestationalAgeWeeks", 34, "iptpDosesReceived", 3)))
                .doesNotContain("ANC_RA_IPTP");
    }

    @Test
    @DisplayName("IPTp does not apply in the first trimester or a non-endemic area")
    void iptpGating() {
        assertThat(due(facts("malariaEndemicArea", true, "gestationalAgeWeeks", 10, "iptpDosesReceived", 0)))
                .doesNotContain("ANC_RA_IPTP");
        assertThat(due(facts("malariaEndemicArea", false, "gestationalAgeWeeks", 22, "iptpDosesReceived", 0)))
                .doesNotContain("ANC_RA_IPTP");
    }

    @Test
    @DisplayName("calcium is offered only where the population is flagged low-calcium")
    void calciumByPolicy() {
        assertThat(due(facts("lowCalciumIntakePopulation", true))).contains("ANC_RA_CALCIUM");
        assertThat(due(facts("lowCalciumIntakePopulation", false))).doesNotContain("ANC_RA_CALCIUM");
    }

    @Test
    @DisplayName("tetanus is owed until protection is complete")
    void tetanusOwedUntilComplete() {
        assertThat(due(facts("tetanusProtectionComplete", "NO"))).contains("ANC_RA_TETANUS");
        assertThat(due(facts("tetanusProtectionComplete", "YES"))).doesNotContain("ANC_RA_TETANUS");
    }

    @Test
    @DisplayName("every routine action is LOW severity and non-interruptive — a reminder, not an alarm")
    void routineActionsAreNotAlarms() {
        for (TabularRule rule : loader.load(PACK)) {
            assertThat(rule.severity()).as("%s is not LOW", rule.code()).isEqualTo("LOW");
            assertThat(rule.interruptive()).as("%s is interruptive", rule.code()).isFalse();
        }
    }

    @TestFactory
    @DisplayName("every rule's fixtures behave as described")
    List<DynamicTest> contentFixturesPass() {
        List<DynamicTest> tests = new ArrayList<>();
        for (TabularRule rule : loader.load(PACK)) {
            assertThat(rule.testCases()).as("%s ships no fixtures", rule.code()).isNotEmpty();
            for (TabularRule.TestCase tc : rule.testCases()) {
                tests.add(DynamicTest.dynamicTest(rule.code() + " — " + tc.name(), () -> {
                    boolean fired = service.evaluate(PACK, tc.facts(), true).alerts().stream()
                            .anyMatch(a -> a.code().equals(rule.code()));
                    assertThat(fired).as("%s: %s", rule.code(), tc.name()).isEqualTo(tc.expectFires());
                }));
            }
        }
        return tests;
    }

    @Test
    @DisplayName("every rule states an action and cites its source")
    void rulesAreActionableAndAttributable() {
        var rules = loader.load(PACK);
        assertThat(rules).isNotEmpty();
        for (TabularRule rule : rules) {
            assertThat(rule.requiredAction()).as("%s has no action", rule.code()).isNotBlank();
            assertThat(rule.sourceRefs()).as("%s cites nothing", rule.code()).isNotEmpty();
            assertThat(rule.provenance()).as("%s no provenance", rule.code()).isNotNull();
        }
    }
}
