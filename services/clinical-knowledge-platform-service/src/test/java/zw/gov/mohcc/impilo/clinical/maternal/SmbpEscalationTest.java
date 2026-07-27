package zw.gov.mohcc.impilo.clinical.maternal;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import zw.gov.mohcc.impilo.clinical.rules.tabular.RuleContentLoader;
import zw.gov.mohcc.impilo.clinical.rules.tabular.TabularRule;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Self-monitored blood pressure — reducing a home series to one action.
 *
 * <p>Three properties are the point, and each is a way home monitoring misleads: a severe reading
 * hidden inside a calm average, a short series read as reassurance, and the rest-artefact discard
 * applied to the wrong check.
 */
class SmbpEscalationTest {

    private final RuleContentLoader loader = new RuleContentLoader(new ObjectMapper());
    private final SmbpEscalationService service =
            new SmbpEscalationService(new MaternalDangerSignService(loader));

    private static HomeBpSeriesReducer.Reading r(int sys, int dia) {
        return new HomeBpSeriesReducer.Reading(sys, dia, false);
    }

    private static HomeBpSeriesReducer.Reading firstOfSession(int sys, int dia) {
        return new HomeBpSeriesReducer.Reading(sys, dia, true);
    }

    /** A controlled week: 14 normal readings, none first-of-session. */
    private static List<HomeBpSeriesReducer.Reading> controlledWeek() {
        List<HomeBpSeriesReducer.Reading> list = new ArrayList<>();
        IntStream.range(0, 14).forEach(i -> list.add(r(122, 78)));
        return list;
    }

    // ---------------------------------------------------------------- severe hidden in a calm mean

    @Test
    @DisplayName("a single severe reading among a calm week is URGENT, not averaged away")
    void aSevereReadingIsNotHiddenByTheMean() {
        var week = controlledWeek();
        week.set(6, r(168, 112)); // one severe reading in an otherwise normal fortnight

        var result = service.assess(week, false, null);

        assertThat(result.summary().anySevere()).isTrue();
        // The mean is still comfortably normal — that is exactly the trap.
        assertThat(result.summary().meanSystolic()).isLessThan(135);
        assertThat(result.verdict()).isEqualTo(SmbpEscalationService.Verdict.URGENT);
        assertThat(result.message()).contains("right away");
    }

    @Test
    @DisplayName("a severe reading marked first-of-session still counts for the severe check")
    void aSevereFirstOfSessionReadingIsNotDiscarded() {
        var week = controlledWeek();
        week.add(firstOfSession(170, 114)); // discarded from the mean, but severe

        var result = service.assess(week, false, null);

        // Excluded from the mean...
        assertThat(result.summary().meanSystolic()).isLessThan(135);
        // ...but the emergency is still caught.
        assertThat(result.summary().anySevere()).isTrue();
        assertThat(result.verdict()).isEqualTo(SmbpEscalationService.Verdict.URGENT);
    }

    // ---------------------------------------------------------------- short series is not reassurance

    @Test
    @DisplayName("two normal readings are INSUFFICIENT_DATA, never CONTROLLED")
    void aShortSeriesIsNotControlled() {
        var result = service.assess(List.of(r(120, 76), r(118, 74)), false, null);

        assertThat(result.summary().sufficient()).isFalse();
        assertThat(result.verdict()).isEqualTo(SmbpEscalationService.Verdict.INSUFFICIENT_DATA);
        assertThat(result.message()).contains("not a result");
    }

    @Test
    @DisplayName("enough normal readings IS allowed to read as controlled")
    void aSufficientNormalSeriesIsControlled() {
        var result = service.assess(controlledWeek(), false, null);

        assertThat(result.summary().sufficient()).isTrue();
        assertThat(result.verdict()).isEqualTo(SmbpEscalationService.Verdict.CONTROLLED);
        assertThat(result.message()).contains("keep watching for the danger signs");
    }

    @Test
    @DisplayName("a short series with a severe reading is still URGENT — insufficiency never downgrades an emergency")
    void insufficiencyDoesNotSwallowASevereReading() {
        var result = service.assess(List.of(r(120, 76), r(175, 118)), false, null);

        assertThat(result.summary().sufficient()).isFalse();
        assertThat(result.verdict()).isEqualTo(SmbpEscalationService.Verdict.URGENT);
    }

    // ---------------------------------------------------------------- sustained elevated

    @Test
    @DisplayName("an elevated mean over enough readings is ELEVATED_REFER")
    void sustainedElevationRefers() {
        List<HomeBpSeriesReducer.Reading> week = new ArrayList<>();
        IntStream.range(0, 14).forEach(i -> week.add(r(138, 88)));

        var result = service.assess(week, false, null);

        assertThat(result.summary().elevatedMean()).isTrue();
        assertThat(result.summary().sufficient()).isTrue();
        assertThat(result.verdict()).isEqualTo(SmbpEscalationService.Verdict.ELEVATED_REFER);
    }

    @Test
    @DisplayName("an elevated mean over too few readings does not refer — it is insufficient")
    void elevatedButShortIsInsufficientNotReferral() {
        var result = service.assess(List.of(r(138, 88), r(140, 90)), false, null);

        assertThat(result.summary().elevatedMean()).isTrue();
        assertThat(result.summary().sufficient()).isFalse();
        // Not ELEVATED_REFER — the rule is gated on sufficiency. A two-reading average is not
        // sustained hypertension.
        assertThat(result.verdict()).isEqualTo(SmbpEscalationService.Verdict.INSUFFICIENT_DATA);
    }

    // ---------------------------------------------------------------- danger signs

    @Test
    @DisplayName("a danger sign is URGENT whatever the readings say")
    void dangerSignRoutesUrgent() {
        var result = service.assess(controlledWeek(), true, null);

        assertThat(result.verdict()).isEqualTo(SmbpEscalationService.Verdict.URGENT);
        assertThat(result.alerts()).anyMatch(a -> "SMBP_DANGER_SIGN".equals(a.code()));
    }

    @Test
    @DisplayName("a danger sign never asked is not the same as one denied")
    void unaskedDangerSignIsNotAbsent() {
        // dangerSignPresent = null: the fact is left unset, the rule reads UNKNOWN, and the verdict
        // rests on the BP alone rather than asserting no danger sign.
        var controlled = service.assess(controlledWeek(), null, null);
        assertThat(controlled.verdict()).isEqualTo(SmbpEscalationService.Verdict.CONTROLLED);
        assertThat(controlled.alerts()).noneMatch(a -> "SMBP_DANGER_SIGN".equals(a.code()));
    }

    // ---------------------------------------------------------------- reducer arithmetic

    @Test
    @DisplayName("the first reading of each session is excluded from the mean")
    void firstOfSessionExcludedFromMean() {
        // Two sessions, first reading of each is high and discarded; the counted readings are 120/80.
        var summary = HomeBpSeriesReducer.reduce(List.of(
                firstOfSession(150, 95), r(120, 80), r(120, 80),
                firstOfSession(150, 95), r(120, 80), r(120, 80)), null);

        assertThat(summary.meanCount()).isEqualTo(4);
        assertThat(summary.meanSystolic()).isEqualTo(120);
        assertThat(summary.meanDiastolic()).isEqualTo(80);
    }

    @Test
    @DisplayName("an empty series reduces to nothing, not to a zero that reads as normal")
    void emptySeriesIsNotNormal() {
        var summary = HomeBpSeriesReducer.reduce(List.of(), null);
        assertThat(summary.meanSystolic()).isNull();
        assertThat(summary.sufficient()).isFalse();
        assertThat(summary.anySevere()).isFalse();

        var result = service.assess(List.of(), false, null);
        assertThat(result.verdict()).isEqualTo(SmbpEscalationService.Verdict.INSUFFICIENT_DATA);
    }

    // ---------------------------------------------------------------- content gate

    @TestFactory
    @DisplayName("every escalation rule's fixtures behave as described")
    List<DynamicTest> contentFixturesPass() {
        List<DynamicTest> tests = new ArrayList<>();
        for (TabularRule rule : loader.load(SmbpEscalationService.CONTENT_PATH)) {
            assertThat(rule.testCases()).as("%s ships no fixtures", rule.code()).isNotEmpty();
            for (TabularRule.TestCase tc : rule.testCases()) {
                tests.add(DynamicTest.dynamicTest(rule.code() + " — " + tc.name(), () -> {
                    var assessment = new MaternalDangerSignService(loader)
                            .evaluate(SmbpEscalationService.CONTENT_PATH, tc.facts(), true);
                    boolean fired = assessment.alerts().stream().anyMatch(a -> a.code().equals(rule.code()))
                            || assessment.supersededAlerts().stream().anyMatch(a -> a.code().equals(rule.code()));
                    assertThat(fired).as("%s: %s", rule.code(), tc.name()).isEqualTo(tc.expectFires());
                }));
            }
        }
        return tests;
    }

    @Test
    @DisplayName("every escalation rule states an action and cites its source")
    void rulesAreActionableAndAttributable() {
        var rules = loader.load(SmbpEscalationService.CONTENT_PATH);
        assertThat(rules).isNotEmpty();
        for (TabularRule rule : rules) {
            assertThat(rule.requiredAction()).as("%s has no action", rule.code()).isNotBlank();
            assertThat(rule.sourceRefs()).as("%s cites nothing", rule.code()).isNotEmpty();
            assertThat(rule.provenance()).as("%s no provenance", rule.code()).isNotNull();
        }
    }
}
