package zw.gov.mohcc.impilo.pct.core.clinical;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.pct.persistence.repository.FunctionalAssessmentRepository;
import zw.gov.mohcc.impilo.pct.persistence.repository.MedicalEpisodeRepository;
import zw.gov.mohcc.impilo.pct.persistence.repository.MedicationReconciliationItemRepository;
import zw.gov.mohcc.impilo.pct.persistence.repository.MedicationReconciliationRepository;
import zw.gov.mohcc.impilo.pct.persistence.repository.ProblemLinkRepository;
import zw.gov.mohcc.impilo.pct.persistence.repository.ProblemRepository;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The §21 adult-medicine analytics indicators, executed by the system of record.
 *
 * <p><strong>Why they live here.</strong> reporting-service holds a separate database with no view of
 * {@code pct.*} — no foreign data wrapper, no dblink, no ETL. A seeded SQL report definition naming
 * these tables would be registered, would show as ACTIVE, and would never run; five governed report
 * definitions in this estate are in exactly that state today. An indicator that cannot execute is
 * worse than an absent one, because it is counted as delivered. So every indicator here is a query
 * PCT itself runs against its own tables.</p>
 *
 * <p><strong>What is not here.</strong> §21 names twenty-two indicators; this service implements the
 * ones the data supports. The rest are not silently absent — every one is listed, with the data that
 * does not exist and the service that would own it, in
 * {@code docs/clinical/adult-medicine-domain-pack/analytics-coverage.md}. An analytics surface that
 * ships a third of its indicators looks complete to everyone who did not count.</p>
 *
 * <p><strong>Three rules every response obeys.</strong> Each count states its unit — rows, episodes,
 * reconciliations or people — because somebody in this estate totalled cohort rows into a patient
 * count. Each rate carries its numerator and denominator, and is {@code null} rather than a number
 * when the denominator is empty ({@link IndicatorRate}). Each absent value is reported as its own
 * quantity rather than folded into a neighbouring one: never assessed is not uncontrolled, no review
 * date is not up to date, and no onset date is not a zero-day diagnostic delay.</p>
 */
@Service
public class MedicineAnalyticsService {

    /** Default window when the caller names none. Long enough to hold a chronic-care review cycle. */
    static final int DEFAULT_WINDOW_DAYS = 90;

    /** The count at which a medicine list is conventionally called polypharmacy. */
    static final int DEFAULT_POLYPHARMACY_THRESHOLD = 5;

    private final ProblemRepository problemRepository;
    private final ProblemLinkRepository problemLinkRepository;
    private final MedicalEpisodeRepository episodeRepository;
    private final MedicationReconciliationRepository reconciliationRepository;
    private final MedicationReconciliationItemRepository reconciliationItemRepository;
    private final FunctionalAssessmentRepository functionalAssessmentRepository;

    public MedicineAnalyticsService(ProblemRepository problemRepository,
                                    ProblemLinkRepository problemLinkRepository,
                                    MedicalEpisodeRepository episodeRepository,
                                    MedicationReconciliationRepository reconciliationRepository,
                                    MedicationReconciliationItemRepository reconciliationItemRepository,
                                    FunctionalAssessmentRepository functionalAssessmentRepository) {
        this.problemRepository = problemRepository;
        this.problemLinkRepository = problemLinkRepository;
        this.episodeRepository = episodeRepository;
        this.reconciliationRepository = reconciliationRepository;
        this.reconciliationItemRepository = reconciliationItemRepository;
        this.functionalAssessmentRepository = functionalAssessmentRepository;
    }

    // ── §21 "Disease detection" ──────────────────────────────────────────────────

    /**
     * What was newly recorded on problem lists in the window, by code and diagnostic certainty.
     *
     * <p>Detection here means <em>first written down in this system</em>. It is not incidence: a
     * condition the patient has had for years, entered today at a first visit, is a detection event by
     * this measure and a prevalent case in reality. The response says so, because a rising line on
     * this indicator during a rollout is the record catching up, not disease spreading.</p>
     */
    @Transactional(readOnly = true)
    public Map<String, Object> detection(String category, LocalDate from, LocalDate to) {
        UUID tenantId = tenant();
        String cat = ProblemVocabulary.require("category",
                category == null ? "DIAGNOSIS" : category, ProblemVocabulary.CATEGORIES, true);
        Window w = Window.of(from, to);

        List<Object[]> rows = problemRepository.detectionCounts(tenantId, cat, w.fromTs(), w.untilTs());
        List<Map<String, Object>> counts = new ArrayList<>();
        long total = 0;
        for (Object[] r : rows) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("code", r[0]);
            row.put("display", r[1]);
            // Null certainty is a recorded state, not a missing one — it is left null rather than
            // labelled, so a consumer cannot mistake a placeholder for a clinical judgement.
            row.put("diagnostic_certainty", r[2]);
            long n = ((Number) r[3]).longValue();
            row.put("problems_recorded", n);
            total += n;
            counts.add(row);
        }

        Map<String, Object> out = base("disease_detection", w);
        out.put("category", cat);
        out.put("counts", counts);
        out.put("problems_recorded_total", total);
        out.put("distinct_people", problemRepository.detectionDistinctSubjects(
                tenantId, cat, w.fromTs(), w.untilTs()));
        out.put("counts_problem_records_not_people",
                "Each row counts PROBLEM RECORDS. One person with hypertension and diabetes appears "
                + "in two rows, and a person whose problem was recorded twice appears twice. Use "
                + "distinct_people for a headcount; do not total the rows.");
        out.put("detection_means_first_recorded_here",
                "The window is on when the problem was recorded in Impilo, not when the illness began. "
                + "A long-standing condition entered at a first visit counts as a detection, so a rise "
                + "on this indicator during a rollout is the record filling up, not incidence rising.");
        out.put("certainty_is_grouped_not_filtered",
                "SUSPECTED, WORKING and CONFIRMED entries of the same code are separate rows. Summing "
                + "them counts a suspicion as a diagnosis.");
        return out;
    }

    // ── §21 "Diagnostic delays" ──────────────────────────────────────────────────

    /**
     * Days from the onset the patient reported to the moment a confirmed diagnosis was recorded.
     *
     * <p>Reported as a median and a 90th percentile rather than a mean: diagnostic delay is heavily
     * skewed, and one patient who reported onset in childhood drags a mean into meaninglessness.</p>
     *
     * <p>Two exclusions are counted and returned rather than dropped. Confirmed diagnoses with no
     * onset date cannot contribute — and if most records lack one, the percentiles describe the
     * minority who happened to have both dates, not the service. Records whose onset falls after the
     * diagnosis was recorded are a data error, not a negative delay, and are reported separately
     * instead of being averaged in as though time ran backwards.</p>
     */
    @Transactional(readOnly = true)
    public Map<String, Object> diagnosticInterval(LocalDate from, LocalDate to) {
        UUID tenantId = tenant();
        Window w = Window.of(from, to);

        List<Object[]> rows = problemRepository.confirmedDiagnosisOnsetAndRecord(
                tenantId, w.fromTs(), w.untilTs());

        List<Long> days = new ArrayList<>();
        long onsetAfterRecord = 0;
        for (Object[] r : rows) {
            LocalDate onset = (LocalDate) r[0];
            LocalDate recorded = ((OffsetDateTime) r[1]).atZoneSameInstant(ZoneId.systemDefault())
                    .toLocalDate();
            long d = ChronoUnit.DAYS.between(onset, recorded);
            if (d < 0) {
                onsetAfterRecord++;
            } else {
                days.add(d);
            }
        }
        Collections.sort(days);

        long withoutOnset = problemRepository.confirmedDiagnosesWithoutOnset(
                tenantId, w.fromTs(), w.untilTs());

        Map<String, Object> out = base("diagnostic_interval", w);
        out.put("measured_diagnoses", (long) days.size());
        out.put("excluded_no_onset_date", withoutOnset);
        out.put("excluded_onset_after_record", onsetAfterRecord);
        out.put("median_days", percentile(days, 0.50));
        out.put("p90_days", percentile(days, 0.90));
        out.put("max_days", days.isEmpty() ? null : days.get(days.size() - 1));
        if (days.isEmpty()) {
            out.put("statistics_undefined_reason",
                    "No confirmed diagnosis in this window carries a usable onset date, so there is no "
                    + "distribution. This is not a delay of zero days.");
        }
        out.put("counts_diagnosis_records_not_people",
                "Counts CONFIRMED DIAGNOSIS RECORDS. A person diagnosed with two conditions contributes "
                + "two intervals.");
        out.put("coverage_note",
                "Only confirmed diagnoses carrying an onset date can be measured: " + days.size()
                + " measured, " + withoutOnset + " excluded for having no onset date. Onset is what "
                + "the patient reported, so this measures the interval from remembered onset to "
                + "record — not from the first health-system contact, which would need an attendance "
                + "history this service does not hold.");
        return out;
    }

    // ── §21 "Complications" ──────────────────────────────────────────────────────

    /**
     * Complications recorded against the condition they arose from.
     *
     * <p>The only complication signal in this estate is the COMPLICATION_OF problem link, so this
     * measures documentation, not disease. A diabetic foot ulcer entered on the problem list without
     * a link to the diabetes is a complication that happened and is not counted here. The response
     * says so, because the reassuring reading of a low number here is the wrong one.</p>
     */
    @Transactional(readOnly = true)
    public Map<String, Object> complications(LocalDate from, LocalDate to) {
        UUID tenantId = tenant();
        Window w = Window.of(from, to);

        List<Object[]> rows = problemLinkRepository.complicationCounts(tenantId, w.fromTs(), w.untilTs());
        List<Map<String, Object>> counts = new ArrayList<>();
        long total = 0;
        for (Object[] r : rows) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("underlying_code", r[0]);
            row.put("underlying_display", r[1]);
            row.put("complication_code", r[2]);
            row.put("complication_display", r[3]);
            long n = ((Number) r[4]).longValue();
            row.put("links_recorded", n);
            total += n;
            counts.add(row);
        }
        long nonProblemTargets = problemLinkRepository.complicationLinksWithNonProblemTarget(
                tenantId, w.fromTs(), w.untilTs());

        Map<String, Object> out = base("complications", w);
        out.put("counts", counts);
        out.put("links_recorded_total", total);
        out.put("links_with_non_problem_target", nonProblemTargets);
        out.put("counts_links_not_people",
                "Counts COMPLICATION_OF LINKS. One person may have several, and one complication linked "
                + "to two underlying conditions appears in two rows.");
        out.put("measures_documentation_not_incidence",
                "A complication is counted only where a clinician linked it to the condition it arose "
                + "from. An unlinked complication is invisible here, so a low number may mean good care "
                + "or poor linking, and this indicator cannot tell you which.");
        if (nonProblemTargets > 0) {
            out.put("excluded_note", nonProblemTargets + " COMPLICATION_OF link(s) point at something "
                    + "other than a local problem row (an observation, a procedure) and carry no code "
                    + "to group by. They are counted here rather than dropped so the total is not "
                    + "quietly short.");
        }
        return out;
    }

    // ── §21 "Follow-up completion" (partial: the due side) ───────────────────────

    /**
     * Review state of the open problem list — overdue, due today, scheduled, or never set.
     *
     * <p>This is the <em>due</em> half of follow-up. Whether the patient then attended cannot be
     * answered here: PCT holds no appointment or attendance record, so completion is listed as NOT
     * COMPUTABLE in the coverage register rather than approximated from this number.</p>
     *
     * <p>NO_REVIEW_DATE is reported as its own quantity and never merged into SCHEDULED. A problem
     * with no review date is not a problem that is up to date — nobody has said when it should next
     * be looked at — and folding those into the compliant group is precisely how a follow-up
     * indicator comes out green on a service following nobody up.</p>
     */
    @Transactional(readOnly = true)
    public Map<String, Object> reviewState(LocalDate asOf) {
        UUID tenantId = tenant();
        LocalDate at = asOf == null ? LocalDate.now() : asOf;

        Map<String, Long> byState = new LinkedHashMap<>();
        byState.put("OVERDUE", 0L);
        byState.put("DUE_TODAY", 0L);
        byState.put("SCHEDULED", 0L);
        byState.put("NO_REVIEW_DATE", 0L);
        for (Object[] r : problemRepository.reviewStateCounts(
                tenantId, ProblemVocabulary.OPEN_STATUSES, at)) {
            byState.put((String) r[0], ((Number) r[1]).longValue());
        }

        long withDate = byState.get("OVERDUE") + byState.get("DUE_TODAY") + byState.get("SCHEDULED");
        long noDate = byState.get("NO_REVIEW_DATE");

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("indicator", "problem_review_state");
        out.put("as_of", at.toString());
        out.put("counts", byState);
        out.put("open_problems_total", withDate + noDate);
        out.put("overdue_among_dated", IndicatorRate.of(byState.get("OVERDUE"), withDate,
                "open problems that carry a review date; the " + noDate + " with no review date are "
                + "deliberately excluded from this denominator and reported separately"));
        out.put("no_review_date_is_not_up_to_date",
                noDate + " open problem(s) have no review date at all. They are not overdue and they "
                + "are not on schedule — nobody has recorded when they should next be reviewed. They "
                + "are kept out of the overdue rate rather than counted as compliant.");
        out.put("counts_problems_not_people",
                "Counts OPEN PROBLEM RECORDS. A person with four open problems contributes four.");
        out.put("this_is_review_due_not_follow_up_completed",
                "Whether the patient attended cannot be answered from PCT: there is no appointment or "
                + "attendance record here. Do not label this follow-up completion.");
        return out;
    }

    // ── §21 "Polypharmacy" ───────────────────────────────────────────────────────

    /**
     * The medicine-count distribution across completed reconciliations.
     *
     * <p>Counted only over COMPLETED reconciliations: an abandoned review has an item list that stops
     * where the clinician stopped, and reading it as a medicine count makes an interrupted review look
     * like a patient on fewer drugs.</p>
     *
     * <p>Reconciliations with no items at all are added back into the denominator. The item query
     * cannot see them — a group-by over items has no row for a reconciliation with none — and leaving
     * them out would shrink the denominator and inflate the rate.</p>
     */
    @Transactional(readOnly = true)
    public Map<String, Object> polypharmacy(LocalDate from, LocalDate to, Integer threshold) {
        UUID tenantId = tenant();
        Window w = Window.of(from, to);
        int limit = (threshold == null || threshold < 1) ? DEFAULT_POLYPHARMACY_THRESHOLD : threshold;

        List<Object[]> rows = reconciliationItemRepository.itemCountsPerCompletedReconciliation(
                tenantId, w.fromTs(), w.untilTs());
        long completed = reconciliationRepository.countCompleted(tenantId, w.fromTs(), w.untilTs());
        long withItems = rows.size();
        long withoutItems = completed - withItems;

        long atOrAbove = 0;
        long bucket1to4 = 0;
        long bucket5to9 = 0;
        long bucket10plus = 0;
        for (Object[] r : rows) {
            long n = ((Number) r[1]).longValue();
            if (n >= limit) {
                atOrAbove++;
            }
            if (n < 5) {
                bucket1to4++;
            } else if (n < 10) {
                bucket5to9++;
            } else {
                bucket10plus++;
            }
        }

        Map<String, Long> distribution = new LinkedHashMap<>();
        distribution.put("0", withoutItems);
        distribution.put("1-4", bucket1to4);
        distribution.put("5-9", bucket5to9);
        distribution.put("10+", bucket10plus);

        Map<String, Object> out = base("polypharmacy", w);
        out.put("threshold_medicines", limit);
        out.put("completed_reconciliations", completed);
        out.put("reconciliations_with_zero_items", withoutItems);
        out.put("medicines_per_reconciliation", distribution);
        out.put("at_or_above_threshold", IndicatorRate.of(atOrAbove, completed,
                "reconciliations completed in the window, including the " + withoutItems
                + " that recorded no medicine at all"));
        out.put("distinct_people", reconciliationRepository.countCompletedDistinctSubjects(
                tenantId, w.fromTs(), w.untilTs()));
        out.put("counts_reconciliations_not_people",
                "Counts RECONCILIATIONS. A patient reconciled at admission and again at discharge is "
                + "counted twice, and if their medicines were rationalised in between the same person "
                + "appears in two different buckets. Use distinct_people for a headcount.");
        out.put("measures_what_was_reconciled",
                "The medicine count is what the reconciliation captured at that moment, not the "
                + "patient's true regimen. Dispensing history lives in pharmacy-service and is not "
                + "joined here.");
        return out;
    }

    // ── §21 "Palliative access" and the episode side of "Mortality" ──────────────

    /**
     * The episode mix opened in the window, palliative reach, and how episodes ended.
     *
     * <p>Palliative access is measured in people, not episodes: someone with three palliative episodes
     * has been reached once, and counting episodes would make a service that re-opens them look like
     * a service reaching more patients.</p>
     *
     * <p>End reasons are reported because status cannot carry them — COMPLETED, TRANSFERRED,
     * LOST_TO_FOLLOW_UP and DIED all leave the status FINISHED. DIED here is episode-level mortality
     * only: it is a death recorded while a medical episode was open, not a population death rate, and
     * a death after the episode closed is invisible to it.</p>
     */
    @Transactional(readOnly = true)
    public Map<String, Object> episodes(String facilityId, LocalDate from, LocalDate to) {
        UUID tenantId = tenant();
        Window w = Window.of(from, to);

        List<Map<String, Object>> mix = new ArrayList<>();
        long episodesTotal = 0;
        for (Object[] r : episodeRepository.episodeTypeCounts(tenantId, facilityId, w.from(), w.to())) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("episode_type", r[0]);
            row.put("status", r[1]);
            long n = ((Number) r[2]).longValue();
            row.put("episodes", n);
            episodesTotal += n;
            mix.add(row);
        }

        long peopleAny = episodeRepository.distinctSubjectsWithEpisode(
                tenantId, facilityId, null, w.from(), w.to());
        long peoplePalliative = episodeRepository.distinctSubjectsWithEpisode(
                tenantId, facilityId, "PALLIATIVE", w.from(), w.to());

        List<Map<String, Object>> endings = new ArrayList<>();
        long died = 0;
        long endedTotal = 0;
        for (Object[] r : episodeRepository.episodeEndReasonCounts(
                tenantId, facilityId, w.from(), w.to())) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("episode_type", r[0]);
            row.put("end_reason", r[1]);
            long n = ((Number) r[2]).longValue();
            row.put("episodes", n);
            endedTotal += n;
            if ("DIED".equals(r[1])) {
                died += n;
            }
            endings.add(row);
        }

        Map<String, Object> out = base("episode_mix_and_outcomes", w);
        out.put("facility_id", facilityId);
        out.put("episode_mix", mix);
        out.put("episodes_opened_total", episodesTotal);
        out.put("people_with_any_episode", peopleAny);
        out.put("people_with_palliative_episode", peoplePalliative);
        out.put("palliative_reach", IndicatorRate.of(peoplePalliative, peopleAny,
                "people with any medical episode opened in the window"));
        out.put("end_reasons", endings);
        out.put("episodes_ended_total", endedTotal);
        out.put("died_among_ended", IndicatorRate.of(died, endedTotal,
                "medical episodes that ended in the window, whatever the reason"));
        out.put("mix_counts_episodes_reach_counts_people",
                "episode_mix and end_reasons count EPISODES — one person may have several. "
                + "people_with_* count distinct people. Do not read the two as the same unit.");
        out.put("palliative_access_means_an_episode_was_opened",
                "This counts people for whom a PALLIATIVE episode exists in PCT. Palliative care given "
                + "without one — a community visit, a hospice referral, symptom control inside another "
                + "episode — does not appear. It measures recorded access, and the unmet-need "
                + "denominator (people who needed palliative care) does not exist anywhere.");
        out.put("mortality_here_is_episode_level_only",
                "DIED counts episodes closed with that reason. It is not a mortality rate: there is no "
                + "population denominator, deaths after an episode closes are not seen, and civil "
                + "death registration is not held by PCT.");
        return out;
    }

    // ── §21 "Functional outcomes" (partial: coverage and the paired denominator) ─

    /**
     * Functional assessments recorded, by instrument, and how many people have a repeat measure.
     *
     * <p>Scores are not averaged and no change is computed. {@code assessment_type} is open text, so
     * instruments are not comparable to one another — a 60 on a Barthel index and a 60 on a cognitive
     * screen mean opposite things, and {@code max_score} is optional so the scores cannot even be
     * normalised. What can be stated honestly is coverage, how often an assessment was attempted but
     * could not be scored, and how many people have the two measurements a change would require.</p>
     */
    @Transactional(readOnly = true)
    public Map<String, Object> functionalAssessments(LocalDate from, LocalDate to) {
        UUID tenantId = tenant();
        Window w = Window.of(from, to);

        Map<String, Map<String, Object>> byType = new LinkedHashMap<>();
        long total = 0;
        long scoreAbsent = 0;
        for (Object[] r : functionalAssessmentRepository.assessmentCounts(tenantId, w.from(), w.to())) {
            String type = (String) r[0];
            String presence = (String) r[1];
            long n = ((Number) r[2]).longValue();
            Map<String, Object> row = byType.computeIfAbsent(type, t -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("assessment_type", t);
                m.put("scored", 0L);
                m.put("attempted_but_unscored", 0L);
                m.put("people_with_repeat_measure", 0L);
                return m;
            });
            if ("SCORE_ABSENT".equals(presence)) {
                row.put("attempted_but_unscored", (Long) row.get("attempted_but_unscored") + n);
                scoreAbsent += n;
            } else {
                row.put("scored", (Long) row.get("scored") + n);
            }
            total += n;
        }

        long repeatSubjects = 0;
        for (Object[] r : functionalAssessmentRepository.repeatMeasureSubjects(
                tenantId, w.from(), w.to())) {
            String type = (String) r[0];
            repeatSubjects++;
            Map<String, Object> row = byType.get(type);
            if (row != null) {
                row.put("people_with_repeat_measure", (Long) row.get("people_with_repeat_measure") + 1);
            }
        }

        Map<String, Object> out = base("functional_assessment_coverage", w);
        out.put("by_instrument", new ArrayList<>(byType.values()));
        out.put("assessments_total", total);
        out.put("attempted_but_unscored_total", scoreAbsent);
        out.put("people_with_repeat_measure_total", repeatSubjects);
        out.put("counts_assessments_not_people",
                "by_instrument and assessments_total count ASSESSMENT RECORDS. "
                + "people_with_repeat_measure counts distinct people on that instrument, and a person "
                + "assessed on two instruments is counted once under each — do not total that column.");
        out.put("this_is_coverage_not_outcome",
                "No change score is computed. assessment_type is free text, instruments are not "
                + "comparable, and max_score is optional, so an average across them would be "
                + "meaningless. people_with_repeat_measure is the denominator a real functional-outcome "
                + "indicator would need; the outcome itself needs a closed instrument vocabulary.");
        out.put("unscored_is_not_a_zero",
                scoreAbsent + " assessment(s) record why no score could be obtained. A zero on a "
                + "functional scale reads as total dependence; these are excluded from scored counts "
                + "rather than given a number.");
        return out;
    }

    // ── helpers ──────────────────────────────────────────────────────────────────

    private static UUID tenant() {
        TrustContext ctx = TrustContextHolder.require();
        return ctx.tenantId();
    }

    private static Map<String, Object> base(String indicator, Window w) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("indicator", indicator);
        out.put("window", Map.of("from", w.from().toString(), "to", w.to().toString()));
        return out;
    }

    /** Nearest-rank percentile over an already-sorted list; null on an empty list, never zero. */
    static Long percentile(List<Long> sortedAscending, double p) {
        if (sortedAscending.isEmpty()) {
            return null;
        }
        int rank = (int) Math.ceil(p * sortedAscending.size());
        return sortedAscending.get(Math.max(0, Math.min(sortedAscending.size() - 1, rank - 1)));
    }

    /**
     * An inclusive day window, defaulted rather than unbounded.
     *
     * <p>Unbounded would mean "since the beginning of the record", which is a different indicator
     * dressed as the same one and grows without limit. Timestamp columns are compared against the
     * window's boundaries in the server's zone, half-open at the end so the final day is included
     * exactly once.</p>
     */
    record Window(LocalDate from, LocalDate to) {
        static Window of(LocalDate from, LocalDate to) {
            LocalDate end = to == null ? LocalDate.now() : to;
            LocalDate start = from == null ? end.minusDays(DEFAULT_WINDOW_DAYS) : from;
            if (start.isAfter(end)) {
                throw new IllegalArgumentException(
                        "Window starts (" + start + ") after it ends (" + end + ").");
            }
            return new Window(start, end);
        }

        OffsetDateTime fromTs() {
            return from.atStartOfDay(ZoneId.systemDefault()).toOffsetDateTime();
        }

        /** Exclusive upper bound: the day after {@code to}, so {@code to} itself is included. */
        OffsetDateTime untilTs() {
            return to.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toOffsetDateTime();
        }
    }
}
