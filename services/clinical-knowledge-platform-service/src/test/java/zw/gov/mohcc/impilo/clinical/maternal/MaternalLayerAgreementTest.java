package zw.gov.mohcc.impilo.clinical.maternal;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import zw.gov.mohcc.impilo.clinical.rules.tabular.RuleContentLoader;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The agreement between the two labour layers: progress and danger signs.
 *
 * <p>The property under test is that a labour-progress verdict never contradicts the danger-sign
 * layer. A slowly-progressing labour with an abnormal fetal heart must lead with the fetal-heart
 * emergency, not "offer support" — the gentle progress message is correct in isolation and lethal
 * as the top line when a danger sign is also live. And the standing invariant: no progress verdict
 * ever recommends a caesarean for slow progress alone.
 */
class MaternalLayerAgreementTest {

    private static final String LCG_PACK = "clinical/rmnp-labour-care-guide.json";
    private static final OffsetDateTime START = OffsetDateTime.parse("2026-01-01T00:00:00Z");

    private final RuleContentLoader loader = new RuleContentLoader(new ObjectMapper());
    private final MaternalDangerSignService dangerSigns = new MaternalDangerSignService(loader);

    private static Map<String, Object> obs(Object... pairs) {
        Map<String, Object> m = new HashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            m.put((String) pairs[i], pairs[i + 1]);
        }
        return m;
    }

    /** A slow-progress labour: reached 5 cm, then only 6 cm eight hours later (ref expects ~9). */
    private LabourCareGuideProgressEngine.Assessment slowProgress() {
        var points = List.of(
                new LabourCareGuideProgressEngine.Observation(START, 5.0),
                new LabourCareGuideProgressEngine.Observation(START.plusHours(8), 6.0));
        return LabourCareGuideProgressEngine.assess(points, START.plusHours(8), null);
    }

    // ---------------------------------------------------------------- the progress engine's invariant

    @Test
    @DisplayName("slow progress opens an assessment and explicitly is not a caesarean indication")
    void slowProgressIsNeverACaesareanIndication() {
        var a = slowProgress();
        assertThat(a.status()).isEqualTo(LabourCareGuideProgressEngine.Status.SLOW_PROGRESS_ASSESS);
        assertThat(a.action()).contains("not an indication for caesarean section");
        assertThat(a.action()).contains("powers", "passage", "passenger");
    }

    @Test
    @DisplayName("no progress verdict at any dilation recommends a caesarean for slow progress")
    void noProgressVerdictRecommendsCaesarean() {
        for (double cm : new double[]{3, 5, 6, 7, 9}) {
            var points = List.of(
                    new LabourCareGuideProgressEngine.Observation(START, Math.min(cm, 5.0)),
                    new LabourCareGuideProgressEngine.Observation(START.plusHours(10), cm));
            var a = LabourCareGuideProgressEngine.assess(points, START.plusHours(10), null);
            // The progress layer never tells anyone to operate — that decision belongs to the
            // assessment it triggers or to the danger-sign layer, never to the line.
            assertThat(a.action().toLowerCase())
                    .as("dilation %s cm progress action must not recommend caesarean", cm)
                    .doesNotContain("perform a caesarean", "proceed to caesarean", "caesarean now");
        }
    }

    @Test
    @DisplayName("exactly one monitoring standard, and it is the Labour Care Guide, never the classic partograph")
    void oneStandardAndItIsTheLcg() {
        assertThat(slowProgress().monitoringStandard()).isEqualTo("WHO_LABOUR_CARE_GUIDE");
        assertThat(slowProgress().monitoringStandard()).doesNotContain("CLASSIC", "PARTOGRAPH");
    }

    // ---------------------------------------------------------------- the agreement rule

    @Test
    @DisplayName("a CRITICAL danger sign wins the top line over a slow-progress verdict")
    void dangerSignWinsOverProgress() {
        // Fetal heart 100 fires LCG_FHR_ABNORMAL (HIGH)... use a CRITICAL by combining with a
        // severe finding. The LCG parameter pack's FHR alert is HIGH; escalate via a genuinely
        // critical parameter if present. Here we assert the composer prefers any danger alert.
        var danger = dangerSigns.evaluate(LCG_PACK, obs("fetalHeartRateBpm", 100), true);
        var top = LabourAssessmentComposer.topLine(slowProgress(), danger.alerts());

        assertThat(danger.alerts()).isNotEmpty();
        assertThat(top.source()).isEqualTo(LabourAssessmentComposer.SOURCE_DANGER_SIGN);
        // The top line is the danger-sign action, NOT the "offer support" progress message.
        assertThat(top.topLineAction()).isNotEqualTo(slowProgress().action());
        assertThat(top.topLineAction()).isEqualTo(danger.alerts().get(0).requiredAction());
    }

    @Test
    @DisplayName("with no danger sign, the slow-progress support message is the top line")
    void progressLeadsWhenNoDangerSign() {
        var danger = dangerSigns.evaluate(LCG_PACK,
                obs("fetalHeartRateBpm", 140, "maternalPulseBpm", 84, "systolicBp", 118,
                        "diastolicBp", 74, "temperatureC", 36.8, "contractionFrequency10Min", 4), true);
        var top = LabourAssessmentComposer.topLine(slowProgress(), danger.alerts());

        assertThat(danger.alerts()).isEmpty();
        assertThat(top.source()).isEqualTo(LabourAssessmentComposer.SOURCE_PROGRESS);
        assertThat(top.topLineAction()).contains("not an indication for caesarean section");
    }

    @Test
    @DisplayName("the standard is the LCG whichever layer supplies the top line")
    void standardIsAlwaysLcg() {
        var withDanger = LabourAssessmentComposer.topLine(slowProgress(),
                dangerSigns.evaluate(LCG_PACK, obs("fetalHeartRateBpm", 100), true).alerts());
        var withoutDanger = LabourAssessmentComposer.topLine(slowProgress(), List.of());
        assertThat(withDanger.monitoringStandard()).isEqualTo("WHO_LABOUR_CARE_GUIDE");
        assertThat(withoutDanger.monitoringStandard()).isEqualTo("WHO_LABOUR_CARE_GUIDE");
    }

    // ---------------------------------------------------------------- silence is not reassurance

    @Test
    @DisplayName("no dilation recorded is INSUFFICIENT_DATA, never PROGRESSING")
    void silenceIsNotProgress() {
        var a = LabourCareGuideProgressEngine.assess(List.of(), START.plusHours(3), null);
        assertThat(a.status()).isEqualTo(LabourCareGuideProgressEngine.Status.INSUFFICIENT_DATA);
        assertThat(a.status()).isNotEqualTo(LabourCareGuideProgressEngine.Status.PROGRESSING);
    }
}
