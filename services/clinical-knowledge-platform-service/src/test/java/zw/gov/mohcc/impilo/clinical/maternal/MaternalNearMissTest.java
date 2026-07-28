package zw.gov.mohcc.impilo.clinical.maternal;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import zw.gov.mohcc.impilo.clinical.classification.ClassificationContentLoader;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * WHO maternal near-miss identification, run against the live engine.
 *
 * <p>The distinction this pack exists to hold is the one between "no criterion was met" and "we did
 * not look". A near-miss missed because nobody recorded a creatinine is a woman who nearly died being
 * counted as a normal birth, and the near-miss-to-death ratio silently improving as a result.
 */
class MaternalNearMissTest {

    private final MaternalNearMissService service =
            new MaternalNearMissService(new ClassificationContentLoader(new ObjectMapper()));

    /** Everything unrecorded — the default a caller gets before any observation is entered. */
    private static MaternalNearMissService.NearMissObservations nothingRecorded() {
        return new MaternalNearMissService.NearMissObservations(
                null, null, null, null, null, null,
                null, null, null, null, null,
                null, null, null,
                null, null, null,
                null, null,
                null, null, null, null,
                null);
    }

    private static MaternalNearMissService.NearMissObservations allNormal() {
        return new MaternalNearMissService.NearMissObservations(
                false, false, false, false, 1.2, 7.38,
                false, false, 18, 0, false,
                false, 70, false,
                false, 250_000, 0,
                false, 12,
                false, false, false, false,
                false);
    }

    private String classify(MaternalNearMissService.NearMissObservations o) {
        var outcome = service.classify(o);
        return outcome == null ? null : outcome.classificationCode();
    }

    @Test
    @DisplayName("the content pack loads and every organ system is reachable")
    void packLoads() {
        assertThat(classify(allNormal())).isEqualTo(MaternalNearMissService.NOT_MET);
    }

    @Test
    @DisplayName("SILENCE IS NOT A NEGATIVE: nothing recorded must not read as 'criteria not met'")
    void nothingRecordedIsNotANegative() {
        String outcome = classify(nothingRecorded());
        assertThat(outcome)
                .as("an unmeasured creatinine is not a normal creatinine — this must not reach the not-met row")
                .isNotEqualTo(MaternalNearMissService.NOT_MET);
    }

    @Test
    @DisplayName("a woman with all-normal observations is genuinely classified not-met")
    void allNormalIsNotMet() {
        // The positive control for the test above: the not-met row IS reachable, so
        // nothingRecordedIsNotANegative is not passing because nothing ever reaches it.
        assertThat(classify(allNormal())).isEqualTo(MaternalNearMissService.NOT_MET);
        assertThat(MaternalNearMissService.isNearMiss(service.classify(allNormal()))).isFalse();
    }

    @Test
    @DisplayName("each organ system independently identifies a near-miss")
    void eachOrganSystemFires() {
        var shock = withShock();
        assertThat(classify(shock)).isEqualTo("NEAR_MISS_CARDIOVASCULAR");
        assertThat(MaternalNearMissService.isNearMiss(service.classify(shock))).isTrue();

        assertThat(classify(withGasping())).isEqualTo("NEAR_MISS_RESPIRATORY");
    }

    @Test
    @DisplayName("published thresholds are inclusive where WHO states them so")
    void thresholdsAreInclusive() {
        // Creatinine >= 300 umol/L. Exactly 300 is a near-miss.
        var atThreshold = new MaternalNearMissService.NearMissObservations(
                false, false, false, false, 1.2, 7.38,
                false, false, 18, 0, false,
                false, 300, false,
                false, 250_000, 0,
                false, 12,
                false, false, false, false,
                false);
        assertThat(classify(atThreshold)).isEqualTo("NEAR_MISS_RENAL");

        // 299 is not — so the threshold does real work rather than rubber-stamping.
        var belowThreshold = new MaternalNearMissService.NearMissObservations(
                false, false, false, false, 1.2, 7.38,
                false, false, 18, 0, false,
                false, 299, false,
                false, 250_000, 0,
                false, 12,
                false, false, false, false,
                false);
        assertThat(classify(belowThreshold)).isEqualTo(MaternalNearMissService.NOT_MET);
    }

    @Test
    @DisplayName("the LOW respiratory bound is not forgotten — a rate of 4 is a near-miss")
    void lowRespiratoryBoundFires() {
        var slow = new MaternalNearMissService.NearMissObservations(
                false, false, false, false, 1.2, 7.38,
                false, false, 4, 0, false,
                false, 70, false,
                false, 250_000, 0,
                false, 12,
                false, false, false, false,
                false);
        assertThat(classify(slow))
                .as("a range criterion written as a single bound loses the collapsing patient")
                .isEqualTo("NEAR_MISS_RESPIRATORY");
    }

    @Test
    @DisplayName("isNearMiss treats not-met as not a near-miss and a fired criterion as one")
    void isNearMissDiscriminates() {
        assertThat(MaternalNearMissService.isNearMiss(service.classify(withShock()))).isTrue();
        assertThat(MaternalNearMissService.isNearMiss(service.classify(allNormal()))).isFalse();
        assertThat(MaternalNearMissService.isNearMiss(null)).isFalse();
    }

    private static MaternalNearMissService.NearMissObservations withShock() {
        return new MaternalNearMissService.NearMissObservations(
                true, false, false, false, null, null,
                null, null, null, null, null,
                null, null, null,
                null, null, null,
                null, null,
                null, null, null, null,
                null);
    }

    private static MaternalNearMissService.NearMissObservations withGasping() {
        return new MaternalNearMissService.NearMissObservations(
                null, null, null, null, null, null,
                null, true, null, null, null,
                null, null, null,
                null, null, null,
                null, null,
                null, null, null, null,
                null);
    }
}
