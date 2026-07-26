package zw.gov.mohcc.impilo.clinical.immunisation;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import zw.gov.mohcc.impilo.clinical.immunisation.EpiForecastEngine.AdministeredDose;
import zw.gov.mohcc.impilo.clinical.immunisation.EpiForecastEngine.DoseStatus;
import zw.gov.mohcc.impilo.clinical.immunisation.EpiForecastEngine.Forecast;
import zw.gov.mohcc.impilo.clinical.immunisation.EpiForecastEngine.ForecastRow;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The fixtures are vaccination histories, not rows: a child who came on time, one who came late,
 * one whose card shows a dose given too early, and one brought at four months for their first
 * rotavirus dose. Each is a real presentation at a Zimbabwean clinic.
 */
class EpiForecastEngineTest {

    private static final String CONTENT = "clinical/zw-epi-schedule.json";
    private static final LocalDate DOB = LocalDate.of(2026, 1, 5);

    private EpiSchedule schedule;

    @BeforeEach
    void setUp() {
        schedule = new EpiScheduleLoader(new ObjectMapper()).load(CONTENT);
    }

    private Forecast at(int ageDays, List<AdministeredDose> given) {
        return EpiForecastEngine.forecast(schedule, DOB, "FEMALE", given, DOB.plusDays(ageDays));
    }

    private static AdministeredDose dose(String series, int number, int ageDaysWhenGiven) {
        return new AdministeredDose(series, number, DOB.plusDays(ageDaysWhenGiven), "COMPLETED");
    }

    private ForecastRow row(Forecast f, String series, int doseNumber) {
        return f.rows().stream()
                .filter(r -> r.series().equals(series) && r.doseNumber() == doseNumber)
                .findFirst().orElseThrow(() -> new AssertionError("no row for " + series + " " + doseNumber));
    }

    /** Everything given exactly on the recommended day, up to the given age. */
    private static List<AdministeredDose> onSchedule(int throughAgeDays) {
        List<AdministeredDose> given = new ArrayList<>();
        if (throughAgeDays >= 0) {
            given.add(dose("BCG", 1, 0));
            given.add(dose("OPV", 0, 0));
        }
        for (int[] visit : new int[][]{{42, 1}, {70, 2}, {98, 3}}) {
            if (throughAgeDays >= visit[0]) {
                given.add(dose("OPV", visit[1], visit[0]));
                given.add(dose("PENTA", visit[1], visit[0]));
                given.add(dose("PCV", visit[1], visit[0]));
                if (visit[1] <= 2) {
                    given.add(dose("ROTA", visit[1], visit[0]));
                }
                if (visit[1] == 3) {
                    given.add(dose("IPV", 1, visit[0]));
                }
            }
        }
        return given;
    }

    // --- the schedule loads and is coherent -------------------------------------------------

    @Test
    void theScheduleLoadsWithItsGovernanceMetadata() {
        assertThat(schedule.antigens()).hasSize(8);
        assertThat(schedule.approvalStatus()).isEqualTo("ENGINEERING_SEED");
        assertThat(schedule.adaptationAuthority()).isEqualTo("PENDING_MOHCC_EPI_PROGRAMME_RATIFICATION");
        assertThat(schedule.provenance()).contains("NOT a ratified national schedule");
    }

    @Test
    void noDoseIsRecommendedBeforeItIsValid() {
        // A schedule that recommends a dose earlier than its own minimum age would generate
        // forecasts that invalidate the doses they prompt.
        schedule.antigens().forEach(a -> a.doses().forEach(d ->
                assertThat(d.recommendedAgeDays())
                        .as("%s dose %d recommended before its minimum age", a.series(), d.doseNumber())
                        .isGreaterThanOrEqualTo(d.minimumAgeDays())));
    }

    // --- the ordinary cases -------------------------------------------------------------------

    @Test
    void aNewbornIsDueBcgAndTheBirthPolioDose() {
        Forecast f = at(0, List.of());

        assertThat(row(f, "BCG", 1).status()).isEqualTo(DoseStatus.DUE);
        assertThat(row(f, "OPV", 0).status()).isEqualTo(DoseStatus.DUE);
        assertThat(row(f, "PENTA", 1).status()).isEqualTo(DoseStatus.NOT_YET_DUE);
        assertThat(f.anyOverdue()).isFalse();
    }

    @Test
    void aSixWeekOldWithABirthVisitIsDueTheFirstPrimaryVisit() {
        Forecast f = at(42, onSchedule(0));

        assertThat(row(f, "PENTA", 1).status()).isEqualTo(DoseStatus.DUE);
        assertThat(row(f, "PCV", 1).status()).isEqualTo(DoseStatus.DUE);
        assertThat(row(f, "ROTA", 1).status()).isEqualTo(DoseStatus.DUE);
        assertThat(row(f, "BCG", 1).status()).isEqualTo(DoseStatus.GIVEN);
        assertThat(f.dueNow()).extracting(ForecastRow::series)
                .contains("OPV", "PENTA", "PCV", "ROTA");
    }

    @Test
    void aFullyVaccinatedInfantHasNothingDue() {
        Forecast f = at(100, onSchedule(98));

        assertThat(f.dueNow()).isEmpty();
        assertThat(f.anyOverdue()).isFalse();
        assertThat(row(f, "MR", 1).status()).isEqualTo(DoseStatus.NOT_YET_DUE);
    }

    // --- the interval gate, which is the one most often missed ---------------------------------

    @Test
    void aChildWhoJustReceivedDoseOneIsNotDueDoseTwoTomorrowEvenIfOldEnough() {
        // The heart of it. A child who defaulted and arrives at 12 weeks gets Penta-1 today. They
        // are already old enough for Penta-2 by age, but the dose would not immunise for another
        // four weeks. A forecast that said "due" here would cause a wasted dose and a child who
        // is recorded as protected and is not.
        List<AdministeredDose> given = new ArrayList<>(onSchedule(0));
        given.add(dose("PENTA", 1, 84));

        Forecast f = at(85, given);
        ForecastRow penta2 = row(f, "PENTA", 2);

        assertThat(penta2.status()).isEqualTo(DoseStatus.BLOCKED_BY_INTERVAL);
        assertThat(penta2.earliestDate()).isEqualTo(DOB.plusDays(84 + 28));
        assertThat(penta2.rationale()).contains("would not immunise");
    }

    @Test
    void theIntervalGateOpensExactlyOnTheMinimumInterval() {
        List<AdministeredDose> given = new ArrayList<>(onSchedule(0));
        given.add(dose("PENTA", 1, 84));

        assertThat(row(at(84 + 27, given), "PENTA", 2).status()).isEqualTo(DoseStatus.BLOCKED_BY_INTERVAL);
        assertThat(row(at(84 + 28, given), "PENTA", 2).status()).isEqualTo(DoseStatus.OVERDUE);
    }

    @Test
    void aDoseTooYoungToBeValidIsNotBlockedByIntervalButSimplyNotYetDue() {
        // Distinguishing the two matters: one is "come back in three weeks", the other is
        // "this child is not old enough yet".
        Forecast f = at(20, onSchedule(0));

        assertThat(row(f, "PENTA", 1).status()).isEqualTo(DoseStatus.NOT_YET_DUE);
    }

    // --- invalid doses ---------------------------------------------------------------------------

    @Test
    void aDoseGivenBeforeTheMinimumAgeMustBeRepeated() {
        // Happens when a busy clinic gives the six-week visit at four weeks.
        List<AdministeredDose> given = new ArrayList<>(onSchedule(0));
        given.add(dose("PENTA", 1, 28));

        ForecastRow penta1 = row(at(50, given), "PENTA", 1);

        assertThat(penta1.status()).isEqualTo(DoseStatus.INVALID_REPEAT_REQUIRED);
        assertThat(penta1.rationale()).contains("does not immunise").contains("repeated");
        assertThat(penta1.actionableToday()).isTrue();
    }

    @Test
    void anInvalidDoseDoesNotStartTheClockForTheNextDose() {
        // If an invalid Penta-1 advanced the series, Penta-2 would be forecast from a dose that
        // never counted, and the child would end up with two invalid doses instead of one.
        List<AdministeredDose> given = new ArrayList<>(onSchedule(0));
        given.add(dose("PENTA", 1, 28));

        // Evaluated at 80 days, when the child IS old enough for dose 2. At 60 days the answer
        // would be "not yet due" for reasons of age alone, and the test could not tell whether the
        // invalid first dose had been correctly ignored.
        Forecast f = at(80, given);

        assertThat(row(f, "PENTA", 1).status()).isEqualTo(DoseStatus.INVALID_REPEAT_REQUIRED);
        assertThat(row(f, "PENTA", 2).status()).isEqualTo(DoseStatus.AWAITING_PREVIOUS_DOSE);
    }

    @Test
    void aDoseGivenTooSoonAfterThePreviousOneMustBeRepeated() {
        // The first dose is late, so by the time the second is given the child is well past its
        // minimum age. Only the interval is violated — which is what this test needs to isolate,
        // because a fixture that breaks the age rule too would pass for the wrong reason.
        List<AdministeredDose> given = new ArrayList<>(onSchedule(0));
        given.add(dose("PENTA", 1, 80));
        given.add(dose("PENTA", 2, 95));   // old enough, but only 15 days later

        ForecastRow penta2 = row(at(110, given), "PENTA", 2);

        assertThat(penta2.status()).isEqualTo(DoseStatus.INVALID_REPEAT_REQUIRED);
        assertThat(penta2.rationale()).contains("15 days after the previous dose");
        assertThat(penta2.rationale()).doesNotContain("before the minimum age");
    }

    @Test
    void aDoseYearsAwayReadsAsNotYetDueRatherThanAwaitingItsPredecessor() {
        // Noticed on the estate: an 85-day-old's card listed HPV dose 2 as "awaiting the previous
        // dose". True, and useless — it is nine years away, and rows like it buried the three
        // doses actually due that morning.
        ForecastRow hpv2 = row(at(85, onSchedule(0)), "HPV", 2);

        assertThat(hpv2.status()).isEqualTo(DoseStatus.NOT_YET_DUE);
        assertThat(hpv2.rationale()).contains("eligible from");
    }

    // --- the rotavirus ceiling, which is a safety limit -------------------------------------------

    @Test
    void rotavirusIsNotStartedAfterFifteenWeeksEvenThoughTheChildIsUnvaccinated() {
        // A four-month-old who has never been vaccinated is due everything else and must NOT be
        // given a first rotavirus dose. The excess intussusception risk rises with age at first
        // dose, so the window closing is a safety decision, not an administrative one.
        Forecast f = at(120, List.of());
        ForecastRow rota1 = row(f, "ROTA", 1);

        assertThat(rota1.status()).isEqualTo(DoseStatus.AGED_OUT);
        assertThat(rota1.rationale()).contains("intussusception");
        assertThat(f.dueNow()).extracting(ForecastRow::series).doesNotContain("ROTA");
        // Everything else is still catchable.
        assertThat(row(f, "PENTA", 1).status()).isEqualTo(DoseStatus.OVERDUE);
    }

    @Test
    void anIncompleteRotavirusCourseIsClosedRatherThanCaughtUp() {
        List<AdministeredDose> given = new ArrayList<>(onSchedule(0));
        given.add(dose("ROTA", 1, 42));

        assertThat(row(at(240, given), "ROTA", 2).status()).isEqualTo(DoseStatus.AGED_OUT);
    }

    @Test
    void theBirthPolioDoseIsNotGivenLate() {
        assertThat(row(at(30, List.of()), "OPV", 0).status()).isEqualTo(DoseStatus.AGED_OUT);
        // But the primary series is unaffected and still catchable: due at six weeks, and only
        // overdue once its own window (10 weeks) has passed — the birth dose has no bearing on it.
        assertThat(row(at(60, List.of()), "OPV", 1).status()).isEqualTo(DoseStatus.DUE);
        assertThat(row(at(80, List.of()), "OPV", 1).status()).isEqualTo(DoseStatus.OVERDUE);
    }

    @Test
    void aMissedBirthDoseDoesNotStrandThePrimarySeries() {
        // Caught by this suite, and it would have affected every baby not born in a facility.
        // Treating "dose 1 comes after dose 0" as a dependency stranded the whole polio series
        // behind a birth dose that can no longer be given. Dependence is the minimum interval,
        // not the dose number.
        Forecast f = at(60, List.of());

        assertThat(row(f, "OPV", 0).status()).isEqualTo(DoseStatus.AGED_OUT);
        assertThat(row(f, "OPV", 1).status()).isEqualTo(DoseStatus.DUE);
        assertThat(f.dueNow()).extracting(ForecastRow::series).contains("OPV");
    }

    // --- defaulters ---------------------------------------------------------------------------------

    @Test
    void aDefaulterIsCaughtUpWithoutRestartingTheSeries() {
        // The commonest real error is restarting a series after a long gap. The interval matters;
        // the gap does not.
        List<AdministeredDose> given = new ArrayList<>(onSchedule(0));
        given.add(dose("PENTA", 1, 42));

        Forecast f = at(400, given);
        ForecastRow penta2 = row(f, "PENTA", 2);

        assertThat(penta2.status()).isEqualTo(DoseStatus.OVERDUE);
        assertThat(penta2.daysOverdue()).isEqualTo(400 - 98);
        assertThat(penta2.rationale()).contains("no need to restart");
        assertThat(row(f, "PENTA", 1).status()).isEqualTo(DoseStatus.GIVEN);
    }

    @Test
    void overdueDosesAreFlaggedOnTheForecastAsAWhole() {
        Forecast f = at(200, onSchedule(0));

        assertThat(f.anyOverdue()).isTrue();
        assertThat(f.dueNow()).isNotEmpty();
    }

    // --- honesty about the source of a dose ------------------------------------------------------

    @Test
    void anUnverifiedReportedDoseCountsButMarksTheForecastProvisional() {
        // Counting it avoids re-vaccinating a child unnecessarily; flagging it avoids recording a
        // child as protected on the strength of an unchecked card.
        List<AdministeredDose> given = new ArrayList<>(onSchedule(0));
        given.add(new AdministeredDose("PENTA", 1, DOB.plusDays(42),
                "RECORDED_ELSEWHERE_PENDING_VERIFICATION"));

        Forecast f = at(75, given);

        assertThat(row(f, "PENTA", 1).status()).isEqualTo(DoseStatus.GIVEN_UNVERIFIED);
        assertThat(f.provisional()).isTrue();
        assertThat(f.provisionalReasons()).anyMatch(r -> r.contains("not been verified"));
        assertThat(f.note()).contains("Verify them");
        // It still advances the series, so dose 2 is forecast normally.
        assertThat(row(f, "PENTA", 2).status()).isEqualTo(DoseStatus.DUE);
    }

    @Test
    void aContraindicatedDoseIsNotForecastAsDue() {
        List<AdministeredDose> given = new ArrayList<>(onSchedule(0));
        given.add(new AdministeredDose("PENTA", 1, DOB.plusDays(42), "NOT_DONE_CONTRAINDICATED"));

        ForecastRow penta1 = row(at(80, given), "PENTA", 1);

        assertThat(penta1.status()).isEqualTo(DoseStatus.CONTRAINDICATED);
        assertThat(penta1.actionableToday()).isFalse();
        assertThat(penta1.rationale()).contains("needs review, not a reminder");
    }

    @Test
    void aDoseEnteredInErrorIsIgnoredEntirely() {
        List<AdministeredDose> given = new ArrayList<>(onSchedule(0));
        given.add(new AdministeredDose("PENTA", 1, DOB.plusDays(42), "ENTERED_IN_ERROR"));

        assertThat(row(at(80, given), "PENTA", 1).status()).isEqualTo(DoseStatus.OVERDUE);
    }

    // --- sex-scoped antigens -------------------------------------------------------------------------

    @Test
    void anAntigenScheduledForGirlsIsNotForecastForABoy() {
        Forecast f = EpiForecastEngine.forecast(schedule, DOB, "MALE", List.of(), DOB.plusDays(3800));

        assertThat(row(f, "HPV", 1).status()).isEqualTo(DoseStatus.NOT_APPLICABLE);
    }

    @Test
    void anUnrecordedSexIsReportedRatherThanGuessed() {
        // Silently dropping the row would leave a girl's HPV doses invisible on her card.
        Forecast f = EpiForecastEngine.forecast(schedule, DOB, null, List.of(), DOB.plusDays(3800));
        ForecastRow hpv = row(f, "HPV", 1);

        assertThat(hpv.status()).isEqualTo(DoseStatus.NOT_APPLICABLE);
        assertThat(hpv.rationale()).contains("sex is not recorded");
    }

    @Test
    void aTeenagerIsDueHpvAndNothingFromInfancyIsStillOpen() {
        Forecast f = at(3700, onSchedule(98));

        assertThat(row(f, "HPV", 1).status()).isIn(DoseStatus.DUE, DoseStatus.OVERDUE);
        assertThat(row(f, "ROTA", 1).status()).isEqualTo(DoseStatus.GIVEN);
    }

    // --- reproducibility ---------------------------------------------------------------------------

    @Test
    void theForecastIsAFunctionOfItsInputsSoAPastCardCanBeReproduced() {
        List<AdministeredDose> given = onSchedule(42);

        Forecast first = at(60, given);
        Forecast second = at(60, given);

        assertThat(second.rows()).usingRecursiveComparison().isEqualTo(first.rows());
        assertThat(first.asOf()).isEqualTo(DOB.plusDays(60));
        assertThat(first.scheduleVersion()).isEqualTo("engineering-seed-1.0.0");
    }
}
