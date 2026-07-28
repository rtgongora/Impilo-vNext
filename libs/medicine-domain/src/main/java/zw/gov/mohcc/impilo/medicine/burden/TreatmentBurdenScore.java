package zw.gov.mohcc.impilo.medicine.burden;

import zw.gov.mohcc.impilo.medicine.common.RefusalReason;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * A composite index of how much work a person's treatment costs them.
 *
 * <p>Treatment burden is the load a health system places on a patient: tablets to take, journeys to
 * make, appointments to attend, conditions to keep track of. It is well described in the literature
 * — Mair and May, <em>Thinking about the burden of treatment</em>, BMJ 2014;349:g6680; Tran et al.,
 * <em>Treatment Burden Questionnaire</em>, BMC Med 2012;10:68 — and it predicts non-adherence and
 * disengagement better than the number of diagnoses alone. In a system where a patient may walk two
 * hours to a clinic, the attendance count is not an administrative statistic.</p>
 *
 * <p><strong>No validated instrument is implemented here.</strong> The published questionnaires are
 * patient-reported and item-scored; this is a three-dimension index computed from counts already in
 * the record. It is an Impilo engineering construct and an engineering seed pending MoHCC
 * ratification; nothing is vendored under {@code docs/reference}. It is a way of ordering a list,
 * not a measurement of a person.</p>
 *
 * <h2>Every threshold is the caller's</h2>
 *
 * <p>This class holds no cut-points. How many medicines counts as a lot, how many clinic visits is
 * too many, how many long-term conditions makes someone complex — those depend on the setting, the
 * disease mix and what a ministry intends to do with the answer, and they change without a code
 * release. They arrive in {@link BurdenThresholds}, from governed CKP content.</p>
 *
 * <p>For the same reason the result carries a number and no band. Banding a composite is a decision
 * about who gets attention, and this library does not make those.</p>
 */
public final class TreatmentBurdenScore {

    /** Bumped whenever the scoring shape or a plausibility bound changes. */
    public static final String CONTENT_VERSION = "impilo-treatment-burden-1.0.0";

    /** Above this, the medicine count is a data fault rather than a regimen. */
    public static final int MAX_PLAUSIBLE_MEDICINE_COUNT = 100;

    /** Above this in a scoring window, the attendance count is a data fault. */
    public static final int MAX_PLAUSIBLE_ATTENDANCE_COUNT = 500;

    /** Above this, the long-term condition count is a data fault. */
    public static final int MAX_PLAUSIBLE_CONDITION_COUNT = 50;

    private TreatmentBurdenScore() {
    }

    /**
     * Score the three dimensions against caller-supplied cut-points and sum the levels.
     *
     * <p>Each dimension's count is turned into a level: the number of that dimension's cut-points
     * the count reaches or exceeds. Three cut-points therefore give levels 0 to 3. The composite is
     * the sum of the three levels, and {@code normalisedIndex} expresses it as a fraction of the
     * maximum the supplied thresholds allow, so two facilities using different cut-point sets can
     * still be compared on a 0–1 scale — though the comparison is only as meaningful as the two
     * threshold sets are alike.</p>
     *
     * <p>Every count is required. A missing medicine count is not zero medicines: one is a gap in
     * the record and the other is a patient on nothing, and a composite computed as though they were
     * the same puts the least-documented patients at the bottom of the list — which is precisely
     * backwards, because a patient nobody has documented is usually the one to look at.</p>
     *
     * @param medicineCount           distinct medicines currently prescribed
     * @param distinctAttendanceCount separate attendances in the window the caller is scoring over.
     *                                What the window is, is the caller's business; it is not
     *                                recorded here, so a caller comparing two scores must ensure
     *                                they used the same one.
     * @param longTermConditionCount  active long-term conditions on the problem list
     * @param thresholds              the cut-points, from governed content
     */
    public static Burden score(Integer medicineCount,
                               Integer distinctAttendanceCount,
                               Integer longTermConditionCount,
                               BurdenThresholds thresholds) {
        if (thresholds == null) {
            return refused(RefusalReason.INPUT_MISSING,
                    "No burden thresholds were supplied. This library holds no default cut-points,"
                            + " because where the boundaries sit is a policy decision and belongs in"
                            + " governed content.");
        }
        String thresholdFault = thresholds.validationFault();
        if (thresholdFault != null) {
            return refused(RefusalReason.INPUTS_INCONSISTENT, thresholdFault);
        }

        String missing = firstMissing(medicineCount, distinctAttendanceCount, longTermConditionCount);
        if (missing != null) {
            return refused(RefusalReason.INPUT_MISSING,
                    "The " + missing + " is not recorded, so a composite treatment burden cannot be"
                            + " computed. An absent count is not a count of zero, and scoring it as one"
                            + " would rank the least-documented patients as the least burdened.");
        }

        String rangeFault = firstOutOfRange(medicineCount, distinctAttendanceCount, longTermConditionCount);
        if (rangeFault != null) {
            return refused(RefusalReason.INPUT_OUT_OF_PHYSIOLOGICAL_RANGE, rangeFault);
        }

        int medicineLevel = level(medicineCount, thresholds.medicineCountCutPoints());
        int attendanceLevel = level(distinctAttendanceCount, thresholds.attendanceCountCutPoints());
        int conditionLevel = level(longTermConditionCount, thresholds.longTermConditionCountCutPoints());

        int total = medicineLevel + attendanceLevel + conditionLevel;
        int max = thresholds.maxPoints();
        BigDecimal normalised = BigDecimal.valueOf(total)
                .divide(BigDecimal.valueOf(max), 3, RoundingMode.HALF_UP);

        return new Burden(total, max, medicineLevel, attendanceLevel, conditionLevel, normalised,
                null, null, CONTENT_VERSION);
    }

    private static String firstMissing(Integer medicineCount, Integer attendanceCount, Integer conditionCount) {
        if (medicineCount == null) {
            return "medicine count";
        }
        if (attendanceCount == null) {
            return "attendance count";
        }
        if (conditionCount == null) {
            return "long-term condition count";
        }
        return null;
    }

    private static String firstOutOfRange(int medicineCount, int attendanceCount, int conditionCount) {
        if (medicineCount < 0 || attendanceCount < 0 || conditionCount < 0) {
            return "A negative count was supplied (medicines " + medicineCount + ", attendances "
                    + attendanceCount + ", long-term conditions " + conditionCount + "). None of these"
                    + " can be below zero.";
        }
        if (medicineCount > MAX_PLAUSIBLE_MEDICINE_COUNT) {
            return "A medicine count of " + medicineCount + " is above the plausibility ceiling of "
                    + MAX_PLAUSIBLE_MEDICINE_COUNT + ". This is usually a duplicated medication list"
                    + " rather than a regimen.";
        }
        if (attendanceCount > MAX_PLAUSIBLE_ATTENDANCE_COUNT) {
            return "An attendance count of " + attendanceCount + " is above the plausibility ceiling of "
                    + MAX_PLAUSIBLE_ATTENDANCE_COUNT + " for one scoring window. Check the window.";
        }
        if (conditionCount > MAX_PLAUSIBLE_CONDITION_COUNT) {
            return "A long-term condition count of " + conditionCount + " is above the plausibility"
                    + " ceiling of " + MAX_PLAUSIBLE_CONDITION_COUNT + ". Check for duplicated problem"
                    + " list entries.";
        }
        return null;
    }

    /** The number of cut-points a count reaches or exceeds. */
    private static int level(int count, List<Integer> cutPoints) {
        int level = 0;
        for (Integer cutPoint : cutPoints) {
            if (count >= cutPoint) {
                level++;
            }
        }
        return level;
    }

    private static Burden refused(RefusalReason reason, String explanation) {
        return new Burden(null, null, null, null, null, null, reason, explanation, CONTENT_VERSION);
    }

    /**
     * Caller-supplied cut-points, one ascending list per dimension.
     *
     * <p>A count scores one point for every cut-point it reaches, so {@code [5, 10, 15]} gives a
     * patient on 11 medicines a level of 2. Lists may be different lengths, which weights the
     * dimensions relative to each other — that weighting is itself a policy choice and is therefore
     * the caller's to make.</p>
     *
     * <p>Cut-points must be strictly ascending and non-negative. An unsorted list is refused rather
     * than sorted: a list in the wrong order is a content-authoring error, and silently sorting it
     * produces a score from thresholds nobody wrote.</p>
     */
    public record BurdenThresholds(List<Integer> medicineCountCutPoints,
                                   List<Integer> attendanceCountCutPoints,
                                   List<Integer> longTermConditionCountCutPoints) {

        /** The highest composite score these thresholds allow: one point per cut-point. */
        public int maxPoints() {
            return medicineCountCutPoints.size()
                    + attendanceCountCutPoints.size()
                    + longTermConditionCountCutPoints.size();
        }

        /** The first structural fault in the supplied cut-points, or null when they are usable. */
        public String validationFault() {
            String fault = validate("medicine", medicineCountCutPoints);
            if (fault != null) {
                return fault;
            }
            fault = validate("attendance", attendanceCountCutPoints);
            if (fault != null) {
                return fault;
            }
            return validate("long-term condition", longTermConditionCountCutPoints);
        }

        private static String validate(String dimension, List<Integer> cutPoints) {
            if (cutPoints == null || cutPoints.isEmpty()) {
                return "No " + dimension + " cut-points were supplied. Every dimension needs at least"
                        + " one, or the composite silently ignores it.";
            }
            Integer previous = null;
            for (Integer cutPoint : cutPoints) {
                if (cutPoint == null) {
                    return "A null " + dimension + " cut-point was supplied.";
                }
                if (cutPoint < 0) {
                    return "A " + dimension + " cut-point of " + cutPoint + " is negative.";
                }
                if (previous != null && cutPoint <= previous) {
                    return "The " + dimension + " cut-points are not strictly ascending (" + previous
                            + " then " + cutPoint + "). They are refused rather than sorted, because a"
                            + " list in the wrong order is an authoring error and sorting it would score"
                            + " patients against thresholds nobody wrote.";
                }
                previous = cutPoint;
            }
            return null;
        }
    }

    /**
     * A composite burden index, or a stated reason there is none.
     *
     * <p>There is deliberately no band. Where the boundaries of "high burden" sit is a decision
     * about who gets extra attention, and it is made in governed content, not here.</p>
     *
     * @param totalPoints              sum of the three dimension levels; null when refused
     * @param maxPoints                the highest total the supplied thresholds allow
     * @param medicineLevel            level from the medicine count
     * @param attendanceLevel          level from the attendance count
     * @param longTermConditionLevel   level from the long-term condition count
     * @param normalisedIndex          {@code totalPoints / maxPoints}, 0.000 to 1.000
     * @param reason                   machine-readable refusal code; null when computed
     * @param explanation              clinician-readable refusal text; null when computed
     * @param contentVersion           the {@link #CONTENT_VERSION} that produced this result
     */
    public record Burden(Integer totalPoints,
                         Integer maxPoints,
                         Integer medicineLevel,
                         Integer attendanceLevel,
                         Integer longTermConditionLevel,
                         BigDecimal normalisedIndex,
                         RefusalReason reason,
                         String explanation,
                         String contentVersion) {

        /** True when a score is present. False means {@link #reason()} explains why it is not. */
        public boolean computed() {
            return totalPoints != null;
        }
    }
}
