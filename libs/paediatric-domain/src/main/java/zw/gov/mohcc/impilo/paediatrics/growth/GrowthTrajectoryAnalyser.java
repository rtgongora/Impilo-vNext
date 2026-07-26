package zw.gov.mohcc.impilo.paediatrics.growth;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Reads a child's serial measurements as a trajectory rather than a set of isolated points.
 *
 * <p>The clinically important signal is usually the change, not the level. A child whose
 * weight-for-age has fallen from -1.4 to -2.2 SD over four months is faltering long before
 * any single measurement looks alarming, and a child who has gained weight can still be
 * losing ground against the standard. This analyser detects that drift, flags measurements
 * that cannot be biologically true, and states its conclusion in plain language a clinician
 * can repeat to a caregiver.</p>
 *
 * <p>Thresholds are engineering seeds pending MoHCC ratification.</p>
 */
public final class GrowthTrajectoryAnalyser {

    /** A fall of this many z-scores between contacts is growth faltering. */
    public static final BigDecimal FALTERING_Z_DROP = BigDecimal.valueOf(0.67d);

    /** Any z-score beyond this magnitude is outside WHO plausibility limits. */
    public static final BigDecimal IMPLAUSIBLE_Z_MAGNITUDE = BigDecimal.valueOf(6);

    /** A single contact-to-contact weight change beyond this fraction is treated as suspicious. */
    private static final BigDecimal IMPLAUSIBLE_WEIGHT_CHANGE_FRACTION = BigDecimal.valueOf(0.25d);

    public static final String CONTENT_VERSION = "engineering-seed-1.0.0";

    private GrowthTrajectoryAnalyser() {
    }

    /**
     * @param history measurements in any order; analysed oldest to newest
     */
    public static TrajectoryAssessment analyse(List<TrajectoryPoint> history) {
        List<String> observations = new ArrayList<>();
        List<String> dataQualityConcerns = new ArrayList<>();

        if (history == null || history.isEmpty()) {
            return new TrajectoryAssessment(false, false, null, List.of(
                    "No previous measurements are recorded, so no trajectory can be assessed yet."),
                    dataQualityConcerns, CONTENT_VERSION);
        }

        List<TrajectoryPoint> ordered = history.stream()
                .filter(point -> point != null && point.ageDays() != null)
                .sorted(Comparator.comparing(TrajectoryPoint::ageDays))
                .toList();

        for (TrajectoryPoint point : ordered) {
            if (point.weightForAgeZ() != null
                    && point.weightForAgeZ().abs().compareTo(IMPLAUSIBLE_Z_MAGNITUDE) > 0) {
                dataQualityConcerns.add("A weight-for-age z-score of " + point.weightForAgeZ()
                        + " is outside biological plausibility limits. Re-measure before acting on it.");
            }
        }

        if (ordered.size() < 2) {
            observations.add("Only one measurement is available; a trajectory needs at least two contacts.");
            return new TrajectoryAssessment(false, false, null, observations, dataQualityConcerns, CONTENT_VERSION);
        }

        TrajectoryPoint earliest = ordered.get(0);
        TrajectoryPoint latest = ordered.get(ordered.size() - 1);
        TrajectoryPoint previous = ordered.get(ordered.size() - 2);

        detectImplausibleWeightJump(previous, latest, dataQualityConcerns);

        BigDecimal zChange = null;
        boolean faltering = false;
        if (latest.weightForAgeZ() != null && earliest.weightForAgeZ() != null) {
            zChange = latest.weightForAgeZ().subtract(earliest.weightForAgeZ()).setScale(2, RoundingMode.HALF_UP);
            faltering = zChange.negate().compareTo(FALTERING_Z_DROP) >= 0;

            String direction = zChange.signum() < 0 ? "fallen" : (zChange.signum() > 0 ? "risen" : "held");
            observations.add("Weight-for-age has " + direction + " from " + earliest.weightForAgeZ()
                    + " to " + latest.weightForAgeZ() + " SD over "
                    + monthsBetween(earliest, latest) + " months.");
        }

        boolean weightLoss = false;
        if (latest.weightKg() != null && previous.weightKg() != null
                && latest.weightKg().compareTo(previous.weightKg()) < 0) {
            weightLoss = true;
            observations.add("Weight has fallen from " + previous.weightKg() + " kg to "
                    + latest.weightKg() + " kg since the previous contact.");
        }

        if (faltering && !weightLoss && zChange != null) {
            observations.add("Weight is still increasing, but not fast enough to keep pace with the"
                    + " growth standard, which is what growth faltering means.");
        }

        return new TrajectoryAssessment(faltering, weightLoss, zChange, observations, dataQualityConcerns,
                CONTENT_VERSION);
    }

    private static void detectImplausibleWeightJump(TrajectoryPoint previous, TrajectoryPoint latest,
                                                    List<String> concerns) {
        if (previous.weightKg() == null || latest.weightKg() == null
                || previous.weightKg().compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        BigDecimal change = latest.weightKg().subtract(previous.weightKg()).abs();
        BigDecimal allowed = previous.weightKg().multiply(IMPLAUSIBLE_WEIGHT_CHANGE_FRACTION);
        if (change.compareTo(allowed) > 0) {
            concerns.add("Weight changed from " + previous.weightKg() + " kg to " + latest.weightKg()
                    + " kg between contacts, which is a larger change than is usually possible."
                    + " Confirm the scale and re-measure before acting on it.");
        }
    }

    private static String monthsBetween(TrajectoryPoint from, TrajectoryPoint to) {
        int days = to.ageDays() - from.ageDays();
        return BigDecimal.valueOf(days).divide(BigDecimal.valueOf(30.4375d), 1, RoundingMode.HALF_UP).toPlainString();
    }

    public record TrajectoryPoint(Integer ageDays, BigDecimal weightKg, BigDecimal weightForAgeZ) {
    }

    /**
     * @param faltering            z-score trajectory has dropped by at least the faltering threshold
     * @param weightLossSinceLast  absolute weight fell since the previous contact
     * @param zScoreChange         change in weight-for-age z-score across the observed period
     */
    public record TrajectoryAssessment(
            boolean faltering,
            boolean weightLossSinceLast,
            BigDecimal zScoreChange,
            List<String> observations,
            List<String> dataQualityConcerns,
            String contentVersion) {
    }
}
