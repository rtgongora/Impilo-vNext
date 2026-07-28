package zw.gov.mohcc.impilo.pct.core.clinical;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerExecutionChain;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import zw.gov.mohcc.impilo.pct.persistence.entity.FunctionalAssessmentEntity;
import zw.gov.mohcc.impilo.pct.persistence.entity.MedicalEpisodeEntity;
import zw.gov.mohcc.impilo.pct.persistence.entity.MedicationReconciliationEntity;
import zw.gov.mohcc.impilo.pct.persistence.entity.MedicationReconciliationItemEntity;
import zw.gov.mohcc.impilo.pct.persistence.entity.ProblemEntity;
import zw.gov.mohcc.impilo.pct.persistence.entity.ProblemLinkEntity;
import zw.gov.mohcc.impilo.pct.persistence.entity.ProgrammeEnrolmentEntity;
import zw.gov.mohcc.impilo.pct.persistence.entity.TreatmentRegimenEntity;
import zw.gov.mohcc.impilo.pct.persistence.repository.FunctionalAssessmentRepository;
import zw.gov.mohcc.impilo.pct.persistence.repository.MedicalEpisodeRepository;
import zw.gov.mohcc.impilo.pct.persistence.repository.MedicationReconciliationItemRepository;
import zw.gov.mohcc.impilo.pct.persistence.repository.MedicationReconciliationRepository;
import zw.gov.mohcc.impilo.pct.persistence.repository.ProblemLinkRepository;
import zw.gov.mohcc.impilo.pct.persistence.repository.ProblemRepository;
import zw.gov.mohcc.impilo.pct.persistence.repository.ProgrammeEnrolmentRepository;
import zw.gov.mohcc.impilo.pct.persistence.repository.TreatmentRegimenRepository;
import zw.gov.mohcc.impilo.shared.auth.AccessMode;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The §21 analytics indicators, executed against a real persistence context.
 *
 * <p><strong>Named {@code *Test}, not {@code *IT}, deliberately.</strong> Surefire in this repo skips
 * {@code *IT} classes and failsafe is not wired for them, so an integration-shaped test with that
 * suffix never runs — a whole class of tests in this estate has been silently green by never
 * executing. These queries need a database to prove anything at all, so they run under surefire.</p>
 *
 * <p>Half the value is that each JPQL statement parses and executes. The other half is the safety
 * properties: a count states its unit, an empty denominator produces no rate, and an absent value
 * (never assessed, no review date, no onset date, unscored) is its own number rather than being
 * folded into a neighbouring one that flatters or condemns the service.</p>
 *
 * <p>{@code @Transactional} so each method rolls back; without it the saves accumulate across methods
 * and every count is wrong in a way that looks like a query defect.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class MedicineAnalyticsTest {

    private static final UUID TENANT = UUID.fromString("00000000-0000-4000-8000-0000000000a1");
    private static final UUID OTHER_TENANT = UUID.fromString("00000000-0000-4000-8000-0000000000a2");

    private static final LocalDate TODAY = LocalDate.now();
    private static final LocalDate WINDOW_FROM = TODAY.minusDays(30);

    @Autowired private MedicineAnalyticsService analytics;
    @Autowired private ProgrammeEnrolmentService programmes;
    @Autowired private ProblemRepository problemRepository;
    @Autowired private ProblemLinkRepository problemLinkRepository;
    @Autowired private MedicalEpisodeRepository episodeRepository;
    @Autowired private MedicationReconciliationRepository reconciliationRepository;
    @Autowired private MedicationReconciliationItemRepository reconciliationItemRepository;
    @Autowired private FunctionalAssessmentRepository functionalAssessmentRepository;
    @Autowired private ProgrammeEnrolmentRepository enrolmentRepository;
    @Autowired private TreatmentRegimenRepository regimenRepository;
    // Qualified: actuator contributes a second RequestMappingHandlerMapping and the routing proof
    // must interrogate the MVC one, not the endpoint one.
    @Autowired @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping handlerMapping;

    @BeforeEach
    void setUp() {
        TrustContextHolder.set(new TrustContext(TENANT, "clinician-1", "PROVIDER", "TREATMENT",
                null, UUID.randomUUID(), null, null, null, AccessMode.INTERNAL));
    }

    @AfterEach
    void tearDown() {
        TrustContextHolder.clear();
    }

    // ── §21 disease detection ────────────────────────────────────────────────────

    @Test
    void detectionRunsAndKeepsSuspicionApartFromConfirmation() {
        problem(TENANT, "cpid-1", "I10", "CONFIRMED", null, null);
        problem(TENANT, "cpid-2", "I10", "CONFIRMED", null, null);
        problem(TENANT, "cpid-3", "I10", "SUSPECTED", null, null);

        Map<String, Object> out = analytics.detection("DIAGNOSIS", WINDOW_FROM, TODAY);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> counts = (List<Map<String, Object>>) out.get("counts");
        // Two rows for one code: collapsing them would count a suspicion as a diagnosis.
        assertThat(counts).hasSize(2);
        assertThat(out.get("problems_recorded_total")).isEqualTo(3L);
    }

    @Test
    void detectionRowsAreProblemsAndTheHeadcountIsSeparate() {
        problem(TENANT, "cpid-1", "I10", "CONFIRMED", null, null);
        problem(TENANT, "cpid-1", "E11", "CONFIRMED", null, null);

        Map<String, Object> out = analytics.detection(null, WINDOW_FROM, TODAY);

        assertThat(out.get("problems_recorded_total")).isEqualTo(2L);
        assertThat(out.get("distinct_people")).isEqualTo(1L);
        assertThat((String) out.get("counts_problem_records_not_people")).contains("PROBLEM RECORDS");
    }

    @Test
    void anotherTenantsProblemsNeverEnterTheDetectionCount() {
        problem(TENANT, "cpid-1", "I10", "CONFIRMED", null, null);
        problem(OTHER_TENANT, "cpid-9", "I10", "CONFIRMED", null, null);

        assertThat(analytics.detection(null, WINDOW_FROM, TODAY).get("problems_recorded_total"))
                .isEqualTo(1L);
    }

    @Test
    void aProblemOutsideTheWindowIsNotADetectionInsideIt() {
        problem(TENANT, "cpid-1", "I10", "CONFIRMED", null, null);

        // Recorded today, asked for last year: the window bounds the answer rather than the table.
        assertThat(analytics.detection(null, TODAY.minusDays(400), TODAY.minusDays(300))
                .get("problems_recorded_total")).isEqualTo(0L);
        assertThat(analytics.detection(null, WINDOW_FROM, TODAY).get("problems_recorded_total"))
                .isEqualTo(1L);
    }

    // ── §21 diagnostic delays ────────────────────────────────────────────────────

    @Test
    void theDiagnosticIntervalReportsPercentilesAndCountsWhatItCouldNotMeasure() {
        // Recorded now, so the interval is TODAY minus the onset: 10, 20 and 100 days.
        problem(TENANT, "cpid-1", "E11", "CONFIRMED", TODAY.minusDays(10), null);
        problem(TENANT, "cpid-2", "E11", "CONFIRMED", TODAY.minusDays(20), null);
        problem(TENANT, "cpid-3", "E11", "CONFIRMED", TODAY.minusDays(100), null);
        // Confirmed, but with no onset date — excluded from the statistic and reported as excluded.
        problem(TENANT, "cpid-4", "E11", "CONFIRMED", null, null);
        problem(TENANT, "cpid-5", "E11", "CONFIRMED", null, null);

        Map<String, Object> out = analytics.diagnosticInterval(WINDOW_FROM, TODAY);

        assertThat(out.get("measured_diagnoses")).isEqualTo(3L);
        assertThat(out.get("excluded_no_onset_date")).isEqualTo(2L);
        assertThat(out.get("median_days")).isEqualTo(20L);
        assertThat(out.get("p90_days")).isEqualTo(100L);
    }

    @Test
    void anOnsetRecordedAfterTheDiagnosisIsNotANegativeDelay() {
        // Onset two days from now on a diagnosis recorded today: a data error, not a negative delay.
        problem(TENANT, "cpid-1", "E11", "CONFIRMED", TODAY.plusDays(2), null);

        Map<String, Object> out = analytics.diagnosticInterval(WINDOW_FROM, TODAY);

        assertThat(out.get("excluded_onset_after_record")).isEqualTo(1L);
        assertThat(out.get("measured_diagnoses")).isEqualTo(0L);
        assertThat(out.get("median_days")).isNull();
    }

    @Test
    void anEmptyDiagnosticWindowHasNoMedianRatherThanAMedianOfZero() {
        Map<String, Object> out = analytics.diagnosticInterval(WINDOW_FROM, TODAY);

        assertThat(out.get("median_days")).isNull();
        assertThat(out.get("p90_days")).isNull();
        assertThat((String) out.get("statistics_undefined_reason")).contains("not a delay of zero days");
    }

    @Test
    void aWindowThatStartsAfterItEndsIsRefusedRatherThanReturningNothing() {
        // An inverted window silently returns an empty result set, which reads as "nothing happened".
        assertThatThrownBy(() -> analytics.detection(null, TODAY, TODAY.minusDays(10)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("after it ends");
    }

    // ── §21 complications ────────────────────────────────────────────────────────

    @Test
    void complicationsRunAndAreGroupedByUnderlyingConditionAndComplication() {
        ProblemEntity diabetes = problem(TENANT, "cpid-1", "E11", "CONFIRMED", null, null);
        ProblemEntity ulcer = problem(TENANT, "cpid-1", "L97", "CONFIRMED", null, null);
        ProblemEntity nephropathy = problem(TENANT, "cpid-1", "N08", "CONFIRMED", null, null);
        complicationLink(TENANT, ulcer.getProblemId(), diabetes.getProblemId(), daysAgo(7));
        complicationLink(TENANT, nephropathy.getProblemId(), diabetes.getProblemId(), daysAgo(7));

        Map<String, Object> out = analytics.complications(WINDOW_FROM, TODAY);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> counts = (List<Map<String, Object>>) out.get("counts");
        assertThat(counts).hasSize(2);
        assertThat(out.get("links_recorded_total")).isEqualTo(2L);
        assertThat((String) out.get("measures_documentation_not_incidence"))
                .contains("An unlinked complication is invisible here");
    }

    @Test
    void aComplicationPointingAtSomethingOtherThanAProblemIsCountedNotDropped() {
        ProblemLinkEntity l = new ProblemLinkEntity();
        l.setTenantId(TENANT);
        l.setProblemId(UUID.randomUUID());
        l.setLinkType("COMPLICATION_OF");
        l.setTargetType("PROCEDURE");
        l.setTargetRef("procedure/abc");
        l.setRecordedBy("test");
        l.setCreatedAt(daysAgo(4));
        problemLinkRepository.save(l);

        Map<String, Object> out = analytics.complications(WINDOW_FROM, TODAY);

        assertThat(out.get("links_with_non_problem_target")).isEqualTo(1L);
        assertThat(out.get("excluded_note")).isNotNull();
    }

    // ── §21 follow-up (the due half) ─────────────────────────────────────────────

    @Test
    void theReviewStateKeepsNeverSetApartFromOnSchedule() {
        problem(TENANT, "cpid-1", "I10", "CONFIRMED", null, TODAY.minusDays(10)); // overdue
        problem(TENANT, "cpid-2", "I10", "CONFIRMED", null, TODAY);               // due today
        problem(TENANT, "cpid-3", "I10", "CONFIRMED", null, TODAY.plusDays(30));  // scheduled
        problem(TENANT, "cpid-4", "I10", "CONFIRMED", null, null);                // never set

        Map<String, Object> out = analytics.reviewState(TODAY);

        @SuppressWarnings("unchecked")
        Map<String, Long> counts = (Map<String, Long>) out.get("counts");
        assertThat(counts).containsEntry("OVERDUE", 1L)
                .containsEntry("DUE_TODAY", 1L)
                .containsEntry("SCHEDULED", 1L)
                .containsEntry("NO_REVIEW_DATE", 1L);
        assertThat(out.get("open_problems_total")).isEqualTo(4L);

        @SuppressWarnings("unchecked")
        Map<String, Object> overdue = (Map<String, Object>) out.get("overdue_among_dated");
        // The problem nobody set a review date for is NOT in the denominator: it is neither overdue
        // nor compliant, and counting it as compliant is how this indicator comes out green on a
        // service following nobody up.
        assertThat(overdue.get("numerator")).isEqualTo(1L);
        assertThat(overdue.get("denominator")).isEqualTo(3L);
    }

    @Test
    void anOverdueRateOverNoReviewDatesAtAllIsUndefinedRatherThanZeroPercent() {
        problem(TENANT, "cpid-1", "I10", "CONFIRMED", null, null);
        problem(TENANT, "cpid-2", "I10", "CONFIRMED", null, null);

        @SuppressWarnings("unchecked")
        Map<String, Object> overdue =
                (Map<String, Object>) analytics.reviewState(TODAY).get("overdue_among_dated");

        assertThat(overdue.get("denominator")).isEqualTo(0L);
        assertThat(overdue.get("rate")).isNull();
        assertThat((String) overdue.get("rate_undefined_reason"))
                .contains("not 0%").contains("not 100%");
    }

    @Test
    void aResolvedProblemIsNotOnTheOpenReviewList() {
        ProblemEntity resolved = problem(TENANT, "cpid-1", "I10", "CONFIRMED", null,
                TODAY.minusDays(30));
        resolved.setClinicalStatus("RESOLVED");
        problemRepository.save(resolved);

        assertThat(analytics.reviewState(TODAY).get("open_problems_total")).isEqualTo(0L);
    }

    // ── §21 polypharmacy ─────────────────────────────────────────────────────────

    @Test
    void polypharmacyKeepsEmptyReconciliationsInTheDenominator() {
        UUID busy = reconciliation(TENANT, "cpid-1", "COMPLETED", daysAgo(4));
        for (int i = 0; i < 6; i++) {
            reconciliationItem(TENANT, busy, "drug-" + i);
        }
        // Completed, and the patient is on nothing. It produces no row in the item group-by, so
        // leaving it out would shrink the denominator and inflate the polypharmacy rate.
        reconciliation(TENANT, "cpid-2", "COMPLETED", daysAgo(4));

        Map<String, Object> out = analytics.polypharmacy(WINDOW_FROM, TODAY, null);

        assertThat(out.get("completed_reconciliations")).isEqualTo(2L);
        assertThat(out.get("reconciliations_with_zero_items")).isEqualTo(1L);

        @SuppressWarnings("unchecked")
        Map<String, Object> rate = (Map<String, Object>) out.get("at_or_above_threshold");
        assertThat(rate.get("numerator")).isEqualTo(1L);
        assertThat(rate.get("denominator")).isEqualTo(2L);
    }

    @Test
    void anAbandonedReconciliationIsNotAPatientOnFewerMedicines() {
        UUID abandoned = reconciliation(TENANT, "cpid-1", "ABANDONED", daysAgo(4));
        for (int i = 0; i < 9; i++) {
            reconciliationItem(TENANT, abandoned, "drug-" + i);
        }

        Map<String, Object> out = analytics.polypharmacy(WINDOW_FROM, TODAY, null);

        assertThat(out.get("completed_reconciliations")).isEqualTo(0L);
        @SuppressWarnings("unchecked")
        Map<String, Object> rate = (Map<String, Object>) out.get("at_or_above_threshold");
        assertThat(rate.get("rate")).isNull();
    }

    @Test
    void polypharmacyCountsReconciliationsAndSaysSoWhileTheHeadcountStaysSeparate() {
        UUID admission = reconciliation(TENANT, "cpid-1", "COMPLETED", daysAgo(6));
        UUID discharge = reconciliation(TENANT, "cpid-1", "COMPLETED", daysAgo(2));
        for (int i = 0; i < 5; i++) {
            reconciliationItem(TENANT, admission, "drug-" + i);
            reconciliationItem(TENANT, discharge, "drug-" + i);
        }

        Map<String, Object> out = analytics.polypharmacy(WINDOW_FROM, TODAY, null);

        assertThat(out.get("completed_reconciliations")).isEqualTo(2L);
        assertThat(out.get("distinct_people")).isEqualTo(1L);
        assertThat((String) out.get("counts_reconciliations_not_people")).contains("counted twice");
    }

    // ── §21 palliative access and episode-level mortality ────────────────────────

    @Test
    void palliativeReachCountsPeopleWhileTheMixCountsEpisodes() {
        episode(TENANT, "cpid-1", "PALLIATIVE", "ACTIVE", TODAY.minusDays(10), null, null);
        episode(TENANT, "cpid-1", "PALLIATIVE", "FINISHED", TODAY.minusDays(5), TODAY.minusDays(1), "DIED");
        episode(TENANT, "cpid-2", "ACUTE_ILLNESS", "ACTIVE", TODAY.minusDays(3), null, null);

        Map<String, Object> out = analytics.episodes(null, WINDOW_FROM, TODAY);

        assertThat(out.get("episodes_opened_total")).isEqualTo(3L);
        // Two palliative episodes, one person reached. Counting episodes would make a service that
        // re-opens them look like a service reaching more patients.
        assertThat(out.get("people_with_palliative_episode")).isEqualTo(1L);
        assertThat(out.get("people_with_any_episode")).isEqualTo(2L);

        @SuppressWarnings("unchecked")
        Map<String, Object> reach = (Map<String, Object>) out.get("palliative_reach");
        assertThat(reach.get("numerator")).isEqualTo(1L);
        assertThat(reach.get("denominator")).isEqualTo(2L);
        assertThat(reach.get("rate")).isEqualTo(0.5);
    }

    @Test
    void deathIsReadFromTheEndReasonBecauseTheStatusCannotCarryIt() {
        // Both are FINISHED. An outcome indicator over status alone cannot tell a cure from a death.
        episode(TENANT, "cpid-1", "CHRONIC_DISEASE", "FINISHED", TODAY.minusDays(20), TODAY.minusDays(2), "DIED");
        episode(TENANT, "cpid-2", "CHRONIC_DISEASE", "FINISHED", TODAY.minusDays(20), TODAY.minusDays(2), "COMPLETED");

        Map<String, Object> out = analytics.episodes(null, WINDOW_FROM, TODAY);

        @SuppressWarnings("unchecked")
        Map<String, Object> died = (Map<String, Object>) out.get("died_among_ended");
        assertThat(died.get("numerator")).isEqualTo(1L);
        assertThat(died.get("denominator")).isEqualTo(2L);
        assertThat((String) out.get("mortality_here_is_episode_level_only")).contains("not a mortality rate");
    }

    @Test
    void palliativeReachOverNobodyIsUndefinedRatherThanZeroPercent() {
        @SuppressWarnings("unchecked")
        Map<String, Object> reach =
                (Map<String, Object>) analytics.episodes(null, WINDOW_FROM, TODAY).get("palliative_reach");

        assertThat(reach.get("denominator")).isEqualTo(0L);
        assertThat(reach.get("rate")).isNull();
    }

    @Test
    void anotherFacilitysEpisodesStayOutOfAFacilityScopedRead() {
        episode(TENANT, "cpid-1", "PALLIATIVE", "ACTIVE", TODAY.minusDays(4), null, null, "fac-1");
        episode(TENANT, "cpid-2", "PALLIATIVE", "ACTIVE", TODAY.minusDays(4), null, null, "fac-2");

        assertThat(analytics.episodes("fac-1", WINDOW_FROM, TODAY).get("episodes_opened_total"))
                .isEqualTo(1L);
        assertThat(analytics.episodes(null, WINDOW_FROM, TODAY).get("episodes_opened_total"))
                .isEqualTo(2L);
    }

    // ── §21 functional outcomes (the coverage half) ──────────────────────────────

    @Test
    void anAssessmentThatCouldNotBeScoredIsCountedAsUnscoredNotAsZero() {
        functionalAssessment(TENANT, "cpid-1", "BARTHEL_INDEX", TODAY.minusDays(10), 60);
        functionalAssessment(TENANT, "cpid-1", "BARTHEL_INDEX", TODAY.minusDays(2), 75);
        functionalAssessment(TENANT, "cpid-2", "BARTHEL_INDEX", TODAY.minusDays(2), null);

        Map<String, Object> out = analytics.functionalAssessments(WINDOW_FROM, TODAY);

        assertThat(out.get("assessments_total")).isEqualTo(3L);
        assertThat(out.get("attempted_but_unscored_total")).isEqualTo(1L);
        // One person has the two measurements a change would require; the other has one.
        assertThat(out.get("people_with_repeat_measure_total")).isEqualTo(1L);
        assertThat((String) out.get("this_is_coverage_not_outcome")).contains("No change score is computed");
    }

    // ── §21 control and target attainment ────────────────────────────────────────

    @Test
    void neverAssessedIsItsOwnNumberAndStaysOutOfTheControlRate() {
        enrolment(TENANT, "cpid-1", ProgrammeVocabulary.HYPERTENSION, "ON_TREATMENT", "fac-1", "CONTROLLED");
        enrolment(TENANT, "cpid-2", ProgrammeVocabulary.HYPERTENSION, "ON_TREATMENT", "fac-1", "NOT_CONTROLLED");
        enrolment(TENANT, "cpid-3", ProgrammeVocabulary.HYPERTENSION, "ON_TREATMENT", "fac-1", null);
        enrolment(TENANT, "cpid-4", ProgrammeVocabulary.HYPERTENSION, "ON_TREATMENT", "fac-1", "TARGET_NOT_SET");

        Map<String, Object> out = programmes.controlAttainment(ProgrammeVocabulary.HYPERTENSION, "fac-1");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> entries = (List<Map<String, Object>>) out.get("entries");
        assertThat(entries).hasSize(1);
        Map<String, Object> row = entries.get(0);
        assertThat(row.get("open_enrolments")).isEqualTo(4L);
        assertThat(row.get("never_assessed")).isEqualTo(1L);
        assertThat(row.get("target_not_set")).isEqualTo(1L);

        @SuppressWarnings("unchecked")
        Map<String, Object> controlled = (Map<String, Object>) row.get("controlled");
        // Denominator is the two assessed against a target — not four, and not three.
        assertThat(controlled.get("numerator")).isEqualTo(1L);
        assertThat(controlled.get("denominator")).isEqualTo(2L);

        @SuppressWarnings("unchecked")
        Map<String, Object> coverage = (Map<String, Object>) row.get("assessment_coverage");
        assertThat(coverage.get("numerator")).isEqualTo(3L);
        assertThat(coverage.get("denominator")).isEqualTo(4L);
    }

    @Test
    void aCohortNobodyHasAssessedHasNoControlRateAtAll() {
        enrolment(TENANT, "cpid-1", ProgrammeVocabulary.DIABETES, "ON_TREATMENT", "fac-1", null);
        enrolment(TENANT, "cpid-2", ProgrammeVocabulary.DIABETES, "ON_TREATMENT", "fac-1", null);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> entries = (List<Map<String, Object>>)
                programmes.controlAttainment(ProgrammeVocabulary.DIABETES, "fac-1").get("entries");
        @SuppressWarnings("unchecked")
        Map<String, Object> controlled = (Map<String, Object>) entries.get(0).get("controlled");

        assertThat(controlled.get("denominator")).isEqualTo(0L);
        assertThat(controlled.get("rate")).isNull();
        assertThat(entries.get(0).get("never_assessed")).isEqualTo(2L);
    }

    @Test
    void anExitedEnrolmentIsNotPartOfTheOpenControlCohort() {
        ProgrammeEnrolmentEntity exited = enrolment(TENANT, "cpid-1", ProgrammeVocabulary.DIABETES,
                "ON_TREATMENT", "fac-1", "NOT_CONTROLLED");
        exited.setStatus("EXITED");
        exited.setExitReason("LOST_TO_FOLLOW_UP");
        exited.setExitOn(TODAY.minusDays(1));
        enrolmentRepository.save(exited);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> entries = (List<Map<String, Object>>)
                programmes.controlAttainment(ProgrammeVocabulary.DIABETES, "fac-1").get("entries");

        assertThat(entries).isEmpty();
    }

    @Test
    void anUnrecognisedProgrammeIsRefusedRatherThanReturningAnEmptyIndicator() {
        assertThatThrownBy(() -> programmes.controlAttainment("MALARIA_CARE", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unrecognised programme");
    }

    // ── §21 programme outcomes and mortality ─────────────────────────────────────

    @Test
    void everyExitReasonIsReportedSeparatelyBecauseTheyAreDifferentOutcomes() {
        exitedEnrolment(TENANT, "cpid-1", ProgrammeVocabulary.TB_TREATMENT, "CURED", TODAY.minusDays(5));
        exitedEnrolment(TENANT, "cpid-2", ProgrammeVocabulary.TB_TREATMENT, "DIED", TODAY.minusDays(5));
        exitedEnrolment(TENANT, "cpid-3", ProgrammeVocabulary.TB_TREATMENT, "LOST_TO_FOLLOW_UP", TODAY.minusDays(5));
        enrolment(TENANT, "cpid-4", ProgrammeVocabulary.TB_TREATMENT, "ON_TREATMENT", null, null);

        Map<String, Object> out = programmes.programmeOutcomes(
                ProgrammeVocabulary.TB_TREATMENT, null, WINDOW_FROM, TODAY);

        assertThat(out.get("exits_in_window_total")).isEqualTo(3L);
        assertThat(out.get("open_now_total")).isEqualTo(1L);

        @SuppressWarnings("unchecked")
        Map<String, Object> favourable = (Map<String, Object>) out.get("favourable_outcome");
        assertThat(favourable.get("numerator")).isEqualTo(1L);
        assertThat(favourable.get("denominator")).isEqualTo(3L);

        @SuppressWarnings("unchecked")
        Map<String, Object> died = (Map<String, Object>) out.get("died_among_exits");
        assertThat(died.get("numerator")).isEqualTo(1L);

        @SuppressWarnings("unchecked")
        Map<String, Object> lost = (Map<String, Object>) out.get("lost_to_follow_up_among_exits");
        assertThat(lost.get("numerator")).isEqualTo(1L);
    }

    @Test
    void anOutcomeRateOverNoExitsIsUndefinedRatherThanZeroSuccess() {
        enrolment(TENANT, "cpid-1", ProgrammeVocabulary.TB_TREATMENT, "ON_TREATMENT", null, null);

        @SuppressWarnings("unchecked")
        Map<String, Object> favourable = (Map<String, Object>) programmes
                .programmeOutcomes(ProgrammeVocabulary.TB_TREATMENT, null, WINDOW_FROM, TODAY)
                .get("favourable_outcome");

        assertThat(favourable.get("denominator")).isEqualTo(0L);
        assertThat(favourable.get("rate")).isNull();
    }

    @Test
    void theOutcomeWindowIsAnExitPeriodAndSaysSoRatherThanBorrowingTheDakName() {
        Map<String, Object> out = programmes.programmeOutcomes(null, null, WINDOW_FROM, TODAY);

        assertThat((String) out.get("exit_period_not_a_start_cohort"))
                .contains("STARTED").contains("must not be reported under its name");
        assertThat((String) out.get("counts_enrolments_not_people")).contains("Do not total");
    }

    @Test
    void newEnrolmentsAreCountedOnlyInsideTheirWindow() {
        enrolment(TENANT, "cpid-1", ProgrammeVocabulary.DIABETES, "ON_TREATMENT", null, null,
                TODAY.minusDays(3));
        enrolment(TENANT, "cpid-2", ProgrammeVocabulary.DIABETES, "ON_TREATMENT", null, null,
                TODAY.minusDays(300));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> started = (List<Map<String, Object>>) programmes
                .programmeOutcomes(ProgrammeVocabulary.DIABETES, null, WINDOW_FROM, TODAY)
                .get("started_in_window");

        assertThat(started).hasSize(1);
        assertThat(started.get(0).get("enrolments")).isEqualTo(1L);
    }

    // ── §21 stockouts, as far as the clinical record sees them ───────────────────

    @Test
    void regimenChangeReasonsSurfaceStockOutWithoutClaimingToBeAStockoutRate() {
        ProgrammeEnrolmentEntity e = enrolment(TENANT, "cpid-1", ProgrammeVocabulary.HIV_CARE,
                "ON_TREATMENT", null, null);
        regimen(TENANT, e, "TLD", "FIRST_LINE", TODAY.minusDays(60), TODAY.minusDays(10), "STOCK_OUT");
        regimen(TENANT, e, "TLE", "FIRST_LINE", TODAY.minusDays(200), TODAY.minusDays(9), "TOXICITY");

        Map<String, Object> out = programmes.regimenChangeReasons(
                ProgrammeVocabulary.HIV_CARE, WINDOW_FROM, TODAY);

        assertThat(out.get("regimen_changes_total")).isEqualTo(2L);
        @SuppressWarnings("unchecked")
        Map<String, Object> stockOut = (Map<String, Object>) out.get("stock_out_among_changes");
        assertThat(stockOut.get("numerator")).isEqualTo(1L);
        assertThat(stockOut.get("denominator")).isEqualTo(2L);
        assertThat((String) out.get("not_a_stockout_rate")).contains("no denominator of facility-days");
    }

    // ── routing ──────────────────────────────────────────────────────────────────

    @Test
    void theLiteralAnalyticsPathsAreNotParsedAsEnrolmentIdentifiers() throws Exception {
        // Declaration order decides this. If any of these fell through to /{enrolmentId} the route
        // would 400 on a path that is not an identifier — the failure the cohort-counts endpoint was
        // already positioned to avoid.
        assertThat(handlerFor("/v1/programme-enrolments/control-attainment")).isEqualTo("controlAttainment");
        assertThat(handlerFor("/v1/programme-enrolments/programme-outcomes")).isEqualTo("programmeOutcomes");
        assertThat(handlerFor("/v1/programme-enrolments/regimen-change-reasons")).isEqualTo("regimenChangeReasons");
        assertThat(handlerFor("/v1/programme-enrolments/cohort-counts")).isEqualTo("cohortCounts");
        assertThat(handlerFor("/v1/programme-enrolments/" + UUID.randomUUID())).isEqualTo("get");
    }

    @Test
    void everyMedicineAnalyticsIndicatorIsRoutable() {
        assertThat(handlerFor("/v1/medicine-analytics/detection")).isEqualTo("detection");
        assertThat(handlerFor("/v1/medicine-analytics/diagnostic-interval")).isEqualTo("diagnosticInterval");
        assertThat(handlerFor("/v1/medicine-analytics/complications")).isEqualTo("complications");
        assertThat(handlerFor("/v1/medicine-analytics/review-state")).isEqualTo("reviewState");
        assertThat(handlerFor("/v1/medicine-analytics/polypharmacy")).isEqualTo("polypharmacy");
        assertThat(handlerFor("/v1/medicine-analytics/episodes")).isEqualTo("episodes");
        assertThat(handlerFor("/v1/medicine-analytics/functional-assessments"))
                .isEqualTo("functionalAssessments");
    }

    private String handlerFor(String path) {
        try {
            HandlerExecutionChain chain = handlerMapping.getHandler(new MockHttpServletRequest("GET", path));
            assertThat(chain).as("no handler for %s", path).isNotNull();
            return ((HandlerMethod) chain.getHandler()).getMethod().getName();
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to resolve handler for " + path, ex);
        }
    }

    // ── fixtures ─────────────────────────────────────────────────────────────────

    private static OffsetDateTime daysAgo(int days) {
        return OffsetDateTime.now().minusDays(days);
    }

    /**
     * {@code created_at} is deliberately not settable on {@link ProblemEntity} — the record of when a
     * problem was written down is not editable — so every fixture lands in "now" and the window tests
     * move the window instead of the row.
     */
    private ProblemEntity problem(UUID tenant, String cpid, String code, String certainty,
                                  LocalDate onset, LocalDate reviewDate) {
        ProblemEntity p = new ProblemEntity();
        p.setTenantId(tenant);
        p.setSubjectCpid(cpid);
        p.setCode(code);
        p.setCodeSystem("ICD-10");
        p.setDisplay("Problem " + code);
        p.setClinicalStatus("ACTIVE");
        p.setCategory("DIAGNOSIS");
        p.setDiagnosticCertainty(certainty);
        p.setOnsetDate(onset);
        p.setReviewDate(reviewDate);
        p.setRecordedBy("test");
        return problemRepository.save(p);
    }

    private void complicationLink(UUID tenant, UUID complicationProblemId, UUID underlyingProblemId,
                                  OffsetDateTime createdAt) {
        ProblemLinkEntity l = new ProblemLinkEntity();
        l.setTenantId(tenant);
        l.setProblemId(complicationProblemId);
        l.setLinkType("COMPLICATION_OF");
        l.setTargetType("PROBLEM");
        l.setTargetProblemId(underlyingProblemId);
        l.setRecordedBy("test");
        l.setCreatedAt(createdAt);
        problemLinkRepository.save(l);
    }

    private void episode(UUID tenant, String cpid, String type, String status,
                         LocalDate startedOn, LocalDate endedOn, String endReason) {
        episode(tenant, cpid, type, status, startedOn, endedOn, endReason, null);
    }

    private void episode(UUID tenant, String cpid, String type, String status, LocalDate startedOn,
                         LocalDate endedOn, String endReason, String facilityId) {
        MedicalEpisodeEntity e = new MedicalEpisodeEntity();
        e.setTenantId(tenant);
        e.setSubjectCpid(cpid);
        e.setEpisodeType(type);
        e.setStatus(status);
        e.setTitle("Episode " + type);
        e.setStartedOn(startedOn);
        e.setEndedOn(endedOn);
        e.setEndReason(endReason);
        e.setManagingFacilityId(facilityId);
        e.setCreatedBy("test");
        episodeRepository.save(e);
    }

    private UUID reconciliation(UUID tenant, String cpid, String status, OffsetDateTime completedAt) {
        MedicationReconciliationEntity r = new MedicationReconciliationEntity();
        r.setTenantId(tenant);
        r.setSubjectCpid(cpid);
        r.setContext("ADMISSION");
        r.setStatus(status);
        r.setStartedBy("test");
        r.setStartedAt(completedAt.minusHours(1));
        if ("COMPLETED".equals(status)) {
            r.setCompletedBy("test");
            r.setCompletedAt(completedAt);
        }
        return reconciliationRepository.save(r).getReconciliationId();
    }

    private void reconciliationItem(UUID tenant, UUID reconciliationId, String display) {
        MedicationReconciliationItemEntity i = new MedicationReconciliationItemEntity();
        i.setTenantId(tenant);
        i.setReconciliationId(reconciliationId);
        i.setDisplay(display);
        i.setFinding("MATCHED");
        i.setSource("PRESCRIBED");
        reconciliationItemRepository.save(i);
    }

    private void functionalAssessment(UUID tenant, String cpid, String type, LocalDate on, Integer score) {
        FunctionalAssessmentEntity f = new FunctionalAssessmentEntity();
        f.setTenantId(tenant);
        f.setSubjectCpid(cpid);
        f.setAssessmentType(type);
        f.setAssessmentDate(on);
        f.setTotalScore(score);
        f.setMaxScore(score == null ? null : 100);
        f.setScoreAbsentReason(score == null ? "PATIENT_UNABLE_TO_PARTICIPATE" : null);
        f.setRecordedBy("test");
        functionalAssessmentRepository.save(f);
    }

    private ProgrammeEnrolmentEntity enrolment(UUID tenant, String cpid, String programme, String status,
                                               String facilityId, String controlStatus) {
        return enrolment(tenant, cpid, programme, status, facilityId, controlStatus, TODAY.minusDays(20));
    }

    private ProgrammeEnrolmentEntity enrolment(UUID tenant, String cpid, String programme, String status,
                                               String facilityId, String controlStatus,
                                               LocalDate enrolledOn) {
        ProgrammeEnrolmentEntity e = new ProgrammeEnrolmentEntity();
        e.setTenantId(tenant);
        e.setSubjectCpid(cpid);
        e.setProgramme(programme);
        e.setStatus(status);
        e.setAnchorProblemId(UUID.randomUUID());
        e.setEnrolledOn(enrolledOn);
        e.setManagingFacilityId(facilityId);
        e.setControlStatus(controlStatus);
        e.setControlAssessedOn(controlStatus == null ? null : enrolledOn.plusDays(5));
        e.setCreatedBy("test");
        return enrolmentRepository.save(e);
    }

    private void exitedEnrolment(UUID tenant, String cpid, String programme, String reason, LocalDate exitOn) {
        ProgrammeEnrolmentEntity e = enrolment(tenant, cpid, programme, "EXITED", null, null);
        e.setExitReason(reason);
        e.setExitOn(exitOn);
        enrolmentRepository.save(e);
    }

    private void regimen(UUID tenant, ProgrammeEnrolmentEntity enrolment, String code, String stage,
                         LocalDate startedOn, LocalDate endedOn, String changeReason) {
        TreatmentRegimenEntity r = new TreatmentRegimenEntity();
        r.setTenantId(tenant);
        r.setEnrolmentId(enrolment.getEnrolmentId());
        r.setSubjectCpid(enrolment.getSubjectCpid());
        r.setRegimenCode(code);
        r.setRegimenStage(stage);
        r.setStartedOn(startedOn);
        r.setEndedOn(endedOn);
        r.setChangeReason(changeReason);
        r.setRecordedBy("test");
        regimenRepository.save(r);
    }
}
