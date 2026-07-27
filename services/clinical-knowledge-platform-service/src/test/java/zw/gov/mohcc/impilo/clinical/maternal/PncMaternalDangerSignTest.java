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
 * Postnatal maternal danger signs — the period in which most maternal deaths happen.
 *
 * <p>The properties that matter: the postpartum hypertensive presentation is the same emergency as
 * the antenatal one and shares its signal family; thoughts of self-harm are CRITICAL and
 * non-overridable, never softened into "low mood"; and every CRITICAL sign is interruptive and
 * un-dismissible, so no downstream bereavement or sensitivity filter can suppress it — a woman after
 * a loss is at higher risk, not lower.
 */
class PncMaternalDangerSignTest {

    private static final String PACK = "clinical/rmnp-pnc-maternal-danger-signs.json";

    private final RuleContentLoader loader = new RuleContentLoader(new ObjectMapper());
    private final MaternalDangerSignService service = new MaternalDangerSignService(loader);

    private static Map<String, Object> facts(Object... pairs) {
        Map<String, Object> map = new HashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put((String) pairs[i], pairs[i + 1]);
        }
        return map;
    }

    private List<String> codes(Map<String, Object> f) {
        return service.evaluate(PACK, f, true).alerts().stream()
                .map(MaternalDangerSignService.Alert::code).toList();
    }

    @Test
    @DisplayName("secondary PPH after birth is critical and non-overridable")
    void secondaryPph() {
        var alert = service.evaluate(PACK, facts("vaginalBleeding", "HEAVY"), true).alerts().stream()
                .filter(a -> "PNC_SECONDARY_PPH".equals(a.code())).findFirst().orElseThrow();
        assertThat(alert.severity()).isEqualTo("CRITICAL");
        assertThat(alert.overrideAllowed()).isFalse();
    }

    @Test
    @DisplayName("puerperal sepsis needs fever/offensive lochia AND a local sign, not fever alone")
    void puerperalSepsis() {
        assertThat(codes(facts("temperature", 38.4, "offensiveLochia", "PRESENT")))
                .contains("PNC_PUERPERAL_SEPSIS");
        assertThat(codes(facts("temperature", 38.4, "offensiveLochia", "ABSENT", "lowerAbdominalPain", "ABSENT")))
                .doesNotContain("PNC_PUERPERAL_SEPSIS");
    }

    @Test
    @DisplayName("postpartum eclampsia is on the shared hypertensive-disorders family")
    void postpartumEclampsiaIsHypertensiveFamily() {
        var rung = loader.load(PACK).stream()
                .filter(r -> "PNC_POSTPARTUM_ECLAMPSIA".equals(r.code())).findFirst().orElseThrow();
        assertThat(rung.signalFamily()).isEqualTo("HYPERTENSIVE_DISORDERS");
        assertThat(codes(facts("convulsions", "PRESENT"))).contains("PNC_POSTPARTUM_ECLAMPSIA");
    }

    @Test
    @DisplayName("venous thromboembolism fires on breathlessness — the postpartum period's most dangerous miss")
    void vte() {
        assertThat(codes(facts("chestPainOrBreathlessness", "PRESENT"))).contains("PNC_VTE");
    }

    @Test
    @DisplayName("thoughts of self-harm are CRITICAL and non-overridable, never mere low mood")
    void selfHarmIsCriticalNeverSuppressed() {
        var alert = service.evaluate(PACK, facts("thoughtsOfSelfHarmOrHarmingBaby", "PRESENT"), true)
                .alerts().stream().filter(a -> "PNC_SELF_HARM_RISK".equals(a.code()))
                .findFirst().orElseThrow();
        assertThat(alert.severity()).isEqualTo("CRITICAL");
        assertThat(alert.overrideAllowed()).isFalse();
        assertThat(alert.interruptive()).isTrue();
        assertThat(alert.explanation()).contains("never suppressed");
    }

    @Test
    @DisplayName("every CRITICAL postnatal sign is interruptive and non-overridable — no filter can suppress it")
    void criticalSignsCannotBeSuppressed() {
        for (TabularRule rule : loader.load(PACK)) {
            if ("CRITICAL".equals(rule.severity())) {
                assertThat(rule.interruptive()).as("%s CRITICAL not interruptive", rule.code()).isTrue();
                assertThat(rule.overrideAllowed()).as("%s CRITICAL dismissible", rule.code()).isFalse();
            }
        }
    }

    @Test
    @DisplayName("silence is not reassurance — an empty incomplete assessment is not 'no danger signs'")
    void silenceIsNotReassurance() {
        assertThat(service.evaluate(PACK, facts(), false).noDangerSignsFound()).isFalse();
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
                            .anyMatch(a -> a.code().equals(rule.code()))
                            || service.evaluate(PACK, tc.facts(), true).supersededAlerts().stream()
                            .anyMatch(a -> a.code().equals(rule.code()));
                    assertThat(fired).as("%s: %s", rule.code(), tc.name()).isEqualTo(tc.expectFires());
                }));
            }
        }
        return tests;
    }
}
