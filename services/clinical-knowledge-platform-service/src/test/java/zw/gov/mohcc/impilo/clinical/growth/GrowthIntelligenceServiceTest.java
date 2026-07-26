package zw.gov.mohcc.impilo.clinical.growth;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The fixtures are growth trajectories, not numbers: a child tracking down, one who lost weight,
 * one recovering after treatment, and one whose apparent loss is an artefact of being measured
 * standing up for the first time.
 */
class GrowthIntelligenceServiceTest {

    private GrowthIntelligenceService service;

    @BeforeEach
    void setUp() {
        service = new GrowthIntelligenceService(new ObjectMapper());
    }

    private static Map<String, Object> point(int ageDays, double weightKg, double wfaZ) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ageDays", ageDays);
        m.put("weightKg", weightKg);
        m.put("weightForAgeZ", wfaZ);
        return m;
    }

    private static List<String> codes(GrowthIntelligenceEngine.Assessment a) {
        return a.signals().stream().map(GrowthIntelligenceEngine.Signal::code).toList();
    }

    // --- the content's own fixtures, run against the engine ------------------------------------

    @TestFactory
    @DisplayName("growth content fixtures")
    Stream<DynamicTest> contentFixturesPass() {
        GrowthIntelligenceContent content = service.content();
        assertThat(content.signals()).as("growth pack must load").isNotEmpty();
        assertThat(content.testCases()).as("pack must ship fixtures").isNotEmpty();

        List<DynamicTest> tests = new ArrayList<>();
        for (GrowthIntelligenceContent.TestCase fixture : content.testCases()) {
            tests.add(DynamicTest.dynamicTest(fixture.name(), () -> {
                var a = service.assess(fixture.points());

                if (fixture.expectInsufficient()) {
                    assertThat(a.assessable()).as("%s — assessable", fixture.name()).isFalse();
                    return;
                }
                assertThat(codes(a)).as("%s — signals", fixture.name())
                        .containsExactlyInAnyOrderElementsOf(fixture.expectSignals());
                assertThat(a.interpretationBlocked()).as("%s — blocked", fixture.name())
                        .isEqualTo(fixture.expectBlocked());
                if (fixture.expectReferral()) {
                    assertThat(a.referralRequired()).as("%s — referral", fixture.name()).isTrue();
                }
            }));
        }
        return tests.stream();
    }

    // --- the property that makes this a trend engine ---------------------------------------------

    @Test
    void oneMeasurementIsNotATrendAndIsNotReportedAsNoConcern() {
        // A child weighed once has not been shown to be growing well. Returning "no concerns"
        // would be a reassurance drawn from an absence of information — the same failure the
        // danger-sign and classification engines refuse.
        var a = service.assess(List.of(point(200, 7.4, -0.4)));

        assertThat(a.assessable()).isFalse();
        assertThat(a.anyConcern()).isFalse();
        assertThat(a.notAssessableReason()).contains("change over time");
        assertThat(a.signals()).isEmpty();
    }

    @Test
    void noMeasurementsAtAllSaysSoRatherThanReturningNothing() {
        var a = service.assess(List.of());

        assertThat(a.assessable()).isFalse();
        assertThat(a.notAssessableReason()).contains("No growth measurements");
    }

    // --- data quality outranks interpretation -----------------------------------------------------

    @Test
    void anImplausibleJumpSuppressesTheFalteringSignalItWouldOtherwiseProduce() {
        // Without this, a mistyped weight generates a severe-faltering alert and a clinic spends
        // its morning investigating a transcription error as if it were disease.
        var a = service.assess(List.of(point(200, 7.4, -0.4), point(260, 2.0, -8.0)));

        assertThat(a.interpretationBlocked()).isTrue();
        assertThat(codes(a)).containsExactly("IMPLAUSIBLE_MEASUREMENT");
        assertThat(codes(a)).doesNotContain("SEVERE_WEIGHT_FALTERING", "WEIGHT_LOSS");
        assertThat(a.referralRequired()).isFalse();
        assertThat(a.note()).contains("measurements themselves are in question");
    }

    @Test
    void theLengthToHeightSwitchIsRecognisedRatherThanReadAsFaltering() {
        Map<String, Object> lying = new LinkedHashMap<>(point(700, 10.0, -0.5));
        lying.put("lengthHeightForAgeZ", -0.3);
        lying.put("statureMode", "LENGTH");
        Map<String, Object> standing = new LinkedHashMap<>(point(760, 10.2, -0.5));
        standing.put("lengthHeightForAgeZ", -1.1);
        standing.put("statureMode", "HEIGHT");

        var a = service.assess(List.of(lying, standing));

        assertThat(codes(a)).containsExactly("STATURE_MODE_ARTEFACT");
        assertThat(a.interpretationBlocked()).isTrue();
        assertThat(a.signals().get(0).action()).contains("Do not act on this apparent loss");
    }

    @Test
    void aStatureDropTooLargeForTheArtefactIsNotExcusedByIt() {
        // The artefact explains about 0.7 cm. A fall far beyond that is real and must not be
        // waved away just because the measurement method changed at the same visit.
        Map<String, Object> lying = new LinkedHashMap<>(point(700, 10.0, -0.5));
        lying.put("lengthHeightForAgeZ", 0.2);
        lying.put("statureMode", "LENGTH");
        Map<String, Object> standing = new LinkedHashMap<>(point(760, 10.2, -0.5));
        standing.put("lengthHeightForAgeZ", -2.6);
        standing.put("statureMode", "HEIGHT");

        var a = service.assess(List.of(lying, standing));

        assertThat(codes(a)).doesNotContain("STATURE_MODE_ARTEFACT");
        assertThat(a.interpretationBlocked()).isFalse();
    }

    // --- the clinical signals ------------------------------------------------------------------------

    @Test
    void aDownwardCrossingIsFalteringWithAnActionAttached() {
        var a = service.assess(List.of(point(200, 7.4, -0.4), point(260, 7.6, -1.3)));

        assertThat(codes(a)).contains("WEIGHT_FALTERING");
        var s = a.signals().stream().filter(x -> x.code().equals("WEIGHT_FALTERING")).findFirst().orElseThrow();
        assertThat(s.action()).contains("observing a feed");
        assertThat(s.zChange().doubleValue()).isLessThan(0);
        assertThat(a.anyConcern()).isTrue();
    }

    @Test
    void theWeightTrendYieldsOneSignalAtTheMostSevereApplicableLevel() {
        // This child satisfies severe faltering, plain faltering, weight loss and static weight
        // all at once. Reporting four would bury the one action that matters — refer — under
        // three weaker restatements of the same finding.
        var a = service.assess(List.of(point(200, 7.4, -0.4), point(260, 7.0, -1.9)));

        assertThat(codes(a)).containsExactly("SEVERE_WEIGHT_FALTERING");
        assertThat(a.referralRequired()).isTrue();
    }

    @Test
    void plainWeightLossWithoutACrossingStillSurfacesOnItsOwn() {
        // The collapse must not hide a finding that has no more severe sibling: a child who lost
        // weight without crossing a threshold is still a child who lost weight.
        var a = service.assess(List.of(point(200, 7.4, -0.4), point(260, 7.2, -0.6)));

        assertThat(codes(a)).containsExactly("WEIGHT_LOSS");
        assertThat(a.anyConcern()).isTrue();
    }

    @Test
    void steadyGrowthProducesNoSignalsAndSaysSo() {
        var a = service.assess(List.of(point(200, 7.4, -0.4), point(260, 8.0, -0.3)));

        assertThat(a.signals()).isEmpty();
        assertThat(a.anyConcern()).isFalse();
        assertThat(a.note()).contains("No growth concern");
    }

    @Test
    void recoveryIsReportedAsReassuringRatherThanBeingSilent() {
        // A clinician needs to know the plan is working, not just when it is not.
        var a = service.assess(List.of(point(200, 6.2, -2.6), point(260, 7.4, -1.7)));

        assertThat(codes(a)).contains("CATCH_UP_GROWTH");
        assertThat(a.anyConcern()).isFalse();
        assertThat(a.signals().get(0).severity()).isEqualTo("REASSURING");
    }

    @Test
    void aChildUnderTwoWhoHasNotGainedInTwoMonthsIsFlagged() {
        var a = service.assess(List.of(point(200, 7.4, -0.4), point(270, 7.4, -1.0)));

        assertThat(codes(a)).contains("STATIC_WEIGHT");
    }

    @Test
    void headCircumferenceCrossingIsFlaggedInEitherDirection() {
        Map<String, Object> a1 = new LinkedHashMap<>(point(100, 5.5, -0.2));
        a1.put("headCircumferenceForAgeZ", -0.2);
        Map<String, Object> a2 = new LinkedHashMap<>(point(160, 6.4, -0.1));
        a2.put("headCircumferenceForAgeZ", 1.0);

        var a = service.assess(List.of(a1, a2));

        assertThat(codes(a)).contains("HEAD_CIRCUMFERENCE_CROSSING");
    }

    // --- boundary and provenance ----------------------------------------------------------------------

    @Test
    void theEngineNeverRecomputesAScoreItWasGiven() {
        // The boundary that keeps a stored score and its interpretation from drifting apart: the
        // engine reads z-scores and has no way to derive one, so it cannot contradict pct-service.
        var a = service.assess(List.of(point(200, 7.4, -0.4), point(260, 7.6, -1.3)));

        // Weights are present but the signal is expressed against the score it was handed.
        assertThat(a.signals().get(0).zChange()).isEqualByComparingTo("-0.9");
    }

    @Test
    void everyAssessmentCitesItsContentVersionAndApprovalStatus() {
        var a = service.assess(List.of(point(200, 7.4, -0.4), point(260, 8.0, -0.3)));

        assertThat(a.contentVersion()).isEqualTo("engineering-seed-1.0.0");
        assertThat(a.approvalStatus()).isEqualTo("ENGINEERING_SEED");
        assertThat(a.contentSource()).contains("never recomputes them");
    }

    @Test
    void everySignalCarriesAnActionBecauseAFlagWithoutOneIsNoise() {
        service.content().signals().forEach(s ->
                assertThat(s.action()).as("%s has no action", s.code()).isNotBlank());
    }
}
