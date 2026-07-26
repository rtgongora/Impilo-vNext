package zw.gov.mohcc.impilo.pct.core.clinical;

import org.junit.jupiter.api.Test;
import zw.gov.mohcc.impilo.pct.core.clinical.PartographProgressEngine.Assessment;
import zw.gov.mohcc.impilo.pct.core.clinical.PartographProgressEngine.Observation;
import zw.gov.mohcc.impilo.pct.core.clinical.PartographProgressEngine.ProgressStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The fixtures here are labours, not rows. Each one is a plausible presentation at a Zimbabwean
 * district facility, because a partograph engine tested on arbitrary numbers proves only that the
 * arithmetic runs.
 */
class PartographProgressEngineTest {

    private static final OffsetDateTime ADMISSION =
            OffsetDateTime.of(2026, 3, 14, 6, 0, 0, 0, ZoneOffset.ofHours(2));

    private static Observation obs(int minutesAfterAdmission, String dilationCm, Integer fhr) {
        return new Observation(ADMISSION.plusMinutes(minutesAfterAdmission),
                dilationCm == null ? null : new BigDecimal(dilationCm),
                fhr, fhr == null ? null : 88, fhr == null ? null : 118,
                fhr == null ? null : new BigDecimal("36.8"));
    }

    // --- the property that matters most -------------------------------------------------

    @Test
    void anEmptyPartographIsInsufficientDataAndNeverTheReassuringCase() {
        // A partograph with nothing on it means nobody has assessed this labour. If that renders
        // as "left of the alert line" the display has invented a reassurance no one recorded.
        Assessment a = PartographProgressEngine.assess(List.of(), ADMISSION.plusHours(3));

        assertThat(a.status()).isEqualTo(ProgressStatus.INSUFFICIENT_DATA);
        assertThat(a.status()).isNotEqualTo(ProgressStatus.LEFT_OF_ALERT);
        assertThat(a.observations()).anyMatch(s -> s.contains("No labour observations"));
    }

    @Test
    void observationsWithoutDilationCannotBePlottedAndSayWhy() {
        // The fetal heart is being listened to but nobody has examined her. Common when a busy
        // ward records the easy observations; the engine must not read it as progress.
        List<Observation> vitalsOnly = List.of(obs(0, null, 140), obs(30, null, 138), obs(60, null, 142));

        Assessment a = PartographProgressEngine.assess(vitalsOnly, ADMISSION.plusMinutes(70));

        assertThat(a.status()).isEqualTo(ProgressStatus.INSUFFICIENT_DATA);
        assertThat(a.observations()).anyMatch(s -> s.contains("dilation has never been recorded"));
        assertThat(a.recommendedAction()).contains("record cervical dilation");
    }

    // --- the alert and action lines ------------------------------------------------------

    @Test
    void aLabourAdvancingAtOneCentimetreAnHourSitsOnTheAlertLine() {
        // Textbook progress: 4 cm on admission, 8 cm four hours later.
        List<Observation> normal = List.of(
                obs(0, "4", 140), obs(120, "6", 138), obs(240, "8", 140));

        Assessment a = PartographProgressEngine.assess(normal, ADMISSION.plusMinutes(250));

        assertThat(a.status()).isEqualTo(ProgressStatus.LEFT_OF_ALERT);
        assertThat(a.expectedDilationCm()).isEqualByComparingTo("8");
        assertThat(a.hoursBehindAlertLine()).isEqualByComparingTo("0");
        assertThat(a.alertReferenceDilationCm()).isEqualByComparingTo("4");
    }

    @Test
    void aLabourTwoHoursBehindHasCrossedTheAlertLineButNotTheActionLine() {
        // 4 cm at 06:00, 5 cm at 09:00. The line expects 7 cm by then, so she is two hours behind:
        // over the alert line, but with hours in hand before the action line forces a decision.
        List<Observation> slow = List.of(
                obs(0, "4", 140), obs(120, "4.5", 136), obs(180, "5", 134));

        Assessment a = PartographProgressEngine.assess(slow, ADMISSION.plusMinutes(190));

        assertThat(a.status()).isEqualTo(ProgressStatus.BETWEEN_ALERT_AND_ACTION);
        assertThat(a.hoursBehindAlertLine()).isEqualByComparingTo("2.00");
        assertThat(a.recommendedAction()).contains("transfer");
    }

    @Test
    void sixHoursToAdvanceTwoCentimetresIsAlreadyPastTheActionLine() {
        // 4 cm at 06:00, 6 cm at 12:00. The alert line reached full dilation at 12:00, so she is
        // four hours behind it — the boundary case, and it must fall on the intervene side.
        List<Observation> slow = List.of(
                obs(0, "4", 140), obs(180, "5", 136), obs(360, "6", 134));

        Assessment a = PartographProgressEngine.assess(slow, ADMISSION.plusMinutes(370));

        assertThat(a.status()).isEqualTo(ProgressStatus.AT_OR_RIGHT_OF_ACTION);
        assertThat(a.hoursBehindAlertLine()).isEqualByComparingTo("4.00");
    }

    @Test
    void obstructedLabourCrossesTheActionLineAndDemandsADecision() {
        // Arrested at 5 cm for six hours — the classic obstructed labour a partograph exists to
        // catch before rupture.
        List<Observation> arrested = List.of(
                obs(0, "4", 140), obs(120, "5", 144), obs(360, "5", 150), obs(600, "5", 156));

        Assessment a = PartographProgressEngine.assess(arrested, ADMISSION.plusMinutes(610));

        assertThat(a.status()).isEqualTo(ProgressStatus.AT_OR_RIGHT_OF_ACTION);
        assertThat(a.hoursBehindAlertLine()).isGreaterThanOrEqualTo(new BigDecimal("4"));
        assertThat(a.recommendedAction()).contains("decision now");
    }

    @Test
    void theAlertLineStartsWhenActiveLabourIsRecognisedNotOnAdmission() {
        // Admitted in the latent phase at 2 cm and slow for five hours, then 4 cm. Measuring the
        // alert line from admission would condemn a normal latent phase as obstruction.
        List<Observation> latentThenActive = List.of(
                obs(0, "2", 140), obs(180, "3", 140), obs(300, "4", 138), obs(420, "6", 140));

        Assessment a = PartographProgressEngine.assess(latentThenActive, ADMISSION.plusMinutes(430));

        assertThat(a.alertReferenceDilationCm()).isEqualByComparingTo("4");
        assertThat(a.alertReferenceAt()).isEqualTo(ADMISSION.plusMinutes(300));
        // 4 cm at 11:00 → 6 cm at 13:00 is exactly on the line.
        assertThat(a.status()).isEqualTo(ProgressStatus.LEFT_OF_ALERT);
    }

    @Test
    void theLatentPhaseIsNotJudgedAgainstTheAlertLineAtAll() {
        List<Observation> latent = List.of(obs(0, "2", 140), obs(240, "3", 138));

        Assessment a = PartographProgressEngine.assess(latent, ADMISSION.plusMinutes(250));

        assertThat(a.status()).isEqualTo(ProgressStatus.INSUFFICIENT_DATA);
        assertThat(a.observations()).anyMatch(s -> s.contains("latent phase"));
        assertThat(a.recommendedAction()).contains("do not plot");
    }

    @Test
    void fullDilationEndsTheProgressQuestion() {
        List<Observation> secondStage = List.of(obs(0, "4", 140), obs(300, "10", 142));

        Assessment a = PartographProgressEngine.assess(secondStage, ADMISSION.plusMinutes(310));

        assertThat(a.status()).isEqualTo(ProgressStatus.SECOND_STAGE);
        assertThat(a.recommendedAction()).contains("five minutes");
    }

    // --- data quality ---------------------------------------------------------------------

    @Test
    void dilationGoingBackwardsIsFlaggedBecauseACervixDoesNotClose() {
        List<Observation> contradictory = List.of(
                obs(0, "4", 140), obs(120, "7", 140), obs(240, "6", 140));

        Assessment a = PartographProgressEngine.assess(contradictory, ADMISSION.plusMinutes(250));

        assertThat(a.observations()).anyMatch(s -> s.contains("does not reverse"));
    }

    @Test
    void observationsAreSortedSoOutOfOrderEntryDoesNotChangeTheVerdict() {
        List<Observation> ordered = List.of(obs(0, "4", 140), obs(120, "6", 138), obs(240, "8", 140));
        List<Observation> shuffled = List.of(obs(240, "8", 140), obs(0, "4", 140), obs(120, "6", 138));

        Assessment fromOrdered = PartographProgressEngine.assess(ordered, ADMISSION.plusMinutes(250));
        Assessment fromShuffled = PartographProgressEngine.assess(shuffled, ADMISSION.plusMinutes(250));

        assertThat(fromShuffled.status()).isEqualTo(fromOrdered.status());
        assertThat(fromShuffled.latestDilationCm()).isEqualByComparingTo(fromOrdered.latestDilationCm());
    }

    // --- outstanding observations ----------------------------------------------------------

    @Test
    void aFetalHeartUnheardForOverHalfAnHourIsReportedAsOutstanding() {
        List<Observation> stale = List.of(obs(0, "4", 140), obs(60, "5", 140));

        Assessment a = PartographProgressEngine.assess(stale, ADMISSION.plusMinutes(160));

        assertThat(a.outstandingObservations())
                .anyMatch(s -> s.startsWith("fetal heart rate last recorded 100 minutes ago"));
    }

    @Test
    void anObservationNeverTakenReadsAsNeverTakenNotAsOverdue() {
        // Dilation recorded, nothing else. "Never recorded" and "recorded but stale" are
        // different failures: one is an omission, the other a lapse.
        List<Observation> dilationOnly = List.of(
                new Observation(ADMISSION, new BigDecimal("4"), null, null, null, null));

        Assessment a = PartographProgressEngine.assess(dilationOnly, ADMISSION.plusMinutes(10));

        assertThat(a.outstandingObservations())
                .contains("fetal heart rate has never been recorded in this session")
                .contains("blood pressure has never been recorded in this session");
    }

    @Test
    void anUpToDatePartographHasNothingOutstanding() {
        List<Observation> current = List.of(obs(0, "4", 140), obs(20, "4", 138));

        Assessment a = PartographProgressEngine.assess(current, ADMISSION.plusMinutes(30));

        assertThat(a.outstandingObservations()).isEmpty();
    }

    @Test
    void everyAssessmentCitesItsGuidelineVersion() {
        // No recommendation without a source and a version.
        Assessment a = PartographProgressEngine.assess(List.of(obs(0, "4", 140)), ADMISSION.plusMinutes(10));

        assertThat(a.contentVersion()).isEqualTo("who-partograph-classic-1.0.0");
        assertThat(a.contentSource()).contains("alert line 1 cm/hour");
    }
}
