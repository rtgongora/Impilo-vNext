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
 * The WHO Labour Care Guide monitoring layer.
 *
 * <p>Two properties are the point of this slice. First, exactly one monitoring standard, and it is
 * the LCG — never the classic partograph alongside it, because the two are designed to disagree on
 * slow progress and showing both would put a contradiction on one labour. Second, this layer does
 * NOT decide progress yet, and it says so — the absence of a progress alert must not read as adequate
 * progress, which is exactly the deferred cervical-dilation reference the freeze slice will add.
 */
class LabourCareGuideTest {

    private final RuleContentLoader loader = new RuleContentLoader(new ObjectMapper());
    private final LabourCareGuideService service =
            new LabourCareGuideService(new MaternalDangerSignService(loader), new ObjectMapper());

    private static Map<String, Object> obs(Object... pairs) {
        Map<String, Object> map = new HashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put((String) pairs[i], pairs[i + 1]);
        }
        return map;
    }

    private List<String> alertCodes(Map<String, Object> o) {
        return service.assess(o, true).parameterAlerts().stream()
                .map(MaternalDangerSignService.Alert::code).toList();
    }

    // ---------------------------------------------------------------- one standard, and it is the LCG

    @Test
    @DisplayName("the assessment carries exactly one monitoring standard, and it is the Labour Care Guide")
    void exactlyOneStandardAndItIsTheLcg() {
        var a = service.assess(obs("fetalHeartRateBpm", 140), true);
        assertThat(a.monitoringStandard()).isEqualTo("WHO_LABOUR_CARE_GUIDE");
        // Never the classic partograph — the LCG replaces it, it does not accompany it.
        assertThat(a.monitoringStandard()).doesNotContain("partograph", "classic", "CLASSIC");
    }

    @Test
    @DisplayName("progress is explicitly NOT decided by this layer, and the note says so")
    void progressIsNotDecidedHere() {
        var a = service.assess(obs("fetalHeartRateBpm", 140), true);
        assertThat(a.progressDecided()).isFalse();
        assertThat(a.note()).contains("does not yet decide whether progress is adequate");
    }

    // ---------------------------------------------------------------- parameter alerts

    @Test
    @DisplayName("an abnormal fetal heart rate is flagged")
    void fhrAlert() {
        assertThat(alertCodes(obs("fetalHeartRateBpm", 100))).contains("LCG_FHR_ABNORMAL");
        assertThat(alertCodes(obs("fetalHeartRateBpm", 175))).contains("LCG_FHR_ABNORMAL");
        assertThat(alertCodes(obs("fetalHeartRateBpm", 140))).doesNotContain("LCG_FHR_ABNORMAL");
    }

    @Test
    @DisplayName("tachysystole is flagged, and a normal contraction frequency is not")
    void tachysystoleAlert() {
        assertThat(alertCodes(obs("contractionFrequency10Min", 6))).contains("LCG_CONTRACTIONS_TACHYSYSTOLE");
        assertThat(alertCodes(obs("contractionFrequency10Min", 4))).doesNotContain("LCG_CONTRACTIONS_TACHYSYSTOLE");
    }

    @Test
    @DisplayName("meconium liquor, severe moulding, high pulse, high BP and fever are each flagged")
    void theParameterAlertsFire() {
        assertThat(alertCodes(obs("liquor", "MECONIUM_THICK"))).contains("LCG_LIQUOR_MECONIUM");
        assertThat(alertCodes(obs("moulding", "PLUS_3"))).contains("LCG_MOULDING_SEVERE");
        assertThat(alertCodes(obs("maternalPulseBpm", 128))).contains("LCG_MATERNAL_PULSE_HIGH");
        assertThat(alertCodes(obs("systolicBp", 146, "diastolicBp", 92))).contains("LCG_MATERNAL_BP_HIGH");
        assertThat(alertCodes(obs("temperatureC", 38.1))).contains("LCG_MATERNAL_TEMPERATURE");
    }

    @Test
    @DisplayName("the most severe parameter alert leads")
    void mostSevereLeads() {
        var a = service.assess(obs(
                "fetalHeartRateBpm", 100, "maternalPulseBpm", 128, "temperatureC", 38.1), true);
        // FHR and BP families are HIGH, pulse and temperature MEDIUM — a HIGH alert leads.
        assertThat(a.parameterAlerts().get(0).severity()).isEqualTo("HIGH");
        assertThat(a.topLineAction()).isEqualTo(a.parameterAlerts().get(0).requiredAction());
    }

    // ---------------------------------------------------------------- silence is not reassurance

    @Test
    @DisplayName("an empty observation with screening incomplete does not read as a normal labour")
    void silenceIsNotReassurance() {
        var a = service.assess(obs(), false);
        assertThat(a.parameterAlerts()).isEmpty();
        assertThat(a.incomplete()).isTrue();
        // Even with no alerts, progress is undecided and the standard is still asserted — nothing
        // here says the labour is fine.
        assertThat(a.progressDecided()).isFalse();
        assertThat(a.monitoringStandard()).isEqualTo("WHO_LABOUR_CARE_GUIDE");
    }

    // ---------------------------------------------------------------- deferral is explicit

    @Test
    @DisplayName("the pack declares the deferred progress rule rather than shipping an approximate curve")
    void theDeferralIsDeclared() {
        var root = new com.fasterxml.jackson.databind.ObjectMapper();
        try (var in = getClass().getClassLoader().getResourceAsStream(LabourCareGuideService.CONTENT_PATH)) {
            var node = root.readTree(in);
            var deferred = node.path("provenance").path("sourceVerification").path("deferred").asText();
            assertThat(deferred)
                    .as("the novel cervical-dilation reference must be declared deferred, not silently absent")
                    .contains("cervical-dilation");
            assertThat(deferred).contains("slow progress");
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    // ---------------------------------------------------------------- content gate

    @TestFactory
    @DisplayName("every parameter rule's fixtures behave as described")
    List<DynamicTest> contentFixturesPass() {
        List<DynamicTest> tests = new ArrayList<>();
        for (TabularRule rule : loader.load(LabourCareGuideService.CONTENT_PATH)) {
            assertThat(rule.testCases()).as("%s ships no fixtures", rule.code()).isNotEmpty();
            for (TabularRule.TestCase tc : rule.testCases()) {
                tests.add(DynamicTest.dynamicTest(rule.code() + " — " + tc.name(), () -> {
                    boolean fired = service.assess(tc.facts(), true).parameterAlerts().stream()
                            .anyMatch(a -> a.code().equals(rule.code()));
                    assertThat(fired).as("%s: %s", rule.code(), tc.name()).isEqualTo(tc.expectFires());
                }));
            }
        }
        return tests;
    }

    @Test
    @DisplayName("every rule states an action and cites the Labour Care Guide")
    void rulesAreActionableAndAttributable() {
        var rules = loader.load(LabourCareGuideService.CONTENT_PATH);
        assertThat(rules).isNotEmpty();
        for (TabularRule rule : rules) {
            assertThat(rule.requiredAction()).as("%s has no action", rule.code()).isNotBlank();
            assertThat(rule.sourceRefs()).as("%s cites nothing", rule.code()).isNotEmpty();
            assertThat(rule.provenance()).as("%s no provenance", rule.code()).isNotNull();
        }
    }
}
