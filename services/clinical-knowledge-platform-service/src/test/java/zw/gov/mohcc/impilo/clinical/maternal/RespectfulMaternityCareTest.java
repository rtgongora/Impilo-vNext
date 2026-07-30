package zw.gov.mohcc.impilo.clinical.maternal;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The respectful-maternity-care instrument, run against the live content file.
 *
 * <p>One property decides whether this instrument helps or harms: after normalisation, a low score
 * must mean worse care for every measure. Get the polarity wrong on one item and the facility where no
 * woman was struck reports as the most abusive — which discredits the whole instrument at the moment it
 * is most needed.
 */
class RespectfulMaternityCareTest {

    private final RespectfulMaternityCareService service =
            new RespectfulMaternityCareService(new ObjectMapper());

    private static Map<String, BigDecimal> answers(Object... kv) {
        Map<String, BigDecimal> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put(String.valueOf(kv[i]), BigDecimal.valueOf(((Number) kv[i + 1]).doubleValue()));
        }
        return m;
    }

    private BigDecimal normalisedFor(String measure, int raw) {
        var n = service.normalise(answers(measure, raw));
        return n.scores().stream()
                .filter(s -> s.measure().equals(measure))
                .map(RespectfulMaternityCareService.NormalisedScore::normalisedScore)
                .findFirst().orElseThrow();
    }

    // ── The instrument loads and says what it is ───────────────────────────────────────────────────

    @Test
    @DisplayName("the measure set loads with its real measure codes, not a placeholder")
    void instrumentLoads() {
        var in = service.instrument();

        assertThat(in).isNotNull();
        assertThat(in.instrumentId()).isEqualTo("RESPECTFUL_MATERNITY_CARE");
        assertThat(in.ratingDomain()).isEqualTo("CLIENT_EXPERIENCE");
        assertThat(in.measures()).hasSize(10);
        // "overall" is what a rating carries when nobody built the instrument. Every measure here is a
        // named question that can be acted on.
        assertThat(in.measures()).noneMatch(m -> m.measure().equals("overall"));
        assertThat(in.measure("respect_dignity")).isNotNull();
        assertThat(in.measure("free_to_leave")).isNotNull();
    }

    @Test
    @DisplayName("every measure code fits Rito's existing VARCHAR(64) and needs no schema change")
    void measureCodesFitTheExistingColumn() {
        for (var m : service.instrument().measures()) {
            assertThat(m.measure().length()).isLessThanOrEqualTo(64);
            assertThat(m.measure()).matches("[a-z0-9_]+");
        }
        // The domain must fit VARCHAR(32) too, since it is stored on the same row.
        assertThat(service.instrument().ratingDomain().length()).isLessThanOrEqualTo(32);
    }

    @Test
    @DisplayName("the approval status travels, so a seed is never read as ratified policy")
    void approvalStatusIsCarried() {
        assertThat(service.instrument().approvalStatus()).isEqualTo("ENGINEERING_SEED");
        assertThat(service.instrument().adaptationAuthority()).isEqualTo("PENDING_MOHCC_RATIFICATION");
    }

    // ── Polarity: the property the whole instrument rests on ───────────────────────────────────────

    @Test
    @DisplayName("AFTER NORMALISATION LOW MEANS WORSE CARE FOR EVERY MEASURE, without exception")
    void lowAlwaysMeansWorseCare() {
        var in = service.instrument();
        int low = in.scale().low();
        int high = in.scale().high();

        for (var m : in.measures()) {
            BigDecimal atWorstAnswer = normalisedFor(m.measure(), m.reverseScored() ? high : low);
            BigDecimal atBestAnswer = normalisedFor(m.measure(), m.reverseScored() ? low : high);

            // The worst clinical reality must always normalise to the bottom of the scale, whichever
            // way the question happened to be worded.
            assertThat(atWorstAnswer)
                    .describedAs("worst answer for %s must normalise to the low end", m.measure())
                    .isEqualByComparingTo(BigDecimal.valueOf(low));
            assertThat(atBestAnswer)
                    .describedAs("best answer for %s must normalise to the high end", m.measure())
                    .isEqualByComparingTo(BigDecimal.valueOf(high));
        }
    }

    @Test
    @DisplayName("a positively-worded abuse item is NOT reversed — the defect that inverted the signal")
    void positivelyWordedAbuseItemIsNotReversed() {
        var m = service.instrument().measure("physical_abuse_free");

        // "Were you free from being slapped?" answered 5 (always free) is the GOOD answer. This item
        // was flagged reverseScored while positively worded, which stored a woman who was never struck
        // as 1 of 5 — reporting the facilities with no physical abuse as the most abusive.
        assertThat(m.reverseScored()).isFalse();
        assertThat(normalisedFor("physical_abuse_free", 5))
                .isEqualByComparingTo(BigDecimal.valueOf(5));
        assertThat(normalisedFor("physical_abuse_free", 1))
                .isEqualByComparingTo(BigDecimal.valueOf(1));
    }

    @Test
    @DisplayName("a negatively-worded item is reversed, so being left alone scores badly")
    void negativelyWordedItemIsReversed() {
        var m = service.instrument().measure("never_left_alone");

        // "Were you left alone when you needed someone?" answered 5 (always) is the WORST answer.
        assertThat(m.reverseScored()).isTrue();
        assertThat(normalisedFor("never_left_alone", 5))
                .isEqualByComparingTo(BigDecimal.valueOf(1));
        assertThat(normalisedFor("never_left_alone", 1))
                .isEqualByComparingTo(BigDecimal.valueOf(5));
    }

    @Test
    @DisplayName("a reverse-scored item's wording matches its flag, checked against the prompt")
    void flagsAgreeWithWording() {
        for (var m : service.instrument().measures()) {
            if (!m.reverseScored()) {
                continue;
            }
            // Only two shapes of question need reversing, and both read as harm happening TO her. A
            // measure named for the good state ("..._free", "never_...") whose prompt asks about the
            // good state must not be reversed. This catches the flag drifting from the wording again.
            assertThat(m.prompt().toLowerCase())
                    .describedAs("reverse-scored %s should be worded as harm occurring", m.measure())
                    .doesNotContain("were you free from")
                    .doesNotContain("were you treated with");
        }
    }

    @Test
    @DisplayName("the raw answer is carried and the flip is its own inverse, so nothing is lost")
    void normalisationIsReversible() {
        var n = service.normalise(answers("never_left_alone", 4));
        var s = n.scores().get(0);

        assertThat(s.rawScore()).isEqualByComparingTo(BigDecimal.valueOf(4));
        assertThat(s.normalisedScore()).isEqualByComparingTo(BigDecimal.valueOf(2));
        assertThat(s.reverseScored()).isTrue();
        // high + low - (high + low - x) == x, so the woman's answer is exactly recoverable from a
        // stored normalised value given the instrument. Storing normalised loses nothing.
        assertThat(BigDecimal.valueOf(6).subtract(s.normalisedScore()))
                .isEqualByComparingTo(s.rawScore());
    }

    // ── Blanks and bad values ──────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("AN UNANSWERED ITEM IS EXCLUDED, never scored as the neutral middle")
    void blankIsNotNeutral() {
        var n = service.normalise(answers("respect_dignity", 5));

        // Nine of ten measures were not asked. Imputing 3 for each would report a facility that asked
        // one question as broadly adequate.
        assertThat(n.scores()).hasSize(1);
        assertThat(n.notAsked()).hasSize(9);
        assertThat(n.notAsked()).contains("free_to_leave", "physical_abuse_free");
        assertThat(n.note()).contains("excluded rather than imputed");
        assertThat(n.note()).contains("does not improve by asking fewer questions");
    }

    @Test
    @DisplayName("an out-of-range answer is not clamped into the flattering end of the scale")
    void outOfRangeIsNotClamped() {
        var n = service.normalise(answers("respect_dignity", 7, "consent_before_procedures", 0));

        // Clamping 7 to 5 would fabricate the best possible answer about being treated with dignity.
        assertThat(n.scores()).isEmpty();
        assertThat(n.notAsked()).contains("respect_dignity", "consent_before_procedures");
        assertThat(String.join(" ", n.unknownCodes())).contains("outside 1-5");
    }

    @Test
    @DisplayName("an unknown measure code is reported rather than stored under a name nothing reads")
    void unknownMeasureIsReported() {
        var n = service.normalise(answers("was_the_tea_nice", 5));

        assertThat(n.scores()).isEmpty();
        assertThat(n.unknownCodes()).contains("was_the_tea_nice");
    }

    @Test
    @DisplayName("no answers at all is an absence of evidence, not evidence of respectful care")
    void emptySubmissionSaysSo() {
        var n = service.normalise(Map.of());

        assertThat(n.scores()).isEmpty();
        assertThat(n.note()).contains("absence of evidence, not evidence of respectful care");
    }

    @Test
    @DisplayName("measure codes are matched case-insensitively, as clients actually send them")
    void measureMatchingIsForgiving() {
        var n = service.normalise(answers("RESPECT_DIGNITY", 4));

        assertThat(n.scores()).hasSize(1);
        assertThat(n.scores().get(0).measure()).isEqualTo("respect_dignity");
    }

    @Test
    @DisplayName("a null answer map does not throw and reports everything unasked")
    void nullAnswersAreSafe() {
        var n = service.normalise(null);

        assertThat(n.scores()).isEmpty();
        assertThat(n.notAsked()).hasSize(10);
    }

    // ── The firewall ───────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("the regulation firewall and anonymous default travel with the instrument")
    void governanceRulesAreCarried() {
        var in = service.instrument();

        // These are not decoration. A consumer that renders these measures beside a provider's
        // registration is building the thing the instrument must not become, so the constraint ships
        // with the payload rather than living only in a document nobody reads at integration time.
        assertThat(in.regulationFirewall()).contains("NEVER mutates a licence");
        assertThat(in.anonymousByDefault()).contains("DEFAULT for maternity feedback");
        assertThat(in.reportingRules()).isNotEmpty();
        assertThat(String.join(" ", in.reportingRules())).contains("never blended");
    }
}
